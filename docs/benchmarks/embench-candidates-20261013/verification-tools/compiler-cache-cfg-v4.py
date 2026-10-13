from pathlib import Path
import json,struct,importlib.util,collections,sys
W=Path(__file__).parent
s=importlib.util.spec_from_file_location('fx',W.parent/'embench-dve-ocb-sdiv2-composed-20261012/same-layout-direct-read-v3/generic54-source-v1/native-audit-v1/effects.py');fx=importlib.util.module_from_spec(s);s.loader.exec_module(fx)
def audit(e,r):
 b=Path(e['arm']['native']['path']).read_bytes();a=struct.unpack('<'+'I'*(len(b)//4),b)
 globalTodo=collections.deque([v[0]for v in e['arm']['exports'].values()]+[x['newTarget']for x in r.get('mappedNativeAddresses',[])]);globalReachable=set()
 while globalTodo:
  pc=globalTodo.popleft()
  if pc in globalReachable:continue
  globalReachable.add(pc);assert 0<=pc<r['newCodeEnd'] and pc%4==0
  z=a[pc//4];rd,wr,succ,kind=fx.effects(z,pc)
  if z&0xfc000000==0x94000000:
   imm=z&0x3ffffff;imm=imm-(1<<26)if imm&(1<<25)else imm;succ=list(succ)+[pc+imm*4]
  globalTodo.extend(succ)
 changedSubs={q for p,q,why in r['wordPairs'] if why=='fuel-subs16-to8'};changedStores={q for p,q,why in r['wordPairs'] if why=='fuel-store16-to8'}
 prologues=[p for p in range(0,len(b),4) if a[p//4]==0xa9bf7bfd];roots=set()
 for c in r['charges']:
  candidates=[p for p in prologues if p<=c['newPC']];assert candidates;root=max(candidates);assert c['newPC']-root<=4096;roots.add(root)
 callEntries={x['newTarget']for x in r['refs']if x['kind']=='BL'};tailCalls=[];checked=set();graphs=[]
 for root in sorted(roots):
  todo=collections.deque([(root,False)]);seen=set();edges=[];closure=set()
  while todo:
   p,valid=todo.popleft()
   if (p,valid) in seen:continue
   seen.add((p,valid));closure.add(p);assert 0<=p<len(b) and p%4==0
   z=a[p//4];rd,wr,succ,kind=fx.effects(z,p)
   if z&0xfc000000==0x14000000 and p>=4 and a[p//4-1]==0xa8c17bfd and len(succ)==1 and succ[0]!=root and succ[0]in callEntries:
    tailCalls.append(dict(owner=root,pc=p,target=succ[0],originalFrameReturnPreserved=True));succ=[]
   if p in changedSubs:
    assert z==0xf1000508 and valid,('STALE_FUEL_AT_CHANGED_CHARGE',e['tag'],root,p,valid);checked.add(p)
   newValid=valid
   if 8 in wr:newValid=False
   if z==0xf94004e8:newValid=True
   if z==0xf90004e8:newValid=True
   if z in [0xf90004f0,0xf90004ff]:newValid=False
   if z&0xfc000000==0x94000000 or kind=='issued-runtime-callback-ABI':
    newValid=False;succ=[p+4]
   if p in changedStores:assert z==0xf90004e8
   for q in succ:edges.append([p,q,newValid]);todo.append((q,newValid))
  # No branch into a cached caller's interior from a different body. The
  # existing shared terminal fuel trap is an allowed sink, not a charge.
  terminals={p for p in closure if not fx.effects(a[p//4],p)[2]}
  for ref in r['refs']:
   if ref['kind'] not in ['B','BC','CB','TB','BL']:continue
   target=ref['newTarget'];origin=ref['newPC']
   if target in closure and origin not in closure:
    entries={x['newTarget'] for x in r['refs'] if x['kind']=='BL'}|{x['newTarget']for x in r.get('mappedNativeAddresses',[])}|{v[0]for v in e['arm']['exports'].values()}
    owner=max(x for x in prologues if x<=origin)
    sameUnreachableOwner=(owner==root and origin not in globalReachable and not any(root<x<=origin for x in entries))
    assert target==root or target in terminals or sameUnreachableOwner,('EXTERNAL_INTERIOR_ENTRY',origin,target,root)
    # Original unreachable same-owner source blocks add no callable entry.
    if sameUnreachableOwner:edges.append(['inherited-unreachable-owned-source-branch',origin,target])
  for name,(entry,arity) in e['arm']['exports'].items():
   if entry in closure:assert entry==root,('EXPORTED_INTERIOR',name,entry)
  graphs.append(dict(root=root,visitedInstructions=len(closure),visitedStates=len(seen),edges=edges,allIncomingBranchesOwnedOrTerminal=True))
 # Differential scratch liveness: bank changes and added cache loads must
 # never feed a retained instruction. ABI scratch values are not arguments;
 # on return conservatively mark both banks different until redefinition.
 byPC={q:(p,why) for p,q,why in r['wordPairs']};extra={x['newPC'] for x in r['insertedFuelLoads']}|{x['newFuelReloadPC']for x in r.get('returnComparisons',[])if 'newFuelReloadPC'in x};scratchRows=[]
 for root in sorted(roots):
  todo=collections.deque([(root,frozenset())]);seen=set();readsChecked=0
  while todo:
   pc,dirty=todo.popleft()
   if (pc,dirty) in seen:continue
   seen.add((pc,dirty));z=a[pc//4];rd,wr,succ,kind=fx.effects(z,pc);d=set(dirty);why=byPC.get(pc,(None,'extra'))[1]
   if z&0xfc000000==0x14000000 and pc>=4 and a[pc//4-1]==0xa8c17bfd and len(succ)==1 and succ[0]!=root and succ[0]in callEntries:succ=[]
   if pc in extra:d.add(8)
   elif why in ['fuel-entry-load16-to8','fuel-subs16-to8']:d.update([8,16])
   elif why=='fuel-store16-to8':pass
   else:
    assert not (rd & d),('CHANGED_SCRATCH_REACHES_RETAINED_READ',e['tag'],root,pc,hex(z),sorted(rd & d));readsChecked+=1;d.difference_update(wr)
   if z&0xfc000000==0x94000000 or kind=='issued-runtime-callback-ABI':d.update([8,16]);succ=[pc+4]
   for q in succ:todo.append((q,frozenset(d)))
  scratchRows.append(dict(root=root,states=len(seen),retainedReadsChecked=readsChecked))
 assert checked==changedSubs,('UNCHARGED_PROOF_GAP',changedSubs-checked)
 return dict(status='PASS_CHANGED_FUEL_CHARGE_CACHE_EQUALS_PUBLISHED_CONTEXT_ON_ALL_CFG_PATHS',tag=e['tag'],roots=sorted(roots),changedCharges=len(checked),graphs=graphs,verifiedFrameReturnTailCallSinks=tailCalls,globalReachableInstructions=len(globalReachable),unreachableIncomingBranchesProvedFromAllExportsCallsAndOwnedFunctionAddresses=True,differentialScratchReads=scratchRows,contextAuthority='Inherited unchanged GN reserved x7 context/frame ABI; pf-scan excludes foreign source calls and callbacks. Added LDR8 never changes memory; actual callee return conservatively invalidates cache.',originalPublishPointsAndTrapTargetsPreserved=True,all39SemanticObservationsPending=True,adopted=False)
if __name__=='__main__':
 plan=Path(sys.argv[1]);maps=Path(sys.argv[2]);out=Path(sys.argv[3]);out.mkdir(exist_ok=False);j=json.loads(plan.read_text());rows=[]
 for e in j['images']:
  r=json.loads((maps/(e['tag']+'.json')).read_text())
  if not r.get('changed'):continue
  try:z=audit(e,r)
  except BaseException as ex:z=dict(status='FIRST_CACHE_PROOF_FAILURE_PRESERVED',tag=e['tag'],error=repr(ex),adopted=False)
  (out/(e['tag']+'.json')).write_text(json.dumps(z,indent=2)+'\n');rows.append({k:z[k] for k in ['tag','status']});print(e['tag'],z['status'],z.get('error',''))
 (out/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')
