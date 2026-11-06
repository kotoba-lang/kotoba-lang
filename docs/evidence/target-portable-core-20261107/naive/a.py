import struct,sys
M=2**63-1;m=-2**63
def p(k,v): print(f"{k}={v}")
p("add_overflow",M+1);p("mul_overflow",4611686018427387904*4);p("min_quot_m1",int(m/-1) if False else m//-1)
try: p("div0",1//0)
except Exception as e: p("div0","throw "+type(e).__name__)
p("quot_-7_2",-7//2);p("ushr_1_64",1>>64);p("shl_1_64",1<<64);p("strlen_あ",len("あ"))
a=1.0+2**-52;b=1.0-2**-52;p("f64_mul_add_bits",struct.unpack('<q',struct.pack('<d',a*b-1.0))[0])
p("f32_add_bits","no-native-f32")
def f(n): return 0 if n==0 else 1+f(n-1)
try: p("deep_rec",f(100000000))
except BaseException as e: p("deep_rec","throw "+type(e).__name__)
