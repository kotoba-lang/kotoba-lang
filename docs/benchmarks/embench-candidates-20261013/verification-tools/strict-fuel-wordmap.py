from pathlib import Path
import json,struct,importlib.util,hashlib
W=Path(__file__).parent
s=importlib.util.spec_from_file_location('nm',W.parent/'embench-dve-ocb-sdiv2-composed-20261012/same-layout-two-call-arm-v4/qualification-source-v1/native-map.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
def audit(e):
 A,ae=m.payload(e['baseline']);B,be=m.payload(e['arm']);src=Path(e['source']['path']).read_bytes();assert src==Path(e['baseline']['source']['path']).read_bytes()
 if A==B:assert ae==be;return dict(tag=e['tag'],status='WHOLE_IDENTITY',changed=False)
 pool,entries=m.source_pool(src);ca=len(A)-len(pool);cb=len(B)-len(pool)
 if pool:assert A[ca:]==B[cb:]==pool
 assert ca%4==cb%4==0
 a=list(struct.unpack('<'+'I'*(ca//4),A[:ca]));b=list(struct.unpack('<'+'I'*(cb//4),B[:cb]));mapping={};pairs=[];insertions=[];charges=[];padding=[];p=q=0
 while p<ca:
  z=a[p//4];v=b[q//4] if q<cb else None
  if p+16<=ca and a[p//4:p//4+2]==[0xf94004f0,0xf1000610] and a[p//4+2]&0xff00001f==0x54000003 and a[p//4+3]==0xf90004f0:
   lead=1 if v==0xf94004e8 else 0;k=q//4+lead
   if k+3<=len(b) and b[k]==0xf1000508 and b[k+1]&0xff00001f==0x54000003 and b[k+2]==0xf90004e8:
    mapping[p]=q
    if lead:pairs.append((p,q,'fuel-entry-load16-to8'))
    for j,reason in [(1,'fuel-subs16-to8'),(2,'reference'),(3,'fuel-store16-to8')]:
     np=q+4*(j-1+lead);mapping[p+4*j]=np;pairs.append((p+4*j,np,reason))
    charges.append(dict(oldPC=p,newPC=q,retainedEntryLoad=bool(lead)));p+=16;q+=4*(3+lead);continue
  rz=m.ref(z,p);rv=m.ref(v,q) if v is not None else None
  if z==v or (rz and rv and rz[0]==rv[0] and z&rz[2]==v&rv[2]):mapping[p]=q;pairs.append((p,q,'retained'));p+=4;q+=4;continue
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
 refs=[]
 for op,np,why in pairs:
  z=a[op//4];v=b[np//4];r=m.ref(z,op);t=m.ref(v,np)
  if r or t:
   assert r and t and r[0]==t[0] and z&r[2]==v&t[2] and t[1]==pos(r[1]),('REFERENCE_MISMATCH',op,np,r,t)
   refs.append(dict(oldPC=op,newPC=np,kind=r[0],oldTarget=r[1],newTarget=t[1]))
  elif why=='retained':assert z==v
 for name,x in ae.items():assert be[name]==[pos(x[0]),x[1]]
 assert set(ae)==set(be)
 return dict(tag=e['tag'],status='PASS_STRICT_WHOLE_FUEL_RECIPE_REFERENCE_LITERAL_POOL_EXPORT_MAP_NOT_CACHE_LIVENESS',changed=True,charges=charges,insertedFuelLoads=insertions,padding=padding,refs=refs,wordPairs=pairs,oldCodeEnd=ca,newCodeEnd=cb,sourcePoolBytes=len(pool),cacheLivenessProofPending=True,guestCalls=0,adopted=False)
if __name__=='__main__':
 import sys
 plan=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(exist_ok=False);j=json.loads(plan.read_text());rows=[]
 for e in j['images']:
  if len(sys.argv)>3 and e['tag'] not in sys.argv[3:]:continue
  try:r=audit(e)
  except BaseException as ex:r=dict(tag=e['tag'],status='FIRST_UNKNOWN_CHANGE_PRESERVED',error=repr(ex),adopted=False)
  (out/(e['tag']+'.json')).write_text(json.dumps(r,indent=2)+'\n');rows.append(dict(tag=e['tag'],status=r['status']));print(e['tag'],r['status'],r.get('error',''))
 (out/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')
