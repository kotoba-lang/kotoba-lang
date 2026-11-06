public class A { public static void main(String[] x){ long max=Long.MAX_VALUE,min=Long.MIN_VALUE,m1=-1,z=0,s=64,one=1;
 p("add_overflow",""+(max+one)); p("mul_overflow",""+(4611686018427387904L*4L));
 try{p("min_quot_m1",""+(min/m1));}catch(Throwable e){p("min_quot_m1","throw");}
 try{p("div0",""+(one/z));}catch(Throwable e){p("div0","throw "+e.getClass().getSimpleName());}
 p("quot_-7_2",""+(-7/2)); p("ushr_1_64",""+(one>>>s)); p("shl_1_64",""+(one<<s));
 p("strlen_あ",""+"あ".length());
 double a=1.0+Math.pow(2,-52),b=1.0-Math.pow(2,-52); double c=-1.0; p("f64_mul_add_bits",""+Double.doubleToRawLongBits(a*b+c));
 p("f32_add_bits",""+Float.floatToRawIntBits(0.1f+0.2f));
 try{p("deep_rec",""+f(100000000));}catch(Throwable e){p("deep_rec","throw "+e.getClass().getSimpleName());}}
 static long f(long n){return n==0?0:1+f(n-1);} static void p(String k,String v){System.out.println(k+"="+v);} }
