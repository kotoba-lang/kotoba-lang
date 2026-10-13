from pathlib import Path
import json,struct,importlib.util,collections,sys
W=Path(__file__).parent
s=importlib.util.spec_from_file_location('fx',W.parent/'embench-dve-ocb-sdiv2-composed-20261012/same-layout-direct-read-v3/generic54-source-v1/native-audit-v1/effects.py');fx=importlib.util.module_from_spec(s);s.loader.exec_module(fx)
def audit(e,r):
 b=Path(e['arm']['native']['path']).read_bytes();a=struct.unpack('<'+'I'*(len(b)//4),b)
 changedSubs={q for p,q,why in r['wordPairs'] if why=='fuel-subs16-to8'};changedStores={q for p,q,why in r['wordPairs'] if why=='fuel-store16-to8'}
 prologues=[p for p in range(0,len(b),4) if a[p//4]==0xa9bf7bfd];roots=set()
 for c in r['charges']:
  candidates=[p for p in prologues if p<=c['newPC']];assert candidates;root=max(candidates);assert c['newPC']-root<=4096;roots.add(root)
 checked=set();graphs=[]
 for root in sorted(roots):
  todo=collections.deque([(root,False)]);seen=set();edges=[];closure=set()
  while todo:
   p,valid=todo.popleft()
   if (p,valid) in seen:continue
   seen.add((p,valid));closure.add(p);assert 0<=p<len(b) and p%4==0
   z=a[p//4];rd,wr,succ,kind=fx.effects(z,p)
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
    assert target==root or target in terminals,('EXTERNAL_INTERIOR_ENTRY',origin,target,root)
  for name,(entry,arity) in e['arm']['exports'].items():
   if entry in closure:assert entry==root,('EXPORTED_INTERIOR',name,entry)
  graphs.append(dict(root=root,visitedInstructions=len(closure),visitedStates=len(seen),edges=edges,allIncomingBranchesOwnedOrTerminal=True))
 # Differential scratch liveness: bank changes and added cache loads must
 # never feed a retained instruction. ABI scratch values are not arguments;
 # on return conservatively mark both banks different until redefinition.
 byPC={q:(p,why) for p,q,why in r['wordPairs']};extra={x['newPC'] for x in r['insertedFuelLoads']};scratchRows=[]
 for root in sorted(roots):
  todo=collections.deque([(root,frozenset())]);seen=set();readsChecked=0
  while todo:
   pc,dirty=todo.popleft()
   if (pc,dirty) in seen:continue
   seen.add((pc,dirty));z=a[pc//4];rd,wr,succ,kind=fx.effects(z,pc);d=set(dirty);why=byPC.get(pc,(None,'extra'))[1]
   if pc in extra:d.add(8)
   elif why in ['fuel-entry-load16-to8','fuel-subs16-to8']:d.update([8,16])
   elif why=='fuel-store16-to8':pass
   else:
    assert not (rd & d),('CHANGED_SCRATCH_REACHES_RETAINED_READ',e['tag'],root,pc,hex(z),sorted(rd & d));readsChecked+=1;d.difference_update(wr)
   if z&0xfc000000==0x94000000 or kind=='issued-runtime-callback-ABI':d.update([8,16]);succ=[pc+4]
   for q in succ:todo.append((q,frozenset(d)))
  scratchRows.append(dict(root=root,states=len(seen),retainedReadsChecked=readsChecked))
 assert checked==changedSubs,('UNCHARGED_PROOF_GAP',changedSubs-checked)
 return dict(status='PASS_CHANGED_FUEL_CHARGE_CACHE_EQUALS_PUBLISHED_CONTEXT_ON_ALL_CFG_PATHS',tag=e['tag'],roots=sorted(roots),changedCharges=len(checked),graphs=graphs,differentialScratchReads=scratchRows,contextAuthority='Inherited unchanged GN reserved x7 context/frame ABI; pf-scan excludes foreign source calls and callbacks. Added LDR8 never changes memory; actual callee return conservatively invalidates cache.',originalPublishPointsAndTrapTargetsPreserved=True,all39SemanticObservationsPending=True,adopted=False)
if __name__=='__main__':
 plan=Path(sys.argv[1]);maps=Path(sys.argv[2]);out=Path(sys.argv[3]);out.mkdir(exist_ok=False);j=json.loads(plan.read_text());rows=[]
 for e in j['images']:
  r=json.loads((maps/(e['tag']+'.json')).read_text())
  if not r.get('changed'):continue
  try:z=audit(e,r)
  except BaseException as ex:z=dict(status='FIRST_CACHE_PROOF_FAILURE_PRESERVED',tag=e['tag'],error=repr(ex),adopted=False)
  (out/(e['tag']+'.json')).write_text(json.dumps(z,indent=2)+'\n');rows.append({k:z[k] for k in ['tag','status']});print(e['tag'],z['status'],z.get('error',''))
 (out/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')
