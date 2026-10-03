#!/bin/zsh
# Differential check of the guest twin lang/compat/kotoba/compiler/refactor/cst.kotoba against the host
# src/kotoba/compiler/refactor/cst.cljk of amu. The twin is compiled by the amu SEED (no stage-0, no JVM at run
# time) together with probe/probe/cst_dump.kotoba and run natively through tools/kexe_loader.c; the host side is
# host_dump.cljs under kbb (BOOTSTRAP-TOOL: the oracle only). Both print one line per walk visit (type, UTF-8
# offsets, prefix, splice, region, index, parent, rc key, head-text, call-head-text, strip-meta, args, rc-branches,
# ->fn-view kids) plus the parse error line; the verdict per file is byte equality of the two dumps.
# Usage: run.sh <amu-repo> <seed.bin> <out-dir> <file>...     (seed.bin with its .offset next to it)
emulate -L zsh; setopt pipefail
H=${0:A:h}; K=${H:h:h}
A=${1:A}; SB=${2:A}; W=${3:A}; shift 3
mkdir -p $W
export SEED_REPO=$A SEED_BUILD=$W SEED_GRANT=3,35,37,38,39; source $A/scripts/seed/lib.sh   # the seed hashes (wire 3) on a multi-module compile
if [ ! -s $W/probe.bin ]; then
  SEED_RESOURCES_35=$K:$A:$W SEED_SECONDS=900 seed_run $SB $(cat ${SB%.bin}.offset) compile $H/probe/probe/cst_dump.kotoba \
    --source-path $H/probe --source-path $K/lang/compat --unpinned --output $W/probe.kseed > $W/compile.out 2>&1 \
    || { cat $W/compile.out; exit 2; }
  zsh $A/scripts/seed/seed-cc.sh extract $W/probe.kseed main $W/probe.bin > $W/extract.out || { cat $W/extract.out; exit 2; }
fi
off=$(sed -n 's/.*:offset \([0-9]*\).*/\1/p' $W/extract.out)
L=$(seed_loader) || exit 2
same=0; differ=0; i=0
for f in "$@"; do
  i=$((i+1)); f=${f:A}
  kbb --classpath $A/src $H/host_dump.cljs $f 2>/dev/null > $W/$i.host
  KEXE_COMMAND=1 KEXE_CAP_RESOURCES_35=${f:h} KEXE_STRING_POOL=1073741824 KEXE_PAIRS=67108864 KEXE_VECTORS=67108864 \
    KEXE_VECTOR_ITEMS=134217728 KEXE_CPU_SECONDS=600 KEXE_WALL_SECONDS=600 \
    $L $W/probe.bin $off 0 aarch64 35,37,38 -- dump $f > $W/$i.twin 2> $W/$i.err
  if [ -s $W/$i.host ] && cmp -s $W/$i.host $W/$i.twin; then
    same=$((same+1)); printf "SAME\t%s\t%s visits\t%s\n" $f $(($(wc -l < $W/$i.host)-1)) "$(head -1 $W/$i.host)"
  else
    differ=$((differ+1)); printf "DIFFER\t%s\t%s\n" $f "$(diff $W/$i.host $W/$i.twin | head -3 | tr '\n' '|' | cut -c1-200) $(head -c 100 $W/$i.err)"
  fi
done
echo "cst-twin-diff: $same same, $differ differ (seed $(shasum -a 256 $SB | cut -c1-8))"
[ $differ -eq 0 ]
