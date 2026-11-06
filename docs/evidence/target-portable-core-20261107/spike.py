#!/usr/bin/env python3
"""Feasibility spike (JUN-947). An i64-core Kotoba -> C / Java / Python / TS emitter
that shares no code with Amu: it reads source text and takes its meaning from the
seed/SIR contract (wrap, quot traps on 0 and MIN/-1, shift count mod 64).

mode "contract" applies those rules. mode "naive" uses each language's own
operators, the negative control: it must disagree with Amu somewhere.
"""
import re, sys, os, subprocess, pathlib

TOK = re.compile(r'\s+|;[^\n]*|([()\[\]])|([^\s()\[\]]+)')

def read(src):
    toks, i = [], 0
    while i < len(src):
        m = TOK.match(src, i); i = m.end()
        if m.group(1) or m.group(2): toks.append(m.group(1) or m.group(2))
    def form(p):
        t = toks[p]
        if t in '([':
            close, out, p = (')' if t == '(' else ']'), [], p + 1
            while toks[p] != close:
                x, p = form(p); out.append(x)
            return (out if t == '(' else ('vec', out)), p + 1
        return t, p + 1
    forms, p = [], 0
    while p < len(toks):
        x, p = form(p); forms.append(x)
    return forms

def mangle(n):
    return 'k_' + re.sub(r'[^a-zA-Z0-9]', lambda m: '_%x_' % ord(m.group()), n)

def is_int(x): return isinstance(x, str) and re.fullmatch(r'-?\d+', x)

