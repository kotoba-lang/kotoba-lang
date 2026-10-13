from pathlib import Path
import json,struct,importlib.util,collections,sys
W=Path(__file__).parent
s=importlib.util.spec_from_file_location('cache',W/'cached-fuel-cfg.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
P=json.loads(Path(sys.argv[1]).read_text());e=P['images'][0];r=json.loads(Path(sys.argv[2]).read_text());A=Path(e['baseline']['native']['path']).read_bytes();B=Path(e['arm']['native']['path']).read_bytes();a=struct.unpack('<'+'I'*(len(A)//4),A);b=struct.unpack('<'+'I'*(len(B)//4),B)
roots=[i*4 for i,x in enumerate(b)if x==0xa9bf7bfd];bynew={q:(p,why)for p,q,why in r['wordPairs']};calls={x['newTarget']for x in r['refs']if x['kind']=='BL'};rows=[]
memo={}
def no_context_writes(entry):
 if entry in memo:return memo[entry]
 todo=collections.deque([entry]);seen=set();okay=True
 while todo:
  p=todo.popleft()
  if p in seen:continue
  seen.add(p);z=b[p//4];rd,wr,succ,kind=m.fx.effects(z,p)
  if z&0xfc000000==0x94000000:
   imm=z&0x3ffffff;imm=imm-(1<<26)if imm&(1<<25)else imm;todo.append(p+imm*4);wr=wr-{7}
  if 7 in wr:okay=False;break
  todo.extend(succ)
 memo[entry]=okay;return okay
for site in r['removedContextReloads']:
 pc=site['newNextPC'];root=max(x for x in roots if x<=pc);load=site['word'];store=load^0x00400000;owners=[p for p in range(root,min(root+64,pc),4)if b[p//4]==store];assert len(owners)==1,(site,'NO_UNIQUE_CONTEXT_SAVE');save=owners[0];oldSave,why=bynew[save];assert why=='retained'and a[oldSave//4]==store
 todo=collections.deque([(root,True,False)]);seen=set();checked=0
 while todo:
  p,valid,saved=todo.popleft()
  if (p,valid,saved)in seen:continue
  seen.add((p,valid,saved));z=b[p//4];rd,wr,succ,kind=m.fx.effects(z,p)
  if p==pc:assert valid and saved,('REMOVED_CONTEXT_RELOAD_NOT_REDUNDANT',site,root,valid,saved);checked+=1
  nv=valid;ns=saved
  if 7 in wr:nv=False
  if z==load:nv=saved
  if z&~(31|0x00400000)==store&~(31|0x00400000) and not z&0x00400000:
   ns=(z==store and valid)
  if z&0xfc000000==0x94000000:
   imm=z&0x3ffffff;imm=imm-(1<<26)if imm&(1<<25)else imm;nv=(valid and no_context_writes(p+imm*4));succ=[p+4]
  elif kind=='issued-runtime-callback-ABI':nv=False;succ=[p+4]
  if z&0xfc000000==0x14000000 and p>=4 and b[p//4-1]==0xa8c17bfd and len(succ)==1 and succ[0]!=root and succ[0]in calls:succ=[]
  for q in succ:todo.append((q,nv,ns))
 assert checked,(site,'REMOVED_SITE_NOT_REACHABLE')
 rows.append(dict(oldPC=site['oldPC'],newPC=pc,root=root,slotBytes=site['slotBytes'],oldContextSave=oldSave,newContextSave=save,states=len(seen),validAtAllReachableIncomingStates=True))
out=dict(status='PASS_REMOVED_CONTEXT_RELOADS_REDUNDANT_ALL_CFG_PATHS_OWNED_ORIGINAL_SAVE',rows=rows,sourceReservedContextFrameABIInherited=True,onlyTransitivelyNoContextWriteCalleesPreserveContext=True,adopted=False)
Path(sys.argv[3]).write_text(json.dumps(out,indent=2)+'\n');print(out['status'],len(rows))
