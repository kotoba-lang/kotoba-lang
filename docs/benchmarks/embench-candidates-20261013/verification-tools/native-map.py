from pathlib import Path
import json,re,struct,hashlib,bisect

def sx(x,n):return x-(1<<n)if x&(1<<(n-1))else x

def ref(w,p):
 if w&0x7c000000==0x14000000:return('BL'if w&0x80000000 else'B',p+4*sx(w&0x3ffffff,26),0xfc000000)
 if w&0xff000010==0x54000000:return('BC',p+4*sx(w>>5&0x7ffff,19),0xff00001f)
 if w&0x7e000000==0x34000000:return('CB',p+4*sx(w>>5&0x7ffff,19),0xff00001f)
 if w&0x7e000000==0x36000000:return('TB',p+4*sx(w>>5&0x3fff,14),0xfff8001f)
 if w&0x3b000000==0x18000000:return('LIT',p+4*sx(w>>5&0x7ffff,19),0xff00001f)
 if w&0x9f000000==0x10000000:return('ADR',p+sx(((w>>5&0x7ffff)<<2)|(w>>29&3),21),0x9f00001f)
 assert w&0x9f000000!=0x90000000,('UNKNOWN_ADRP',p)
 return None

def payload(a):
 b=Path(a['native']['path']).read_bytes();h,v=Path(a['container']['path']).read_bytes().split(b'\n\n',1);ls=h.decode().splitlines();m,n,c=ls[0].split()
 assert m=='KSEED1'and int(n)==len(b)and int(c)==len(ls)-1 and v==b
 ex={}
 for row in ls[1:]:
  name,p,k=row.split();assert name not in ex;ex[name]=[int(p),int(k)];assert int(p)%4==0 and 0<=int(p)<len(b)
 if 'exports'in a:assert ex=={k:list(v)for k,v in a['exports'].items()}
 for kind,data in [('native',b),('container',Path(a['container']['path']).read_bytes())]:
  if 'sha256'in a[kind]:assert hashlib.sha256(data).hexdigest()==a[kind]['sha256']
 return b,ex

def source_pool(source):
 # Preserve quoted text while stripping comments; exact lexical strings and
 # complete constant numeric vectors only, otherwise pool equality refuses.
 text=source.decode();clean=[];strings=[];i=0
 while i<len(text):
  if text[i]==';':
   j=text.find('\n',i);j=len(text)if j<0 else j;clean.extend(' '*(j-i));i=j
  elif text[i]=='"':
   start=i;i+=1;escape=False
   while i<len(text):
    ch=text[i];i+=1
    if escape:escape=False
    elif ch=='\\':escape=True
    elif ch=='"':break
   else:raise AssertionError('UNTERMINATED_SOURCE_STRING')
   literal=text[start:i];data=json.loads(literal).encode();strings.append((start,'string',data));clean.extend(literal)
  else:clean.append(text[i]);i+=1
 clean=''.join(clean);events=strings[:]
 for m in re.finditer(r'\(def\s+[^\s()]+\s+\[([^]\[]*)\]\)',clean):
  tokens=m.group(1).split();assert all(re.fullmatch(r'-?[0-9]+',x)for x in tokens),'UNKNOWN_NUMERIC_LITERAL_SOURCE'
  events.append((m.start(),'numericVector',b''.join(struct.pack('<Q',int(x)&((1<<64)-1))for x in tokens)))
 pool=b'';entries=[]
 for _,kind,data in sorted(events):
  pool+=bytes((-len(pool))%8);entries.append(dict(offset=len(pool),kind=kind,length=len(data)));pool+=data
 return pool,entries