class Emit:
    def __init__(s, t, mode): s.t, s.mode, s.n, s.lines = t, mode, 0, []
    def tmp(s):
        s.n += 1; return 't%d' % s.n
    def lit(s, v):
        t = s.t
        if t == 'ts': return v + 'n'
        if t in ('c',): return '(-9223372036854775807LL-1)' if v == '-9223372036854775808' else v + 'LL'
        if t == 'java': return v + 'L'
        return v
    def decl(s, n, ind, v=None):
        v = v if v is not None else s.lit('0')
        d = {'c': 'int64_t %s=%s;', 'java': 'long %s=%s;', 'ts': 'let %s:bigint=%s;', 'py': '%s=%s'}[s.t]
        s.lines.append(ind + d % (n, v))
    def assign(s, n, v, ind):
        s.lines.append(ind + ('%s=%s' if s.t == 'py' else '%s=%s;') % (n, v))
    def block(s, head, ind): s.lines.append(ind + (head + ':' if s.t == 'py' else head + '{'))
    def close(s, ind):
        if s.t != 'py': s.lines.append(ind + '}')
    def cond(s, v):
        return {'py': '%s!=0', 'ts': '%s!==0n'}.get(s.t, '%s!=0') % v
    def op(s, f, a, b):
        t, m = s.t, s.mode
        if m == 'contract':
            names = {'+': 'kadd', '-': 'ksub', '*': 'kmul', 'quot': 'kquot', 'i64-shift-left': 'kshl',
                     'u64-shift-right': 'kushr', 'i64-shift-right': 'ksshr'}
            if f in names: return '%s(%s,%s)' % (names[f], a, b)
        else:
            if f == 'quot': return {'py': '(%s//%s)'}.get(t, '(%s/%s)') % (a, b)
            if f == 'i64-shift-left': return '(%s<<%s)' % (a, b)
            if f == 'u64-shift-right': return {'java': '(%s>>>%s)', 'c': '((int64_t)((uint64_t)%s>>%s))'}.get(t, '(%s>>%s)') % (a, b)
            if f == 'i64-shift-right': return '(%s>>%s)' % (a, b)
            if f in '+-*': return '(%s%s%s)' % (a, f, b)
        if f in ('bit-and', 'bit-or', 'bit-xor'):
            return '(%s%s%s)' % (a, {'bit-and': '&', 'bit-or': '|', 'bit-xor': '^'}[f], b)
        cmp = {'=': '==', '<': '<', '>': '>', '<=': '<=', '>=': '>='}[f]
        one, zero = s.lit('1'), s.lit('0')
        if t == 'py': return '(%s if %s%s%s else %s)' % (one, a, cmp, b, zero)
        return '((%s%s%s)?%s:%s)' % (a, '===' if t == 'ts' and cmp == '==' else cmp, b, one, zero)
    BIN = {'+', '-', '*', 'quot', 'bit-and', 'bit-or', 'bit-xor', 'i64-shift-left', 'u64-shift-right',
           'i64-shift-right', '=', '<', '>', '<=', '>='}
    def expr(s, e, env, ind, loop=None, tail=None):
        """Lower e and return a target expression. tail = (dest, loop vars): e ends the enclosing loop."""
        if tail is not None:
            return s.tail(e, env, ind, loop, tail)
        if is_int(e): return s.lit(e)
        if isinstance(e, str):
            if e == 'true': return s.lit('1')
            if e == 'false': return s.lit('0')
            return env[e]
        h = e[0]
        if h == 'if':
            d = s.tmp(); s.decl(d, ind)
            c = s.expr(e[1], env, ind)
            s.block('if (%s)' % s.cond(c) if s.t != 'py' else 'if %s' % s.cond(c), ind)
            s.assign(d, s.expr(e[2], env, ind + '  '), ind + '  ')
            s.close(ind)
            s.block('else', ind)
            s.assign(d, s.expr(e[3], env, ind + '  '), ind + '  ')
            s.close(ind)
            return d
        if h == 'let':
            env = dict(env); b = e[1][1]
            for i in range(0, len(b), 2):
                v = s.expr(b[i + 1], env, ind); n = s.tmp(); s.decl(n, ind, v); env[b[i]] = n
            r = None
            for x in e[2:]:
                r = s.expr(x, env, ind)
            return r
        if h == 'do':
            r = None
            for x in e[1:]:
                r = s.expr(x, env, ind)
            return r
        if h == 'loop':
            d = s.tmp(); s.decl(d, ind)
            env = dict(env); b = e[1][1]; names = []
            for i in range(0, len(b), 2):
                v = s.expr(b[i + 1], env, ind); n = s.tmp(); s.decl(n, ind, v); env[b[i]] = n; names.append(n)
            s.block({'py': 'while True', 'c': 'for(;;)', 'java': 'for(;;)', 'ts': 'for(;;)'}[s.t], ind)
            s.expr(e[2], env, ind + '  ', names, (d, names))
            s.close(ind)
            return d
        if h == 'inc': return s.op('+', s.expr(e[1], env, ind), s.lit('1'))
        if h == 'dec': return s.op('-', s.expr(e[1], env, ind), s.lit('1'))
        if h == 'zero?': return s.op('=', s.expr(e[1], env, ind), s.lit('0'))
        if h == 'not': return s.op('=', s.expr(e[1], env, ind), s.lit('0'))
        if h in s.BIN:
            # left to right, call by value: each operand is forced into a temp in source order
            a = s.tmp(); s.decl(a, ind, s.expr(e[1], env, ind))
            b = s.tmp(); s.decl(b, ind, s.expr(e[2], env, ind))
            return s.op(h, a, b)
        args = []
        for x in e[1:]:
            a = s.tmp(); s.decl(a, ind, s.expr(x, env, ind)); args.append(a)
        return '%s(%s)' % (mangle(h), ','.join(args))
    def tail(s, e, env, ind, names, tl):
        d, _ = tl
        if isinstance(e, list) and e[0] == 'if':
            c = s.expr(e[1], env, ind)
            s.block('if (%s)' % s.cond(c) if s.t != 'py' else 'if %s' % s.cond(c), ind)
            s.tail(e[2], env, ind + '  ', names, tl); s.close(ind)
            s.block('else', ind)
            s.tail(e[3], env, ind + '  ', names, tl); s.close(ind)
            return
        if isinstance(e, list) and e[0] == 'let':
            env = dict(env); b = e[1][1]
            for i in range(0, len(b), 2):
                v = s.expr(b[i + 1], env, ind); n = s.tmp(); s.decl(n, ind, v); env[b[i]] = n
            for x in e[2:-1]:
                s.expr(x, env, ind)
            s.tail(e[-1], env, ind, names, tl); return
        if isinstance(e, list) and e[0] == 'recur':
            ts = []
            for x in e[1:]:
                t = s.tmp(); s.decl(t, ind, s.expr(x, env, ind)); ts.append(t)
            for n, t in zip(names, ts):
                s.assign(n, t, ind)
            s.lines.append(ind + ('continue' if s.t == 'py' else 'continue;'))
            return
        s.assign(d, s.expr(e, env, ind), ind)
        s.lines.append(ind + ('break' if s.t == 'py' else 'break;'))

