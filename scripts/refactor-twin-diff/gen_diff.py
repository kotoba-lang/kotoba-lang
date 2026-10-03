#!/usr/bin/env python3
"""gen_diff.py <out-dir> [src-file ...] : seeded diff cases. For every k writes <k>.old and <k>.new: the old text is a source
file (or a synthetic one), the new text a seeded mutation (deleted / inserted / changed / moved blocks, duplicated lines,
a trailing-newline flip, an empty side). Small synthetic cases come first so that a failure is readable."""
import random, sys, os
out = sys.argv[1]; files = sys.argv[2:]
os.makedirs(out, exist_ok=True)
k = 0
def put(old, new):
    global k
    open(f"{out}/{k}.old", "w").write(old); open(f"{out}/{k}.new", "w").write(new); k += 1
fixed = [("", ""), ("", "a\n"), ("a\n", ""), ("a", "a\n"), ("a\n", "a"), ("a\nb\nc\n", "a\nb\nc\n"), ("a\nb\nc\nd\ne\n", "a\nX\nc\nd\ne\n"),
         ("a\n\n\nb\n", "a\nb\n"), ("x\ny\nx\ny\n", "y\nx\ny\nx\n"), ("1\n2\n3\n4\n5\n6\n7\n8\n9\n", "1\n2\n3\n4\nfive\n6\n7\n8\n9\n"),
         ("a\r\nb\r\n", "a\r\nc\r\n"), ("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\n", "A\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nL\n")]
for o, n in fixed: put(o, n)
def mutate(r, lines):
    L = list(lines)
    for _ in range(r.randint(1, 8)):
        op = r.random(); i = r.randrange(len(L) + 1)
        if op < 0.3 and L: del L[min(i, len(L) - 1):min(i, len(L) - 1) + r.randint(1, 4)]
        elif op < 0.6: L[i:i] = [f"new line {r.randint(0, 99)}" for _ in range(r.randint(1, 4))]
        elif op < 0.8 and L: L[min(i, len(L) - 1)] = "changed " + str(r.randint(0, 9))
        elif op < 0.9 and len(L) > 6:   # move a block
            a = r.randrange(len(L) - 3); blk = L[a:a + 3]; del L[a:a + 3]; L[r.randrange(len(L) + 1):0] = blk
        else: L[i:i] = [L[r.randrange(len(L))]] if L else ["dup"]
    return L
for n, f in enumerate(files):
    text = open(f, encoding="utf-8", errors="replace").read()
    text = text.encode("ascii", "replace").decode("ascii")   # offsets are not the question here
    lines = text.split("\n")
    if len(lines) > 400: lines = lines[:400]
    r = random.Random(77 + n)
    for rep in range(4):
        new = mutate(r, lines)
        put("\n".join(lines) + "\n", "\n".join(new) + ("\n" if r.random() < 0.8 else ""))
# unrelated large pair (no anchors, product > 250000 -> one replaced block; and a small LCS-only pair)
put("\n".join(f"same {i % 3}" for i in range(30)) + "\n", "\n".join(f"same {(i * 2) % 3}" for i in range(36)) + "\n")
put("\n".join(f"a{i % 7}" for i in range(600)) + "\n", "\n".join(f"b{i % 5}" for i in range(600)) + "\n")
put("\n".join(f"s{i % 7}" for i in range(600)) + "\n", "\n".join(f"s{(i + 3) % 7}" for i in range(580)) + "\n")
