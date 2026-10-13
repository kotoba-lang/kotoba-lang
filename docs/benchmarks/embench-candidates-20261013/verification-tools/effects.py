def sx(x,n):return x-(1<<n) if x>>(n-1) else x
def effects(w,pc):
 rd=w&31;rn=w>>5&31;rm=w>>16&31
 def normal(read,write):return set(read),set(write),[pc+4],'normal'
 if w&0x7c000000==0x14000000:
  target=pc+4*sx(w&0x3ffffff,26)
  return set(),({30} if w&0x80000000 else set()),([target,pc+4] if w&0x80000000 else [target]),'direct-branch/call'
 if w&0xff000010==0x54000000:return set(),set(),[pc+4,pc+4*sx(w>>5&0x7ffff,19)],'Bcond'
 if w&0x7e000000==0x34000000:return {rd},set(),[pc+4,pc+4*sx(w>>5&0x7ffff,19)],'CB'
 if w&0x7e000000==0x36000000:return {rd},set(),[pc+4,pc+4*sx(w>>5&0x3fff,14)],'TB'
 if w&0xffe0001f==0xd4200000:return set(),set(),[],'synchronous-trap-scratch-not-source-home'
 if w==0:return set(),set(),[],'source-gn-e-udf-zero-synchronous-trap'
 if w&0xfffffc1f==0xd65f0000:return {rn},set(),[],'return-scratch-not-return-register'
 if w&0xfffffc1f==0xd63f0000:return {rn},set(range(8))|{16,17,30},[pc+4],'issued-runtime-callback-ABI'
 if w&0xfffffc1f==0xd61f0000:return {rn},set(),[],'indirect-tail-ABI'
 if w==0xd503201f:return normal([],[])
 if w&0x9f000000 in (0x10000000,0x90000000):return normal([], [rd])
 if w&0x1f800000==0x12800000:return normal(([rd] if w&0x60000000==0x60000000 else []),[rd])
 if w&0x1f000000==0x11000000:return normal([rn],[rd])
 if w&0x1f000000==0x0b000000:return normal([rn,rm],[rd])
 if w&0x1f000000==0x0a000000:return normal([rn,rm],[rd])
 if w&0x1f800000==0x12000000:return normal([rn],[rd])
 if w&0x1f800000==0x13000000:return normal(([rn,rd] if w&0x60000000==0x20000000 else [rn]),[rd])
 # Exact64 conditional-select encodings. NZCV is explicit sentinel32;
 # no variant writes NZCV, and register31 is XZR (not SP).
 if w&0xffe00c00 in (0x9a800000,0x9a800400,0xda800000,0xda800400):
  return normal([rn,rm,32],[rd])
 if w&0x1fe00000==0x1ac00000:return normal([rn,rm],[rd])
 if w&0x1f000000==0x1b000000:return normal([rn,rm,w>>10&31],[rd])
 if w&0x3b000000==0x39000000:
  return normal(([rn] if w&0x00400000 else [rn,rd]),([rd] if w&0x00400000 else []))
 if w&0x3b200c00==0x38200800:
  return normal(([rn,rm] if w&0x00400000 else [rn,rm,rd]),([rd] if w&0x00400000 else []))
 if w&0x3b200000==0x38000000:
  read=[rn] if w&0x00400000 else [rn,rd];write=[rd] if w&0x00400000 else []
  if w&0x00000400:write.append(rn)
  return normal(read,write)
 if w&0x3a000000==0x28000000:
  rt2=w>>10&31;read=[rn] if w&0x00400000 else [rn,rd,rt2];write=[rd,rt2] if w&0x00400000 else []
  if w>>23&3 in (1,3):write.append(rn)
  return normal(read,write)
 raise AssertionError(('unsupported-effects',pc,hex(w)))