RT = {
 'c': r'''#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
static void trap(void){fputs("TRAP\n",stdout);exit(70);}
static int64_t kadd(int64_t a,int64_t b){return (int64_t)((uint64_t)a+(uint64_t)b);}
static int64_t ksub(int64_t a,int64_t b){return (int64_t)((uint64_t)a-(uint64_t)b);}
static int64_t kmul(int64_t a,int64_t b){return (int64_t)((uint64_t)a*(uint64_t)b);}
static int64_t kquot(int64_t a,int64_t b){if(b==0||(a==INT64_MIN&&b==-1))trap();return a/b;}
static int64_t kshl(int64_t a,int64_t b){return (int64_t)((uint64_t)a<<(b&63));}
static int64_t kushr(int64_t a,int64_t b){return (int64_t)((uint64_t)a>>(b&63));}
static int64_t ksshr(int64_t a,int64_t b){return a>>(b&63);}
''',
 'java': r'''static RuntimeException trap(){System.out.println("TRAP");System.exit(70);return null;}
static long kadd(long a,long b){return a+b;} static long ksub(long a,long b){return a-b;} static long kmul(long a,long b){return a*b;}
static long kquot(long a,long b){if(b==0||(a==Long.MIN_VALUE&&b==-1))throw trap();return a/b;}
static long kshl(long a,long b){return a<<b;} static long kushr(long a,long b){return a>>>b;} static long ksshr(long a,long b){return a>>b;}
''',
 'ts': r'''const W=(x:bigint):bigint=>BigInt.asIntN(64,x);
function trap():never{console.log("TRAP");Deno.exit(70);}
const kadd=(a:bigint,b:bigint)=>W(a+b),ksub=(a:bigint,b:bigint)=>W(a-b),kmul=(a:bigint,b:bigint)=>W(a*b);
const kquot=(a:bigint,b:bigint):bigint=>{if(b===0n||(a===-(2n**63n)&&b===-1n))trap();return W(a/b);};
const kshl=(a:bigint,b:bigint)=>W(a<<(b&63n)),kushr=(a:bigint,b:bigint)=>W(BigInt.asUintN(64,a)>>(b&63n)),ksshr=(a:bigint,b:bigint)=>a>>(b&63n);
''',
 'py': r'''import sys
sys.setrecursionlimit(1000000)
def W(x):
    x&=0xFFFFFFFFFFFFFFFF
    return x-(1<<64) if x>>63 else x
def trap():
    print("TRAP"); sys.stdout.flush(); import os; os._exit(70)
def kadd(a,b): return W(a+b)
def ksub(a,b): return W(a-b)
def kmul(a,b): return W(a*b)
def kquot(a,b):
    if b==0 or (a==-(1<<63) and b==-1): trap()
    q=abs(a)//abs(b)
    return q if (a<0)==(b<0) else -q
def kshl(a,b): return W(a<<(b&63))
def kushr(a,b): return W((a&0xFFFFFFFFFFFFFFFF)>>(b&63))
def ksshr(a,b): return a>>(b&63)
''',
}

