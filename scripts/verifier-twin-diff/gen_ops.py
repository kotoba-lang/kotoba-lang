#!/usr/bin/env python3
"""Generate lang/compat/kotoba/verifier/ops.kotoba from osaho's verifier.cljk.

Every operation-name table of the host verifier (quoted symbol maps and sets)
becomes a `cond` chain over `=` on the symbol's text, so a lookup allocates
nothing. The tables are READ from the host source, not retyped."""
import re, sys
src = open(sys.argv[1]).read()

def strip_comments(s):
    out=[]
    for line in s.split('\n'):
        # drop ;; comments (no strings with ; in these tables)
        i=line.find(';')
        out.append(line if i<0 else line[:i])
    return '\n'.join(out)

def form_after(name):
    m = re.search(r'\(def (?:\^:private )?'+re.escape(name)+r'\b', src)
    assert m, name
    i = m.start(); depth=0; j=i
    in_str=False
    while True:
        c=src[j]
        if in_str:
            if c=='\\': j+=1
            elif c=='"': in_str=False
        else:
            if c=='"': in_str=True
            elif c=='(' or c=='[' or c=='{': depth+=1
            elif c==')' or c==']' or c=='}':
                depth-=1
                if depth==0: return strip_comments(src[i:j+1])
        j+=1

def quoted_map(name):
    f = form_after(name)
    m = re.search(r"'\{(.*?)\}", f, re.S)
    toks = m.group(1).split()
    assert len(toks)%2==0, name
    return [(toks[i], int(toks[i+1])) for i in range(0,len(toks),2)]

def quoted_set(name):
    f = form_after(name)
    m = re.search(r"'#\{(.*?)\}", f, re.S)
    return m.group(1).split()

out = []
def arity_fn(fname, pairs, doc):
    seen=set(); arms=[]
    for k,v in pairs:
        if k in seen: continue
        seen.add(k); arms.append(f'    (= op "{k}") {v}')
    out.append(f';; {doc}\n(defn {fname} [op :string] :i64\n  (cond\n' + '\n'.join(arms) + '\n    :else -1))\n')
def set_fn(fname, names, doc):
    seen=[]; 
    for n in names:
        if n not in seen: seen.append(n)
    # or-chain in groups to keep nesting shallow
    arms='\n'.join(f'    (= op "{n}") true' for n in seen)
    out.append(f';; {doc}\n(defn {fname} [op :string] :bool\n  (cond\n{arms}\n    :else false))\n')

memory = quoted_map('kernel-memory-operations')
arities_extra = re.search(r"'\{(.*?)\}", form_after('kernel-memory-arities'), re.S).group(1).split()
extra=[(arities_extra[i], int(arities_extra[i+1])) for i in range(0,len(arities_extra),2)]

arity_fn('kernel-memory-operation-arity', memory, 'kernel-memory-operations (checked-memory surface): op -> arity, -1 when absent')
arity_fn('kernel-memory-arity', memory+extra, 'kernel-memory-arities = kernel-memory-operations + the lock pair, atomics and dot kernels')
for nm,fn in [('i64-operations','i64-op-arity'),('i32-operations','i32-op-arity'),('heap-operations','heap-op-arity'),
              ('kgraph-operations','kgraph-op-arity'),('string-operations','string-op-arity'),
              ('tagged-i64-operations','tagged-i64-op-arity'),('f64-operations','f64-op-arity'),
              ('f32-operations','f32-op-arity'),('vector-operations','vector-op-arity'),
              ('bytes-operations','bytes-op-arity'),('string-index-operations','string-index-op-arity'),
              ('xml-operations','xml-op-arity'),('decimal-operations','decimal-op-arity'),
              ('region-operations','region-op-arity')]:
    arity_fn(fn, quoted_map(nm), nm+': op -> arity, -1 when absent')
for nm,fn in [('arithmetic','arithmetic-op?'),('i32-shifts','i32-shift-op?'),('i64-shifts','i64-shift-op?'),
              ('comparisons','comparison-op?'),('vector-constructors','vector-constructor-op?'),
              ('hetero-vector-operations','hetero-vector-op?')]:
    set_fn(fn, quoted_set(nm), nm)

# region-scalar-operations: (into (set/union ...) '#{...}) -- the quoted tail plus the unions of the keyed tables
tail = quoted_set('region-scalar-operations')
union = set([k for k,_ in quoted_map('i64-operations')]) | set([k for k,_ in quoted_map('i32-operations')]) \
      | set([k for k,_ in quoted_map('f64-operations')]) | set([k for k,_ in quoted_map('f32-operations')]) \
      | set(quoted_set('arithmetic')) | set(quoted_set('comparisons')) | set(quoted_set('i64-shifts'))
set_fn('region-scalar-operation?', sorted(union)+tail, 'region-scalar-operations')

# the privileged-operation arity map inside verify-expr!
m = re.search(r"\(\{'kernel-boot-info 0(.*?)\} op\)", strip_comments(src), re.S)
body = "'kernel-boot-info 0" + m.group(1)
toks = body.replace("'", '').split()
priv=[(toks[i], int(toks[i+1])) for i in range(0,len(toks),2)]
arity_fn('kernel-privileged-op-arity', priv, 'the kernel privileged operations verify-expr! admits: op -> arity')

# granted-region-operations, kernel-native-operations
set_fn('granted-region-op?', quoted_set('granted-region-operations'), 'granted-region-operations')
kn = quoted_set('kernel-native-operations')
set_fn('kernel-native-tail-op?', kn, 'the quoted tail of kernel-native-operations (the rest is kernel-memory-operations)')

m = re.search(r"\(into \(set/difference\s+\(set \(keys kernel-memory-operations\)\)\s+granted-region-operations\)\s+'#\{(.*?)\}\)", strip_comments(src), re.S)
set_fn('kernel-target-only-tail-op?', m.group(1).split(), 'the quoted tail of the operations a hosted (non-kernel) target refuses')
header = open(sys.argv[3]).read()
open(sys.argv[2],'w').write(header + '\n' + '\n'.join(out))
