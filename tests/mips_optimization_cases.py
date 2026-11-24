"""Independent arithmetic and alias checks for initial backend optimizations."""
import random

def trunc_div(a, b):
    return (abs(a) // abs(b)) * (-1 if (a < 0) != (b < 0) else 1)

values = [-2147483648, -2147483647, -1073741825, -65537, -32769, -129, -9, -8, -7,
          -1, 0, 1, 7, 8, 9, 127, 255, 32767, 65535, 2147483647]
values += random.Random(2026).sample(range(-10000000, 10000000), 20)
divisors = [1, 2, -2, 4, -4, 128, -128, 32768, -32768, 65536, -65536, 1073741824, -1073741824]
body = ''.join(f'printf("%d %d ",n/({d}),n%({d}));' for d in divisors)
expected = ''.join(f'{trunc_div(n,d)} {n-trunc_div(n,d)*d} ' for n in values for d in divisors)
signed_source = 'void f(int n){'+body+'}int main(){int a['+str(len(values))+']={'+','.join(map(str,values))+'};int i=0;while(i<'+str(len(values))+'){f(a[i]);i=i+1;}return 0;}'

char_values = [0, 1, 2, 3, 127, 128, 129, 200, 254, 255]
char_body = ''.join(f'printf("%d %d ",(int)(n/(char){d}),(int)(n%(char){d}));' for d in [1,2,4,128])
char_source = 'void f(char n){'+char_body+'}int main(){'+''.join(f'f((char){n});' for n in char_values)+'return 0;}'
char_expected = ''.join(f'{n//d} {n%d} ' for n in char_values for d in [1,2,4,128])

OPTIMIZATION_CASES = [
    ('opt-signed-div-rem', signed_source, '', expected, 0),
    ('opt-char-div-rem', char_source, '', char_expected, 0),
    ('opt-loop-sum', 'int main(){int i=0,s=0;while(i<1000){s=s+(i*8+3)/4;i=i+1;}put_int(s);return 0;}', '', '999000', 0),
    ('opt-alias-call', 'int f(int p[]){p[0]=99;return p[0];}int main(){int a[1]={7};int x=a[0];int y=f(a);printf("%d %d %d",x,y,a[0]);return 0;}', '', '7 99 99', 0),
    ('opt-large-immediates', 'int f(int x){return x+32767-32768+65536-65537+2147483647;}int main(){put_int(f(-2147483647));return 0;}', '', '-2', 0),
    ('opt-multiply-powers', 'int f(int x){printf("%d %d %d %d %d %d ",x*0,x*1,x*(-1),x*8,x*(-2147483648),x*(-8));return 0;}int main(){f(-7);f(2147483647);return 0;}', '', '0 -7 7 -56 -2147483648 56 0 2147483647 -2147483647 -8 -2147483648 8 ', 0),
    ('opt-calls-and-cache', 'int f(int x){put_int(x);return x*8+1;}int main(){int a=f(2);int b=f(3);printf(" %d %d",a,b);return 0;}', '', '23 17 25', 0),
    ('opt-byte-alias', 'void f(char p[]){p[0]=(char)255;}int main(){char a[3]="ab";char old=a[0];f(a);printf("%c %d %c",old,(int)a[0],a[1]);return 0;}', '', 'a 255 b', 0),
]