def emit(forms, t, mode):
    fns = [f for f in forms if isinstance(f, list) and f[0] in ('defn', 'defn-')]
    out = []
    protos = []
    for f in fns:
        name, params = f[1], [p for p in f[2][1] if not p.startswith(':')]
        ps = ['p%d' % i for i in range(len(params))]
        em = Emit(t, mode); env = dict(zip(params, ps))
        body = f[3:]
        if isinstance(body[0], str) and body[0].startswith(':'): body = body[1:]
        r = None
        for x in body:
            r = em.expr(x, env, '  ')
        m = mangle(name)
        sig = {'c': 'static int64_t %s(%s)' % (m, ','.join('int64_t ' + p for p in ps) or 'void'),
               'java': 'static long %s(%s)' % (m, ','.join('long ' + p for p in ps)),
               'ts': 'function %s(%s):bigint' % (m, ','.join(p + ':bigint' for p in ps)),
               'py': 'def %s(%s)' % (m, ','.join(ps))}[t]
        protos.append(sig + ';')
        if t == 'py':
            out += [sig + ':'] + em.lines + ['  return ' + r]
        else:
            out += [sig + '{'] + em.lines + ['  return %s;' % r, '}']
    mn = mangle('main'); body = '\n'.join(out)
    if t == 'c':
        return RT['c'] + '\n'.join(protos) + '\n' + body + '\nint main(void){printf("%%lld\\n",(long long)%s());return 0;}\n' % mn
    if t == 'java':
        return 'public class P {\n' + RT['java'] + body + '\npublic static void main(String[] a){System.out.println(%s());}\n}\n' % mn
    if t == 'ts':
        return RT['ts'] + body + '\nconsole.log(String(%s()));\n' % mn
    return RT['py'] + body + '\nprint(%s())\n' % mn

def run(cmd, cwd, env=None):
    try:
        r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=45, stdin=subprocess.DEVNULL, env=env)
    except subprocess.TimeoutExpired:
        return 'TIMEOUT'
    o = r.stdout.strip().splitlines()
    if r.returncode == 0: return o[-1] if o else '(empty)'
    if o and o[-1] == 'TRAP': return 'trap'
    err = (r.stderr.strip().splitlines() or [''])
    name = re.search(r'(StackOverflowError|RecursionError|RangeError|ArithmeticException|ZeroDivisionError|MemoryError)', r.stderr)
    return 'crash:%s' % (name.group(1) if name else 'exit%d' % r.returncode)

def exec_target(t, mode, src, work):
    code = emit(read(src), t, mode)
    work.mkdir(parents=True, exist_ok=True)
    if t == 'c':
        (work / 'p.c').write_text(code)
        c = subprocess.run(['clang', '-O2', '-w', '-ffp-contract=off', 'p.c', '-o', 'p'], cwd=work, capture_output=True, text=True)
        if c.returncode: return 'CE:' + c.stderr[:100]
        return run(['./p'], work)
    if t == 'java':
        (work / 'P.java').write_text(code)
        c = subprocess.run(['javac', 'P.java'], cwd=work, capture_output=True, text=True)
        if c.returncode: return 'CE:' + c.stdout[:100] + c.stderr[:100]
        return run(['java', '-cp', '.', 'P'], work)
    if t == 'ts':
        (work / 'p.ts').write_text(code)
        return run(['deno', 'run', '--no-prompt', 'p.ts'], work)
    (work / 'p.py').write_text(code)
    return run([sys.executable, '-I', 'p.py'], work)

