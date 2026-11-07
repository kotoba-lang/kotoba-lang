const p=(k,v)=>console.log(`${k}=${v}`);const M=9223372036854775807,m=-(2**63);
p("add_overflow(number)",M+1);p("add_overflow(bigint)",9223372036854775807n+1n);p("mul_overflow(bigint)",4611686018427387904n*4n);p("min_quot_m1(bigint)",(-(2n**63n))/-1n);
try{p("div0(bigint)",1n/0n)}catch(e){p("div0(bigint)","throw "+e.name)};p("div0(number)",1/0);
p("quot_-7_2(number /)",-7/2);p("quot_-7_2(bigint)",-7n/2n);p("ushr_1_64(bigint)",1n>>64n);p("shl_1_64(bigint)",1n<<64n);p("ushr_1_64(number)",1>>>64);p("strlen_あ","あ".length);
const a=1+2**-52,b=1-2**-52;const dv=new DataView(new ArrayBuffer(8));dv.setFloat64(0,a*b-1);p("f64_mul_add_bits",dv.getBigInt64(0));
dv.setFloat32(0,Math.fround(Math.fround(0.1)+Math.fround(0.2)));p("f32_add_bits",dv.getInt32(0));
function f(n){return n==0?0:1+f(n-1)};try{p("deep_rec",f(100000000))}catch(e){p("deep_rec","throw "+e.name)}