def arithmetic_audit(old,arm):
 A,ae=payload(old);N,ne=payload(arm);S=Path(old['source']['path']).read_bytes();assert S==Path(arm['source']['path']).read_bytes()
 if A==N:assert ae==ne;return dict(status='PASS_WHOLE_NATIVE_CONTAINER_EXPORT_SOURCE_EXACT',changed=False)
 pool,entries=source_pool(S);op=len(A)-len(pool);np=len(N)-len(pool);oa=na=0
 if pool:
  assert A[op:]==N[np:]==pool and op%8==np%8==0,'SOURCE_LITERAL_POOL_MISMATCH'
  oa=4 if A[op-4:op]==bytes(4)else 0;na=4 if N[np-4:np]==bytes(4)else 0
  assert (op-oa+7)&-8==op and (np-na+7)&-8==np
  assert int.from_bytes(A[op-oa-4:op-oa],'little')&0xffe0001f==0xd4200000
  assert int.from_bytes(N[np-na-4:np-na],'little')&0xffe0001f==0xd4200000
 ca,cn=op-oa,np-na;assert ca%4==cn%4==0
 ow=list(struct.unpack('<'+'I'*(ca//4),A[:ca]));nw=list(struct.unpack('<'+'I'*(cn//4),N[:cn]));removed=[];replace={};sites=[]
 p=q=0
 while p<ca:
  if p+60<=ca:
   x=ow[p//4:p//4+15]
   valid=(x[:2]==[0xf9409cf0,0xf9400210] and x[2]&~(31<<5)==0xd1000411
    and x[3:11]==[0xeb10023f,0x54000043,0,0xf940a0f0,0x8b111210,0xa9400e11,0xf940a4e4,0x8b110c84]
    and x[11]&~(31<<5)==0xeb03001f and x[12:14]==[0x54000043,0]
    and x[14]&~((31<<16)|31)==0xf8607880)
   rv=x[2]>>5&31;ri=x[11]>>5&31;dest=x[14]&31
   if valid and 19<=rv<=28 and q+16<=cn and nw[q//4:q//4+4]==x[11:15]:
    removed.extend(range(p,p+44,4))
    sites.append(dict(oldPC=p,newPC=q,handleRegister=rv,indexRegister=ri,destination=dest,
     deletedOriginalDescriptorPreflight=[f'{v:08x}' for v in x[:11]],
     retainedOriginalIndexCheckTrapAndPayloadLoad=[f'{v:08x}' for v in x[11:15]],
     indexCompareNZCVAndOriginalPayloadAddressRecipeRetained=True))
    p+=60;q+=16;continue
  p+=4;q+=4
 assert sites and cn==ca-4*len(removed),('UNKNOWN_SIZE_OR_RECIPE',len(A),len(N),len(sites))
 forbidden=set(removed)
 def pos(p):
  if op<=p<=len(A):return np+p-op
  assert 0<=p<ca and p%4==0 and p not in forbidden,('REMOVED_PADDING_OR_UNKNOWN_TARGET',p)
  return p-4*bisect.bisect_left(removed,p)
 literal={};literalRows=[];faddr={};faddrRows=[]
 # Source-owned FADDR ADR+ADD/SUB low/high pointer recipe. No arbitrary
 # arithmetic-immediate normalization; complete register/target chain only.
 for p in range(0,ca-8,4):
  if p in removed or p in replace:continue
  a,b,c=ow[p//4:p//4+3];d=a&31
  if a!=0x10000000|d or d==31:continue
  if b&~0x3ffc00 not in (0x91000000|(d<<5)|d,0xd1000000|(d<<5)|d):continue
  sign=-1 if b&0x40000000 else 1;want=(0xd1400000 if sign<0 else 0x91400000)|(d<<5)|d
  if c&~0x3ffc00!=want:continue
  target=p+sign*((b>>10&4095)+(c>>10&4095)*4096)
  assert target not in forbidden,'FADDR_INTERIOR_ENTRY'
  nt=pos(target);delta=nt-pos(p);mag=abs(delta);assert mag>>12<=4095
  base=0xd1000000 if delta<0 else 0x91000000
  faddr[p+4]=base|(d<<5)|d|((mag&4095)<<10);faddr[p+8]=base|0x400000|(d<<5)|d|((mag>>12)<<10)
  faddrRows.append(dict(oldPC=p,newPC=pos(p),rd=d,oldTarget=target,newTarget=nt))
 for p in range(8,ca-24,4):
  if p in removed or p in replace or p+4 in removed:continue
  u=ow[p//4];v=nw[pos(p)//4]
  if u==v or u&0xffe00000!=0xd2800000:continue
  rd=u&31;assert rd in(1,16,17)and v&~0x1fffe0==u&~0x1fffe0,'UNKNOWN_MOVZ_CHANGE'
  second=ow[p//4+1];assert second&~0x1fffe0==0xf2a00000|rd
  val=(u>>5&65535)|((second>>5&65535)<<16)
  owned=[e for e in entries if e['offset']==val-op]
  if rd==1:owned=[e for e in owned if e['kind']=='string' and ow[p//4+2]==0xd2800002|(e['length']<<5)]
  assert len(owned)==1,'UNKNOWN_SOURCE_LITERAL_ENTRY'
  entry=owned[0];length=entry['length'];at=p//4
  if rd==1:
   assert entry['kind']=='string'and ow[at+2]==0xd2800002|(length<<5)
   assert (ow[at+3]&~0x3ffc00==0xf94003e0 or ow[at+3]==0xaa0703e0)and ow[at+4:at+6]==[0xf9401c10,0xd63f0200]
   reason='gn-op-str-pair/gn-rtcall source offset+length+CTX callback56'
  elif rd==17 and entry['kind']=='string':
   assert ow[at-3]==0xd2800011|(length<<5)and ow[at-2]==0xeb11013f and ow[at-1]&~0xffffe0==0x54000001
   assert ow[at+2]==0xeb11011f and ow[at+3]&~0xffffe0==0x54000000
   candidates=[]
   for t in range(max(7,at-41),at):
    if ow[t]==0xa9402608 and ow[t-2:t]==[0xf94094f0,0x8b111210]and ow[t-7:t-5]==[0xf94090f0,0xf9400210]and ow[t-5]&~0x3e0==0xd1000411 and ow[t-4]==0xeb10023f and ow[t-3]&~0xffffe0==0x54000002:candidates.append(t)
   assert len(candidates)==1,'UNKNOWN_AC_STRING_DESCRIPTOR'
   reason='ac-pool source offset/length exact issued descriptor comparison'
  elif rd==16 and ow[at-1]==0xf940c0e5:
   assert ow[at+2:at+6]==[0x8b1000a5,0xd2800002,0xf9400080,0xf94000a1]or(ow[at+2]==0x8b1000a5 and all(ow[at+j]&~(31<<16)==x for j,x in[(3,0xaa0003e1),(4,0xaa0003e2),(5,0xaa0003e3)])and ow[at+6:at+8]==[0xf940048a,0xf940088b])
   reason='source literal-plane CTX_CODE_BASE5+offset16+ordered loads'
  else:
   assert rd==16 and ow[at-2]==0xf940c0f1 and ow[at-1]&~(31<<16)==0x8b000e31 and ow[at+2]&~31==0xf8706a20
   reason='tc-call source literal CTX_CODE_BASE17/index8/offset16/LDR'
  nv=pos(val);literal[p]=(u&~0x1fffe0)|((nv&65535)<<5);literal[p+4]=(second&~0x1fffe0)|((nv>>16)<<5)
  literalRows.append(dict(oldPC=p,newPC=pos(p),oldValue=val,newValue=nv,kind=entry['kind'],length=length,reason=reason))
 maps=[];refs=[]
 for p in range(0,ca,4):
  if p in removed:continue
  q=pos(p);u=ow[p//4];v=nw[q//4];expected=replace.get(p,u);reason='retained'
  r=ref(u,p);t=ref(v,q)
  if r or t:
   assert p not in replace and r and t and r[0]==t[0]and u&r[2]==v&t[2]
   assert r[1]not in forbidden,('EXTERNAL_INTERIOR_ENTRY',p,r)
   assert t[1]==pos(r[1]),('PC_TARGET',p,q,r,t);expected=v;reason='pc-relative-retained';refs.append(dict(oldPC=p,newPC=q,kind=r[0],oldTarget=r[1],newTarget=t[1]))
  elif p in literal:expected=literal[p];reason='source-owned-LIT32'
  elif p in faddr:expected=faddr[p];reason='source-owned-FADDR-lowhigh'
  assert v==expected,('UNEXPECTED_WORD',p,q,hex(u),hex(v),hex(expected))
  maps.append(dict(oldPC=p,newPC=q,oldWord=f'{u:08x}',newWord=f'{v:08x}',reason=reason))
 assert set(ae)==set(ne)
 for name,x in ae.items():assert x[0]not in forbidden and ne[name]==[pos(x[0]),x[1]],('EXPORT',name,x,ne.get(name))
 return dict(status='PASS_DVE_OMITTED_PREFLIGHT_FULL_RETAINED_WORD_TARGET_POOL_EXPORT_MAP',changed=True,oldBytes=len(A),newBytes=len(N),sites=sites,removedWords=removed,retainedWords=len(maps)-len(replace),PCReferences=len(refs),literalPairs=literalRows,FADDR=faddrRows,sourcePoolBytes=len(pool),sourcePoolEntries=entries,oldRawCodeEnd=ca,newRawCodeEnd=cn,oldPool=op,newPool=np,oldAlignmentBytes=oa,newAlignmentBytes=na,exports=ne,wordMap=maps,PCMap=refs,allOtherMemoryFuelFrameCallTrapWordsRetained=True,indexFlagsRetained=True,removedCheckScratch16And17RequireInheritedABIAndHistoryProof=True,typedInstanceAuthority='Exact existing ve-read mode-minus1 preflight and bounded dve source/emitted effects; this word-map audit alone does not claim fresh typed-GN snapshot or runtime reachability.',noPerformanceClaim=True)

if __name__=='__main__':
 W=Path(__file__).parent;P=json.loads((W/'PLAN.json').read_text())
 result=arithmetic_audit(P['OFF'],P['ON'])
 (W/'report.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({k:v for k,v in result.items() if k not in ['wordMap','PCMap']},indent=2))
