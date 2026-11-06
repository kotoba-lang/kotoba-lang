#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <limits.h>
__attribute__((noinline)) int64_t id(int64_t v){return v;}
__attribute__((noinline)) double dd(double v){return v;}
__attribute__((noinline)) int64_t f(int64_t n){return n==0?0:1+f(n-1);}
int main(int c,char**v){int64_t max=id(INT64_MAX),s=id(64),one=id(1);
printf("add_overflow=%lld\n",(long long)(max+one));
printf("mul_overflow=%lld\n",(long long)(id(4611686018427387904LL)*id(4)));
printf("quot_-7_2=%lld\n",(long long)(id(-7)/id(2)));
printf("ushr_1_64=%llu\n",(unsigned long long)((uint64_t)one>>s));
printf("shl_1_64=%lld\n",(long long)(one<<s));
printf("strlen_あ=%zu\n",strlen("あ"));
double a=dd(1.0+0x1p-52),b=dd(1.0-0x1p-52),cc=dd(-1.0);double r=a*b+cc;int64_t bits;memcpy(&bits,&r,8);printf("f64_mul_add_bits=%lld\n",(long long)bits);
float x=0.1f,y=0.2f;volatile float xv=x,yv=y;float z=xv+yv;int32_t i;memcpy(&i,&z,4);printf("f32_add_bits=%d\n",i);
if(c>1&&v[1][0]=='d'){printf("div0=%lld\n",(long long)(one/id(0)));}
if(c>1&&v[1][0]=='m'){printf("min_quot_m1=%lld\n",(long long)(id(INT64_MIN)/id(-1)));}
if(c>1&&v[1][0]=='r'){printf("deep_rec=%lld\n",(long long)f(100000000));}
return 0;}
