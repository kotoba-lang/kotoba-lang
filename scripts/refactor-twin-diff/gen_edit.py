#!/usr/bin/env python3
"""gen_edit.py <out-dir> [n] : seeded edit scenarios (ASCII sources). Writes <out>/<k>.src and <out>/<k>.scn.
Token rules: text and rule are one token without spaces ('~' = empty)."""
import random, sys, os
out = sys.argv[1]; n = int(sys.argv[2]) if len(sys.argv) > 2 else 40
os.makedirs(out, exist_ok=True)
words = ["(defn", "foo", "[x]", "  (+ x 1)", "\t(let", "bar)", "", "   ", "a", "b-c", ";; note", "(ns x)", "{:a 1}"]
for k in range(n):
    r = random.Random(1000 + k)
    lines = [r.choice(words) for _ in range(r.randint(1, 25))]
    src = "\n".join(lines) + ("\n" if r.random() < 0.5 else "")
    open(f"{out}/{k}.src", "w").write(src)
    m = r.randint(0, 24); es = []
    for _ in range(m):
        s = r.randint(0, len(src)); kind = r.random()
        e = s if kind < 0.35 else min(len(src), s + r.randint(0, 12))
        if r.random() < 0.15 and es:   # nested / same range as an earlier edit
            o = r.choice(es); s, e = o[0], o[1]
            if r.random() < 0.5 and e - s > 2: s += 1
        es.append((s, e, r.choice(["~", "X", "Hello", "(f_x)", "ab"]), r.choice(["r1", "r2", "~"]), r.choice([0, 0, 0, 1, 2, 5])))
    with open(f"{out}/{k}.scn", "w") as f:
        for s, e, t, ru, o in es: f.write(f"E {s} {e} {t} {ru} {o}\n")
