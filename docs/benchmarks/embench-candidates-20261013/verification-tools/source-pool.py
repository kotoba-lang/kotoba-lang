from pathlib import Path
import re,json,struct,hashlib
def source_pool(source):
 S=source.decode();t=[m for m in re.finditer(r';[^\n]*|"(?:\\.|[^"\\])*"|[()\[\]]|[^\s()\[\]";]+',S)if not m.group().startswith(';')];at=0
 class N:
  def __init__(self,v,p,k='atom'):self.v=v;self.p=p;self.k=k
 
 def parse():
  nonlocal at
  x=t[at];at+=1;v=x.group()
  if v in ['(','[']:
   end=')'if v=='('else']';ls=[]
   while t[at].group()!=end:ls.append(parse())
   at+=1;return N(ls,x.start(),v)
  if v.startswith('"'):return N(json.loads(v),x.start(),'string')
  try:z=int(v,16 if 'x'in v.lower()else 10)
  except ValueError:z=v
  return N(z,x.start())
 fs=[]
 while at<len(t):fs.append(parse())
 for f in fs:
  a=f.v
  assert not(isinstance(a,list)and len(a)>=3 and a[0].v=='def'and a[2].k=='['and a[2].v),'GLOBAL_VECTOR_DEF_UNSUPPORTED_THIS_TRANSITION'
 strings=[dict(sourceOffset=x.start(),kind='string',bytes=json.loads(x.group()).encode())for x in t if x.group().startswith('"')];events=[]
 def ints(n):return n.k=='['and bool(n.v)and all(isinstance(x.v,int)for x in n.v)
 def append(n,owner,why):
  events.append(dict(sourceOffset=n.p,kind='numericVector',bytes=b''.join(struct.pack('<Q',x.v&((1<<64)-1))for x in n.v),owner=owner,why=why,items=len(n.v)))
 def walk(n,owner):
  if not isinstance(n.v,list):return
  a=n.v
  if n.k=='[' and ints(n)and len(a)>64:
   assert len(a)<=128,'UNSUPPORTED_LITERAL_VECTOR_EXTENT'
   append(n,owner,'lw-expr ND-VEC >64 OP-VECP');return
  if n.k=='(' and a and a[0].v=='vector-at'and len(a)==3 and ints(a[1]):append(a[1],owner,'lw-tabsel RT-VECTOR-AT/lw-tabdata-before-index');walk(a[2],owner);return
  for x in a:walk(x,owner)
 for f in fs:
  a=f.v
  if not isinstance(a,list)or not a or a[0].v not in ['defn','defn-']:continue
  if a[2].k=='string':a=a[:2]+a[3:]
  if a[2].v and isinstance(a[2].v[0].v,list):
   for sig in a[2:]:walk(sig.v[2],a[1].v)
  else:walk(a[4],a[1].v)
 pool=b'';rows=[]
 for e in strings+events:
  pool+=bytes((-len(pool))%8);rows.append({k:v for k,v in e.items()if k!='bytes'}|dict(offset=len(pool),length=len(e['bytes']),sha256=hashlib.sha256(e['bytes']).hexdigest()));pool+=e['bytes']
 return pool,rows
