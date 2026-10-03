#!/bin/zsh
# Differential check of the guest twins lang/compat/kotoba/compiler/refactor/{edit,diff,prelude}.kotoba against the host
# modules src/kotoba/compiler/refactor/*.cljk of amu. The twin and its probe are compiled by the amu SEED (no stage-0,
# no JVM at run time) and run natively through tools/kexe_loader.c; the host side (host_*.cljs under kbb) is the oracle
# only (BOOTSTRAP-TOOL). Verdict per case: byte equality of the two outputs.
# Usage: run.sh <edit|diff|prelude> <amu-repo> <seed.bin> <out-dir> <case>...
#   edit:    case = <k>.scn with <k>.src beside it (gen_edit.py)
#   diff:    case = <k>.old with <k>.new beside it (gen_diff.py)
#   prelude: no cases
emulate -L zsh; setopt pipefail
H=${0:A:h}; K=${H:h:h}
kind=$1; A=${2:A}; SB=${3:A}; W=${4:A}; shift 4
mkdir -p $W
export SEED_REPO=$A SEED_BUILD=$W SEED_GRANT=3,35,37,38,39; source $A/scripts/seed/lib.sh
probe=$H/probe/probe/${kind}_dump.kotoba
if [ ! -s $W/probe.bin ]; then
  SEED_RESOURCES_35=$K:$A:$W SEED_SECONDS=900 seed_run $SB $(cat ${SB%.bin}.offset) compile $probe \
    --source-path $H/probe --source-path $K/lang/compat --unpinned --output $W/probe.kseed > $W/compile.out 2>&1 \
    || { cat $W/compile.out; exit 2; }
  zsh $A/scripts/seed/seed-cc.sh extract $W/probe.kseed main $W/probe.bin > $W/extract.out || { cat $W/extract.out; exit 2; }
fi
off=$(sed -n 's/.*:offset \([0-9]*\).*/\1/p' $W/extract.out)
L=$(seed_loader) || exit 2
run1() { # args for host and twin
  KEXE_COMMAND=1 KEXE_CAP_RESOURCES_35=${RES:-/private/tmp} KEXE_STRING_POOL=1073741824 KEXE_PAIRS=67108864 KEXE_VECTORS=67108864 \
    KEXE_VECTOR_ITEMS=134217728 KEXE_CPU_SECONDS=600 KEXE_WALL_SECONDS=600 \
    $L $W/probe.bin $off 0 aarch64 35,37,38 -- "$@"
}
same=0; differ=0; i=0
if [ $kind = prelude ]; then
  kbb --classpath $A/src $H/host_prelude.cljs > $W/p.host 2>/dev/null
  run1 prelude > $W/p.twin 2> $W/p.err
  if [ -s $W/p.host ] && cmp -s $W/p.host $W/p.twin; then same=1; printf "SAME\tprelude\t%s lines\n" $(wc -l < $W/p.host)
  else differ=1; printf "DIFFER\tprelude\t%s\n" "$(diff $W/p.host $W/p.twin | head -3 | tr '\n' '|' | cut -c1-200) $(head -c 100 $W/p.err)"; fi
else
for f in "$@"; do
  i=$((i+1)); f=${f:A}; b=${f:r}
  if [ $kind = edit ]; then
    kbb --classpath $A/src $H/host_edit.cljs $b.src $f 2>/dev/null > $W/$i.host
    run1 edit $b.src $f > $W/$i.twin 2> $W/$i.err
  else
    kbb --classpath $A/src $H/host_diff.cljs $b.old $b.new 2>/dev/null > $W/$i.host
    run1 diff $b.old $b.new > $W/$i.twin 2> $W/$i.err
  fi
  # the probe ends every output with a newline the host's println also adds
  if [ -s $W/$i.host ] && cmp -s $W/$i.host $W/$i.twin; then
    same=$((same+1)); printf "SAME\t%s\t%s lines\n" $f $(wc -l < $W/$i.host)
  else
    differ=$((differ+1)); printf "DIFFER\t%s\t%s\n" $f "$(diff $W/$i.host $W/$i.twin | head -3 | tr '\n' '|' | cut -c1-200) $(head -c 100 $W/$i.err)"
  fi
done
fi
echo "refactor-twin-diff $kind: $same same, $differ differ (seed $(shasum -a 256 $SB | cut -c1-8))"
[ $differ -eq 0 ]
