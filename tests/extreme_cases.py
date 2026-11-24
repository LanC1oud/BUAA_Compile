"""Independent oracles for SSA cycles, CFG inlining, aliasing and reciprocal division."""
import random

def div(a, b):
    return (abs(a)//abs(b)) * (-1 if (a < 0) != (b < 0) else 1)

def wrap(x): return (x+2147483648)%4294967296-2147483648

values = [-2147483648, -2147483647, -65537, -129, -1, 0, 1, 127, 65535, 2147483647]
values += random.Random(1124).sample(range(-2147483648, 2147483648), 35)
divisors = [3,-3,5,-5,7,-7,10,-10,31,-31,127,-127,255,-255,65535,-65535,
            123456789,-123456789,2147483647,-2147483647,-2147483648]
body = ''.join(f'printf("%d %d ",n/({d}),n%({d}));' for d in divisors)
magic = 'void f(int n){'+body+'}int main(){int a['+str(len(values))+']={'+','.join(map(str,values))+'};int i=0;while(i<'+str(len(values))+'){f(a[i]);i=i+1;}return 0;}'
expected_magic = ''.join(f'{div(n,d)} {n-div(n,d)*d} ' for n in values for d in divisors)

char_body = ''.join(f'printf("%d %d ",(int)(n/(char){d}),(int)(n%(char){d}));' for d in [3,5,7,10,31,127,129,200,255])
char_source = 'void f(char n){'+char_body+'}int main(){int i=0;while(i<256){f((char)i);i=i+1;}return 0;}'
char_expected = ''.join(f'{n//d} {n%d} ' for n in range(256) for d in [3,5,7,10,31,127,129,200,255])

def rotate(n):
    a,b,c,s=1,2,3,0
    for _ in range(n):
        a,b,c=b,c,a
        s += a*100+b*10+c
    return f'{a} {b} {c} {s}'

def pressure(n):
    a=list(range(1,15))
    for i in range(n):
        a=[v+i+k for k,v in enumerate(a)]
    return ' '.join(map(str,a))

declarations=''.join(f'int a{k}={k+1};' for k in range(14))
increments=''.join(f'a{k}=a{k}+i+{k};' for k in range(14))
fmt=' '.join(['%d']*14)
pressure_source='int main(){int n=get_int();'+declarations+'int i=0;while(i<n){'+increments+'i=i+1;}printf("'+fmt+'",'+','.join(f'a{k}' for k in range(14))+');return 0;}'

EXTREME_CASES = [
    ('extreme-magic-signed',magic,'',expected_magic,0),
    ('extreme-magic-char',char_source,'',char_expected,0),
    ('extreme-phi-cycle','int main(){int n=get_int();int a=1,b=2,c=3,s=0,i=0;while(i<n){int t=a;a=b;b=c;c=t;s=s+a*100+b*10+c;i=i+1;}printf("%d %d %d %d",a,b,c,s);return 0;}','37\n',rotate(37),0),
    ('extreme-loop-pressure',pressure_source,'40\n',pressure(40),0),
    ('extreme-inline-returns','int f(int a){if(a<0)return -a;if(a==0)return 7;return a*3;}int g(int n){int i=0,s=0;while(i<n){s=s+f(i-4);i=i+1;}return s;}int main(){int n=get_int();printf("%d %d",g(n),f(-13));return 0;}','12\n',str(sum(-x if x<0 else 7 if x==0 else x*3 for x in range(-4,8)))+' 13',0),
    ('extreme-inline-loop-phi','int f(int n){int a=1,b=2,i=0;while(i<n){int t=a;a=b;b=t;i=i+1;}return a*10+b;}int main(){int n=get_int();int i=0,s=0;while(i<n){s=s+f(i);i=i+1;}put_int(s);return 0;}','19\n',str(sum(12 if i%2==0 else 21 for i in range(19))),0),
    ('extreme-memory-alias','int f(int a[],int b[]){a[0]=3;b[0]=7;return a[0]+b[0];}int main(){int a[2]={1,2};int x=f(a,a);printf("%d %d ",x,a[0]);a[0]=2;a[1]=9;printf("%d %d",a[0],a[1]);return 0;}','','14 7 2 9',0),
    ('extreme-string-store-order','int main(){char a[4]="ab";a[0]=\'x\';put_string(a);a[0]=\'y\';put_string(a);printf("%c",a[0]);return 0;}','','xbyby',0),
    ('extreme-gcm-loop','int main(){int n=get_int();int a=get_int();int i=0,s=0;while(i<n){int j=0;while(j<30){s=s+(a+3)*(a-2)+i+j;j=j+1;}i=i+1;}put_int(s);return 0;}','20\n7\n',str(sum((7+3)*(7-2)+i+j for i in range(20) for j in range(30))),0),
    ('extreme-gcm-zero-trip','int main(){int n=get_int();int a[1]={9};int i=0,s=0;while(i<n){s=s+a[i]/(n-n);i=i+1;}put_int(s);return 0;}','0\n','0',0),
    ('extreme-phi-call','int f(int n){if(n==0)return 1;int a=n*3;int x=f(n-1);return a+x;}int main(){int n=get_int();int i=0,s=0;while(i<30){int a=i+n;int x=f(n);s=s+a+x;i=i+1;}put_int(s);return 0;}','5\n',str(sum(i+5+46 for i in range(30))),0),
    ('extreme-char-phi','char f(char n){char a=(char)250,b=(char)9,i=(char)0;while(i<n){char t=a;a=a+b;b=t;i=i+(char)1;}return a;}int main(){int n=get_int();printf("%d",(int)f((char)n));return 0;}','30\n',str((lambda n: __import__('functools').reduce(lambda p,_:((p[0]+p[1])%256,p[0]),range(n),(250,9))[0])(30)),0),
    ('extreme-tail-recursion','int f(int n,int a,int b){if(n==0)return a*100+b;return f(n-1,b,a+b);}int main(){put_int(f(get_int(),0,1));return 0;}','12\n','14633',0),
    ('extreme-tail-void','void f(int n){if(n==0)return;put_int(n);f(n-1);}int main(){f(get_int());return 0;}','7\n','7654321',0),
    ('extreme-tail-multiple','int f(int n,int a){if(n==0)return a;if(n%2)return f(n-1,a+3);return f(n-1,a+1);}int main(){put_int(f(get_int(),5));return 0;}','20\n','45',0),
    ('extreme-global-scalar','int g=3;char h=(char)250;int main(){int n=get_int();int i=0;while(i<n){g=g+i;h=h+(char)3;i=i+1;}printf("%d %d",g,(int)h);return 0;}','40\n','783 114',0),
    ('extreme-array-parameter','int f(int a[],int n){int i=0,s=0;while(i<n){s=s+a[i];i=i+1;}return s;}int main(){int a[10]={1,2,3,4,5,6,7,8,9,10};printf("%d",f(a,get_int()));return 0;}','10\n','55',0),
    ('extreme-range-positive','int main(){int i=0,s=0;while(i<100){s=s+(i+3)*(i+1)/8+(i*8+5)%8;i=i+1;}char c=(char)get_int();printf("%d %d",s,((int)c+3)/8);return 0;}','250\n',str(sum((i+3)*(i+1)//8+(i*8+5)%8 for i in range(100)))+' 31',0),
    ('extreme-range-overflow','int main(){int i=0,s=0;while(i<50){int x=i*100000000+13;s=s+x/8+x%8;i=i+1;}put_int(s);return 0;}','',str(wrap(sum(div(wrap(i*100000000+13),8)+wrap(i*100000000+13)-div(wrap(i*100000000+13),8)*8 for i in range(50)))),0),
    ('extreme-range-negative','int main(){int i=-20,s=0;while(i<20){s=s+i/8+i%8;i=i+1;}put_int(s);return 0;}','',str(sum(div(i,8)+i-div(i,8)*8 for i in range(-20,20))),0),
    ('extreme-range-step','int main(){int i=3,s=0;while(i<100){s=s+(i+5)/8+(i+5)%8;i=i+7;}put_int(s);return 0;}','',str(sum((i+5)//8+(i+5)%8 for i in range(3,100,7))),0),
]
