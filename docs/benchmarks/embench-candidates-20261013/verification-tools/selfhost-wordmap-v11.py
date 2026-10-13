from pathlib import Path
import json,struct,importlib.util,hashlib
W=Path(__file__).resolve().parents[2]
s=importlib.util.spec_from_file_location('nm',W.parent/'embench-dve-ocb-sdiv2-composed-20261012/same-layout-two-call-arm-v4/qualification-source-v1/native-map.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
def audit(e):
 A,ae=m.payload(e['baseline']);B,be=m.payload(e['arm']);src=Path(e['source']['path']).read_bytes();assert src==Path(e['baseline']['source']['path']).read_bytes()
 if A==B:assert ae==be;return dict(tag=e['tag'],status='WHOLE_IDENTITY',changed=False)
 if 'sourcePoolDecoder' in e:
  d=e['sourcePoolDecoder'];decoder=Path(d['path']);assert hashlib.sha256(decoder.read_bytes()).hexdigest()==d['sha256']
  sp=importlib.util.spec_from_file_location('seed_pool_authority',decoder);poolmod=importlib.util.module_from_spec(sp);sp.loader.exec_module(poolmod);pool,entries=poolmod.source_pool(src)
 else:pool,entries=m.source_pool(src)
 ca=len(A)-len(pool);cb=len(B)-len(pool)
 if pool:assert A[ca:]==B[cb:]==pool
 assert ca%4==cb%4==0
 a=list(struct.unpack('<'+'I'*(ca//4),A[:ca]));b=list(struct.unpack('<'+'I'*(cb//4),B[:cb]));mapping={};pairs=[];insertions=[];charges=[];padding=[];contextReloads=[];returnComparisons=[];removedContextReloads=[];nativeAddresses=[];p=q=0
 while p<ca:
  z=a[p//4];v=b[q//4] if q<cb else None
  if p+20<=ca and a[p//4:p//4+5]==[0xf94004f0,0xf1000610,0x54000042,0xd4200000,0xf90004f0]:
   lead=1 if v==0xf94004e8 else 0;k=q//4+lead
   if k+4<=len(b) and b[k:k+4]==[0xf1000508,0x54000042,0xd4200000,0xf90004e8]:
    mapping[p]=q
    if lead:pairs.append((p,q,'fuel-entry-load16-to8'))
    for j,reason in [(1,'fuel-subs16-to8'),(2,'reference'),(3,'retained'),(4,'fuel-store16-to8')]:
     np=q+4*(j-1+lead);mapping[p+4*j]=np;pairs.append((p+4*j,np,reason))
    charges.append(dict(oldPC=p,newPC=q,retainedEntryLoad=bool(lead),recipe='original-five-word-local-exhaustion-trap'));p+=20;q+=4*(4+lead);continue
  if p+16<=ca and a[p//4:p//4+2]==[0xf94004f0,0xf1000610] and a[p//4+2]&0xff00001f==0x54000003 and a[p//4+3]==0xf90004f0:
   lead=1 if v==0xf94004e8 else 0;k=q//4+lead
   if k+3<=len(b) and b[k]==0xf1000508 and b[k+1]&0xff00001f==0x54000003 and b[k+2]==0xf90004e8:
    mapping[p]=q
    if lead:pairs.append((p,q,'fuel-entry-load16-to8'))
    for j,reason in [(1,'fuel-subs16-to8'),(2,'reference'),(3,'fuel-store16-to8')]:
     np=q+4*(j-1+lead);mapping[p+4*j]=np;pairs.append((p+4*j,np,reason))
    charges.append(dict(oldPC=p,newPC=q,retainedEntryLoad=bool(lead)));p+=16;q+=4*(3+lead);continue
  # Exact comparison materialization with an owned context-frame restore.
  if z&~0x3ffc00==0xf100001f and p>=4 and a[p//4-1]&0xfc000000==0x94000000 and v==0xaa0003e9 and p+8<=ca and q+20<=cb:
   restore=b[q//4+1];cset=a[p//4+1]
   if restore&~0x3ffc00==0xf94003e7 and b[q//4+2]==0xf94004e8 and b[q//4+3]==z|(9<<5) and b[q//4+4]==cset and cset&~0xf000==0x9a9f07e9:
    roots=[t for t in range(max(0,q-32768),q,4) if b[t//4]==0xa9bf7bfd];assert roots;root=max(roots);slot=restore>>10&4095;store=0xf90003e7|(slot<<10)
    owners=[t for t in range(root,min(root+64,q),4)if b[t//4]==store];assert len(owners)==1
    oldOwners=[op for op,np,why in pairs if np==owners[0] and why=='retained'];assert len(oldOwners)==1 and a[oldOwners[0]//4]==store
    contextReloads.append(dict(oldNextPC=p,newPC=q+4,frameRoot=root,slotBytes=slot*8,originalContextSavePC=oldOwners[0],newContextSavePC=owners[0],pairedFollowingFuelReload=True))
    mapping[p]=q+12;mapping[p+4]=q+16;pairs.extend([(p,q+12,'returned-x0-cmp-materialized-x9'),(p+4,q+16,'retained')]);returnComparisons.append(dict(recipe='returned-copy-context-fuel-compare',oldPC=p,newCopyPC=q,newContextReloadPC=q+4,newFuelReloadPC=q+8,newCmpPC=q+12,oldCompareWord=z,newCompareWord=b[q//4+3],followingCsetWord=cset,oldX9DeadUntilSameCset=True));p+=8;q+=20;continue
  # Returned x0 value materialized in x9 before the inserted fuel reload;
  # the immediately following original CSET overwrites x9. Exact two-word
  # original comparison recipe, no arbitrary register normalization.
  if v==0xaa0003e9 and p>=4 and a[p//4-1]&0xfc000000==0x94000000 and q+16<=cb and b[q//4+1]==0xf94004e8 and p+8<=ca:
   cset=a[p//4+1]
   if z&~0x3ffc00==0xf100001f and cset&~0xf000==0x9a9f07e9 and b[q//4+2]==z|(9<<5) and b[q//4+3]==cset:
    mapping[p]=q+8;mapping[p+4]=q+12;pairs.append((p,q+8,'returned-x0-cmp-materialized-x9'));pairs.append((p+4,q+12,'retained'));returnComparisons.append(dict(oldPC=p,newCopyPC=q,newFuelReloadPC=q+4,newCmpPC=q+8,oldCompareWord=z,newCompareWord=b[q//4+2],followingCsetWord=cset,oldX9DeadUntilSameCset=True));p+=8;q+=16;continue
  # Exact old context reload immediately before a retained BL. This is only
  # a mapping candidate; separate full-CFG context validity proof is required.
  if z&~0x3ffc00==0xf94003e7 and p+8<=ca and ((a[p//4+1]&0xfc000000==0x94000000 and v&0xfc000000==0x94000000) or (p+12<=ca and a[p//4+1:p//4+3]==[0xf94004f0,0xf1000610] and v==0xf1000508)):
   mapping[p]=q;removedContextReloads.append(dict(oldPC=p,newNextPC=q,word=z,slotBytes=((z>>10)&4095)*8,contextCFGProofPending=True));p+=4;continue
  # Exact ADR-self + ADD-low12 + ADD-high12 native address recipe.
  # Both full computed addresses must map to the same original code position.
  if p>=4 and q>=4 and p+8<=ca and q+8<=cb and a[p//4-1]==b[q//4-1] and a[p//4-1]&~31==0x10000000:
   reg=a[p//4-1]&31;lo=0x91000000|(reg<<5)|reg;hi=lo|0x400000
   if z&~0x3ffc00==v&~0x3ffc00==lo and a[p//4+1]&~0x3ffc00==b[q//4+1]&~0x3ffc00==hi:
    ot=p-4+((z>>10)&4095)+(((a[p//4+1]>>10)&4095)<<12);nt=q-4+((v>>10)&4095)+(((b[q//4+1]>>10)&4095)<<12)
    assert 0<=ot<ca and 0<=nt<cb
    nativeAddresses.append(dict(oldPC=p,newPC=q,oldTarget=ot,newTarget=nt,register=reg))
    for j in [0,4]:mapping[p+j]=q+j;pairs.append((p+j,q+j,'mapped-native-address-immediate'))
    p+=8;q+=8;continue
  # New comparison priority repair, exact copied-x0 / fuel-reload suffix.
  if z==0xaa0003e9 and p>=4 and a[p//4-1]&0xfc000000==0x94000000 and p+16<=ca and q+12<=cb and a[p//4+1]==v==0xf94004e8:
   oldCmp=a[p//4+2];cset=a[p//4+3]
   if oldCmp&~0x3ffc00==0xf100013f and b[q//4+1]==oldCmp&~(31<<5) and b[q//4+2]==cset and cset&~0xf000==0x9a9f07e9:
    mapping[p]=q;mapping[p+4]=q;mapping[p+8]=q+4;mapping[p+12]=q+8
    pairs.extend([(p+4,q,'retained'),(p+8,q+4,'cmp9-to0-after-exact-copy'),(p+12,q+8,'retained')]);returnComparisons.append(dict(recipe='exact-copy-elimination-across-fuel-reload',oldPC=p,newPC=q,oldCompareWord=oldCmp,newCompareWord=b[q//4+1],sameCsetWord=cset,oldX9DeadUntilSameCset=True));p+=16;q+=12;continue
  rz=m.ref(z,p);rv=m.ref(v,q) if v is not None else None
  if z==v or (rz and rv and rz[0]==rv[0] and z&rz[2]==v&rv[2]):mapping[p]=q;pairs.append((p,q,'retained'));p+=4;q+=4;continue
  if v is not None and v&~0x3ffc00==0xf94003e7 and q+8<=cb and b[q//4+1]==0xf94004e8:
   roots=[t for t in range(max(0,q-32768),q,4) if b[t//4]==0xa9bf7bfd];assert roots,'NO_CONTEXT_FRAME_ROOT';root=max(roots);slot=v>>10&4095;store=0xf90003e7|(slot<<10)
   owners=[t for t in range(root,min(root+64,q),4) if b[t//4]==store];assert len(owners)==1,'NO_UNIQUE_RESERVED_CONTEXT_SAVE'
   oldOwners=[op for op,np,why in pairs if np==owners[0] and why=='retained'];assert len(oldOwners)==1 and a[oldOwners[0]//4]==store
   assert any(c['retainedEntryLoad'] and root<=c['newPC']<root+96 for c in charges),'NOT_A_CACHED_FUEL_FRAME'
   contextReloads.append(dict(oldNextPC=p,newPC=q,frameRoot=root,slotBytes=slot*8,originalContextSavePC=oldOwners[0],newContextSavePC=owners[0],pairedFollowingFuelReload=True));q+=4;continue
  if v==0xf94004e8:insertions.append(dict(oldNextPC=p,newPC=q,word=v));q+=4;continue
  if p==ca-4 and z in [0,0xd503201f] and q==cb:mapping[p]=q;padding.append(dict(oldPC=p,removed=z));p+=4;continue
  if q==cb-4 and v in [0,0xd503201f] and p==ca:padding.append(dict(newPC=q,inserted=v));q+=4;continue
  # Preserve complete source-owned literal offset MOVZ+MOVK pair.
  if p+8<=ca and q+8<=cb and z&0xffe00000==0xd2800000 and v&~0x1fffe0==z&~0x1fffe0 and a[p//4+1]&~0x1fffe0==0xf2a00000|(z&31) and b[q//4+1]&~0x1fffe0==a[p//4+1]&~0x1fffe0:
   ov=(z>>5&65535)|((a[p//4+1]>>5&65535)<<16);nv=(v>>5&65535)|((b[q//4+1]>>5&65535)<<16)
   assert any(ca+x['offset']==ov for x in entries) and nv==cb+ov-ca,(e['tag'],'unowned literal',p,ov,nv)
   for j in [0,4]:mapping[p+j]=q+j;pairs.append((p+j,q+j,'source-literal-relocation'))
   p+=8;q+=8;continue
  # Existing accepted trailing literal plane, after a terminal RET. Only
  # alignment NOP changes; the complete remaining tail must be identical.
  if p>=4 and q>=4 and a[p//4-1]==b[q//4-1]==0xd65f03c0:
   skipA=4 if z==0xd503201f and p%8==4 and q%8==0 else 0
   skipB=4 if v==0xd503201f and q%8==4 and p%8==0 else 0
   if skipA or skipB:
    assert A[p+skipA:]==B[q+skipB:],'UNKNOWN_TRAILING_DATA_CHANGE'
    padding.append(dict(oldPC=p,newPC=q,removedNOP=bool(skipA),insertedNOP=bool(skipB),completeRemainingTailIdentical=True))
    if skipA:mapping[p]=q
    p+=skipA;q+=skipB
    while p<ca:mapping[p]=q;pairs.append((p,q,'retained'));p+=4;q+=4
    continue
  raise AssertionError((e['tag'],'UNKNOWN_CHANGE',p,q,hex(z),hex(v) if v is not None else None))
 if q<cb:
  assert q==cb-4 and b[q//4] in [0,0xd503201f];padding.append(dict(newPC=q,inserted=b[q//4]));q+=4
 assert p==ca and q==cb
 def pos(t):
  if ca<=t<=len(A):return cb+t-ca
  assert t in mapping,('UNMAPPED_TARGET',t);return mapping[t]
 for address in nativeAddresses:assert address['newTarget']==pos(address['oldTarget']), ('NATIVE_ADDRESS_TARGET_MISMATCH',address)
 refs=[]
 for op,np,why in pairs:
  z=a[op//4];v=b[np//4];r=m.ref(z,op);t=m.ref(v,np)
  if r or t:
   assert r and t and r[0]==t[0] and z&r[2]==v&t[2] and t[1]==pos(r[1]),('REFERENCE_MISMATCH',op,np,r,t)
   for recipe in returnComparisons:
    if recipe.get('recipe')=='exact-copy-elimination-across-fuel-reload':assert not recipe['oldPC']<r[1]<=recipe['oldPC']+12, ('COMPARISON_INTERIOR_ENTRY',recipe,r)
   refs.append(dict(oldPC=op,newPC=np,kind=r[0],oldTarget=r[1],newTarget=t[1]))
  elif why=='retained':assert z==v
 for name,x in ae.items():assert be[name]==[pos(x[0]),x[1]]
 assert set(ae)==set(be)
 return dict(tag=e['tag'],status='MAPPED_WHOLE_FUEL_CONTEXT_RELOAD_CANDIDATE_REQUIRES_CONTEXT_AND_CACHE_CFG_PROOFS',changed=True,charges=charges,insertedFuelLoads=insertions,insertedContextReloads=contextReloads,removedContextReloads=removedContextReloads,mappedNativeAddresses=nativeAddresses,returnComparisons=returnComparisons,padding=padding,refs=refs,wordPairs=pairs,oldCodeEnd=ca,newCodeEnd=cb,sourcePoolBytes=len(pool),cacheLivenessProofPending=True,guestCalls=0,adopted=False)
if __name__=='__main__':
 import sys
 plan=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(exist_ok=False);j=json.loads(plan.read_text());rows=[]
 for e in j['images']:
  if len(sys.argv)>3 and e['tag'] not in sys.argv[3:]:continue
  try:r=audit(e)
  except BaseException as ex:r=dict(tag=e['tag'],status='FIRST_UNKNOWN_CHANGE_PRESERVED',error=repr(ex),adopted=False)
  (out/(e['tag']+'.json')).write_text(json.dumps(r,indent=2)+'\n');rows.append(dict(tag=e['tag'],status=r['status']));print(e['tag'],r['status'],r.get('error',''))
 (out/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')
