"""MIPS ABI, addressing and input boundary fixtures, with independent expected outputs."""
EXTRA_CASES = [
    ('many-args', 'int f(int a,int b,int c,int d,int e,int f,int g){return a+10*b+100*c+1000*d+e*f+g;}int main(){put_int(f(1,2,3,4,5,6,7));return 0;}', '', '4358', 0),
    ('mixed-args', "char f(int a,char b,int c,char d,char s[],int x[],char e){s[1]=e;x[1]=a+c;return b+d+e;}int main(){char s[3]=\"ab\";int x[2]={};printf(\"%d %s %d\",(int)f(1,(char)200,2,(char)100,s,x,'Z'),s,x[1]);return 0;}", '', '134 aZ 3', 0),
    ('recursive-stack-args', 'int f(int n,int a,int b,int c,int d,int e){if(n==0)return a+b+c+d+e;return f(n-1,a+1,b+2,c+3,d+4,e+5)+n;}int main(){put_int(f(5,1,2,3,4,5));return 0;}', '', '105', 0),
    ('nested-calls', 'int inc(int n){return n+1;}int sum(int a,int b,int c,int d,int e){return a+b+c+d+e;}int main(){put_int(sum(inc(1),sum(1,2,3,4,5),inc(inc(2)),inc(4),inc(5)));return 0;}', '', '32', 0),
    ('large-stack', 'int main(){char c[3]="ab";int a[10000];char b[33001];a[0]=-123456789;a[9999]=2147483647;b[33000]=(char)255;printf("%s %d %d %d",c,a[0],a[9999],(int)b[33000]);return 0;}', '', 'ab -123456789 2147483647 255', 0),
    ('global-alignment', 'char a=\'A\';int b=123456789;char s[3]="xy";int v[2]={-2147483648,2147483647};int main(){printf("%c %d %s %d %d",a,b,s,v[0],v[1]);return 0;}', '', 'A 123456789 xy -2147483648 2147483647', 0),
    ('global-last-element', 'int a[10000];char s[33001];int main(){a[9999]=987654321;s[33000]=\'Z\';printf("%d %c %d",a[9999],s[33000],a[0]);return 0;}', '', '987654321 Z 0', 0),
    ('string-consecutive', 'int main(){char a[4],b[9];get_string(a,4);get_string(b,9);printf("[%s][%s]",a,b);return 0;}', 'abcdef\nnext\n', '[abc][next\n]', 0),
    ('string-capacity-consumes', 'int main(){char a[4]="xyz",b[4]="xyz",c[8];get_string(a,0);get_string(b,1);get_string(c,8);printf("%s %d %s",a,(int)b[0],c);return 0;}', 'first\nsecond\nthird\n', 'xyz 0 third\n', 0),
    ('string-empty-eof', 'int main(){char a[8],b[8];get_string(a,8);get_string(b,8);printf("[%s][%s]",a,b);return 0;}', '\nend', '[\n][end\n]', 0),
    ('int-overflow', 'int main(){int a=2147483647,b=-2147483648;printf("%d %d %d",a+1,b-1,a*2);return 0;}', '', '-2147483648 2147483647 -2', 0),
    ('high-byte-data', 'char c=(char)128;char a[3]={(char)127,(char)128,(char)255};int main(){static char d=(char)255;printf("%d %d %d %d %d",(int)c,(int)d,(int)a[0],(int)a[1],(int)a[2]);return 0;}', '', '128 255 127 128 255', 0),
    ('array-sort', 'void sort(int a[],int n){int i=0;while(i<n){int j=i+1;while(j<n){if(a[j]<a[i]){int t=a[i];a[i]=a[j];a[j]=t;}j=j+1;}i=i+1;}}int main(){int a[8]={9,-2,0,4,-2,8,1,3};sort(a,8);int i=0;while(i<8){printf("%d ",a[i]);i=i+1;}return 0;}', '', '-2 -2 0 1 3 4 8 9 ', 0),
]

# The then-body exceeds the MIPS conditional branch's signed 16-bit displacement.
_body = 'x=x+1;' * 2600
EXTRA_CASES += [
    ('far-branch-false', 'int main(){int x=0;if(get_int()){' + _body + '}put_int(x);return 0;}', '0\n', '0', 0),
    ('far-branch-true', 'int main(){int x=0;if(get_int()){' + _body + '}put_int(x);return 0;}', '1\n', '2600', 0),
]
