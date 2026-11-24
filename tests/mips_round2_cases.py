"""Runtime pressure, calls, comparison chains and stack slot lifetimes."""
expressions = [f'a+{n}' for n in range(1, 15)]
format_string = ' '.join(['%d'] * len(expressions))
pressure = 'int main(){int a=get_int();printf("'+format_string+'",'+','.join(expressions)+');return 0;}'
expressions = [f'a+{n}' for n in range(1, 9)] + ['tick(a)'] + [f'a+{n}' for n in range(10, 16)]
format_string = ' '.join(['%d'] * len(expressions))
call_pressure = 'int tick(int x){put_char(\'x\');return x+10;}int main(){int a=get_int();printf("'+format_string+'",'+','.join(expressions)+');return 0;}'

values = [-2147483648, -7, -1, 0, 1, 7, 2147483647]
tests = ['n==0', 'n!=0', 'n<0', 'n>0', 'n<=0', 'n>=0', '!n', '!(!n)', '!(!(!n))', "c>'a'", "c<=(char)255"]
body = ''.join('if('+test+')put_int(1);else put_int(0);' for test in tests)+'put_char(\'\\n\');'
branch_source = 'void f(int n,char c){'+body+'}int main(){'+''.join(f'f({n},(char)255);' for n in values)+'return 0;}'
branch_expected = ''.join(''.join(str(int(v)) for v in
    [n==0,n!=0,n<0,n>0,n<=0,n>=0,not n,bool(n),not n,True,True])+'\n' for n in values)

ROUND2_CASES = [
    ('round2-register-pressure', pressure, '40\n', ' '.join(str(40+n) for n in range(1,15)), 0),
    ('round2-pressure-across-call', call_pressure, '5\n', 'x'+' '.join(str(x) for x in [*range(6,14),15,*range(15,21)]), 0),
    ('round2-dynamic-branches', branch_source, '', branch_expected, 0),
    ('round2-slot-loop', 'int main(){int i=0,s=0;while(i<300){int a=(i+1)*(i+2);int b=a/2;char c=(char)(i+255);s=s+b+(int)c;i=i+1;}put_int(s);return 0;}', '', str(sum((i+1)*(i+2)//2+(i+255)%256 for i in range(300))), 0),
    ('round2-many-parameters', 'int f(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j){return a+b*2+c*3+d*4+e*5+f*6+g*7+h*8+i*9+j*10;}int main(){int n=get_int();printf("%d",f(n+1,n+2,n+3,n+4,n+5,n+6,n+7,n+8,n+9,n+10));return 0;}', '10\n', str(sum((10+n)*n for n in range(1,11))), 0),
    ('round2-char-pressure', 'char f(char a,char b,char c,char d,char e,char f,char g,char h){return a+b+c+d+e+f+g+h;}int main(){char a=(char)get_int();printf("%d",(int)f(a+(char)1,a+(char)2,a+(char)3,a+(char)4,a+(char)5,a+(char)6,a+(char)7,a+(char)8));return 0;}', '250\n', str(sum((250+n)%256 for n in range(1,9))%256), 0),
]