PROGRAMS = {
 'fib30': '(defn fib [n :i64] :i64 (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2))))) (defn main [] :i64 (fib 30))',
 'loop_sum_1e6': '(defn main [] :i64 (loop [i 0 s 0] (if (= i 1000000) s (recur (+ i 1) (+ s i)))))',
 'collatz': '(defn step [n :i64] :i64 (if (= (bit-and n 1) 0) (quot n 2) (+ (* 3 n) 1))) (defn main [] :i64 (loop [n 837799 c 0] (if (= n 1) c (recur (step n) (+ c 1)))))',
 'fnv_overflow': '(defn main [] :i64 (loop [i 0 h -3750763034362895579] (if (= i 1000) h (recur (+ i 1) (* (bit-xor h (bit-and i 255)) 1099511628211)))))',
 'add_overflow': '(defn big [] :i64 9223372036854775807) (defn main [] :i64 (+ (big) 1))',
 'neg_quot': '(defn a [] :i64 -7) (defn main [] :i64 (quot (a) 2))',
 'shl_64': '(defn s [] :i64 64) (defn main [] :i64 (i64-shift-left 1 (s)))',
 'ushr_64': '(defn s [] :i64 64) (defn main [] :i64 (u64-shift-right 1 (s)))',
 'ushr_neg': '(defn a [] :i64 -1) (defn s [] :i64 1) (defn main [] :i64 (u64-shift-right (a) (s)))',
 'sshr_neg': '(defn a [] :i64 -8) (defn s [] :i64 65) (defn main [] :i64 (i64-shift-right (a) (s)))',
 'div0': '(defn z [] :i64 0) (defn main [] :i64 (quot 7 (z)))',
 'min_quot_m1': '(defn m [] :i64 -9223372036854775808) (defn n [] :i64 -1) (defn main [] :i64 (quot (m) (n)))',
 'eval_order_let': '(defn f [x :i64] :i64 (* x 2)) (defn main [] :i64 (let [a (f 3) b (f (+ a 1))] (+ a (* b 10))))',
 'mutual_tail_1e6': '(defn ev [n :i64] :i64 (if (= n 0) 1 (od (- n 1)))) (defn od [n :i64] :i64 (if (= n 0) 0 (ev (- n 1)))) (defn main [] :i64 (ev 1000000))',
 'deep_nontail_1e8': '(defn f [n :i64] :i64 (if (= n 0) 0 (+ 1 (f (- n 1))))) (defn main [] :i64 (f 100000000))',
}

def amu_result(amu, name, src, work):
    work.mkdir(parents=True, exist_ok=True)
    f = work / (name + '.kotoba')
    f.write_text('(ns t (:export [main]))\n' + src + '\n')
    r = subprocess.run([amu, 'run', str(f)], capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)
    if r.returncode == 0: return r.stdout.strip()
    if r.returncode == 133: return 'trap'
    return 'crash:exit%d(%s)' % (r.returncode, r.stderr.strip()[:50])

def main():
    amu, root = sys.argv[1], pathlib.Path(sys.argv[2])
    targets = ['c', 'java', 'ts', 'py']
    print('%-18s %-22s | %s' % ('program', 'amu-native', ' | '.join('%-22s' % t for t in targets)))
    agree = {(t, m): 0 for t in targets for m in ('contract', 'naive')}
    total = {(t, m): 0 for t in targets for m in ('contract', 'naive')}
    for name, src in PROGRAMS.items():
        base = amu_result(amu, name, src, root / 'amu')
        for mode in ('contract', 'naive'):
            cells = []
            for t in targets:
                got = exec_target(t, mode, src, root / mode / t / name)
                ok = got == base
                total[(t, mode)] += 1; agree[(t, mode)] += ok
                cells.append('%-22s' % ((('= ' if ok else 'X ') + got)[:22]))
            print('%-18s %-22s | %s' % (name if mode == 'contract' else '  (naive)', (base[:22] if mode == 'contract' else ''), ' | '.join(cells)))
    for m in ('contract', 'naive'):
        print(m, {t: '%d/%d' % (agree[(t, m)], total[(t, m)]) for t in targets})

if __name__ == '__main__':
    main()
