# 语义分析测试集：每条用例给出源码与期望输出。
# 由 tools/run-tests.ps1 读取执行。
#
# 字段：
#   name     用例名
#   file     比对哪个输出文件：'symbol' -> symbol.txt，'error' -> error.txt
#   expected 期望内容（逐行精确比对）
#   source   源码
#
# 期望值的来源：
#   * 标 "官方" 的用例直接取自作业文档的样例输出
#   * 其余用例的行号均经过实测核对，并逐一对照文法文档的错误行号规则
#     （例如 g 报函数末尾 '}' 的行号、m 报 break/continue 关键字的行号）

@{
  cases = @(

    # ==================== 正确程序：符号表输出 ====================

    @{
      name = '官方正确样例'
      file = 'symbol'
      expected = @(
        '1 year ConstInt','1 month ConstInt','1 day Int','1 getDay IntFunc',
        '1 fun IntFunc','2 day StaticInt','5 length Int'
      )
      source = @'
const int year = 2026, month = 9;
int day;
int getDay(){
    static int day;
    day = get_int();
    return day;
}
int fun(){
    return 12345;
}
int main(){
    day = getDay();
    printf("Tody is %d-%d-%d\n", year, month, day);
    {
        int length = fun();
        if(length > 4){
            printf("%d\n", length);
        }
    }
    return 0;
}
'@
    }

    @{
      name = '13 种类型名全覆盖'
      file = 'symbol'
      expected = @(
        '1 ci ConstInt','1 cc ConstChar','1 ia IntArray','1 ca CharArray','1 cai ConstIntArray',
        '1 cac ConstCharArray','1 gv Int','1 gc Char','1 f1 IntFunc','1 f2 CharFunc','1 f3 VoidFunc',
        '2 pv Int','2 pc Char','2 pa IntArray','2 pca CharArray','2 sv StaticInt','2 sc StaticChar',
        '2 sa StaticIntArray','2 sca StaticCharArray'
      )
      source = @'
const int ci = 1;
const char cc = 'a';
int ia[3];
char ca[3];
const int cai[2] = {1, 2};
const char cac[2] = {'a', 'b'};
int gv;
char gc;

int f1(int pv, char pc, int pa[], char pca[]) {
    static int sv = 1;
    static char sc = 'a';
    static int sa[2];
    static char sca[2];
    return pv + pa[0];
}

char f2() { return 'a'; }
void f3() { return; }

int main() {
    return 0;
}
'@
    }

    @{
      name = '嵌套作用域编号'
      file = 'symbol'
      expected = @(
        '1 g Int','1 f IntFunc','2 p Int','2 a Int','3 b Int','4 c Int','5 m1 Int','6 m2 Int'
      )
      source = @'
int g;
int f(int p) {
    int a = 1;
    {
        int b = 2;
        {
            int c = 3;
        }
    }
    return a;
}
int main() {
    int m1 = 1;
    {
        int m2 = 2;
    }
    return 0;
}
'@
    }

    @{
      name = 'char 与常量的混合运算不报错'
      file = 'symbol'
      expected = @(
        '1 C ConstChar','1 f IntFunc','2 c Char','2 r Int'
      )
      source = @'
const char C = 'a';
int f(char c) {
    int r = 0;
    if (c == C) {
        r = 1;
    }
    while (c != 'z' && r < 10 || c > 'A') {
        r = r + 1;
    }
    return r + (int) c;
}
int main() { return 0; }
'@
    }

    @{
      name = 'const 数组与字符串初始化'
      file = 'symbol'
      expected = @(
        '1 A ConstIntArray','1 S ConstCharArray','1 B IntArray',
        '1 f IntFunc','2 x Int'
      )
      source = @'
const int A[3] = {1, 2, 3};
const char S[6] = "hello";
int B[3] = {0, 0, 0};
int f() {
    int x = A[0] + B[1];
    if (S[0] == 'h') {
        x = x + 1;
    }
    return x;
}
int main() { return 0; }
'@
    }

    @{
      name = 'switch 与 while 的作用域'
      file = 'symbol'
      expected = @(
        '1 f IntFunc','2 c Char','2 i Int','3 t Int'
      )
      source = @'
int f(char c) {
    int i = 0;
    switch (c) {
        case 'a':
            i = 1;
            break;
        case 'b':
            i = 2;
            break;
        default:
            i = 3;
    }
    while (i < 10) {
        int t = i;
        i = i + t;
        if (i == 5) {
            continue;
        }
        if (i > 20) {
            break;
        }
    }
    return i;
}
int main() { return 0; }
'@
    }

    @{
      name = 'int 与 char 显式转换'
      file = 'symbol'
      expected = @(
        '1 f IntFunc','2 ci Char','2 i Int','2 r Char'
      )
      source = @'
int f() {
    char ci = 'a';
    int i = (int) ci;
    i = i + 1;
    char r = (char) i;
    if (r == 'b') {
        return 1;
    }
    return 0;
}
int main() { return 0; }
'@
    }

    # ==================== 错误程序 ====================

    @{
      name = '官方错误样例'
      file = 'error'
      expected = @('4 h','5 i')
      source = @'
const int const1 = 1, const2 = -100;
int change1;
int gets1(int var1,int var2){
  const1 = 999;
  change1 = var1 + var2          return (change1);
}
int main(){
  change1 = 10;
  printf("Hello World$");
  return 0;
}
'@
    }

    @{
      name = 'b 同作用域重定义变量'
      file = 'error'
      expected = @('3 b')
      source = @'
int main() {
    int a = 1;
    int a = 2;
    return 0;
}
'@
    }

    @{
      name = 'b 形参与局部变量重名'
      file = 'error'
      expected = @('2 b')
      source = @'
int f(int a) {
    int a = 1;
    return a;
}
int main() { return 0; }
'@
    }

    @{
      name = 'b 重定义函数'
      file = 'error'
      expected = @('3 b')
      source = @'
int f() { return 1; }
int g() { return 2; }
int f() { return 3; }
int main() { return 0; }
'@
    }

    @{
      name = 'b 全局变量与函数同名'
      file = 'error'
      expected = @('1 b')
      source = @'
int f() { return 1; }
int f;
int main() { return 0; }
'@
    }

    @{
      name = 'c 未定义变量'
      file = 'error'
      expected = @('2 c')
      source = @'
int main() {
    int a = b;
    return 0;
}
'@
    }

    @{
      name = 'c 未定义函数'
      file = 'error'
      expected = @('2 c')
      source = @'
int main() {
    int a = foo();
    return 0;
}
'@
    }

    @{
      name = 'd 参数个数偏少'
      file = 'error'
      expected = @('3 d')
      source = @'
int f(int a, int b) { return a + b; }
int main() {
    int x = f(1);
    return 0;
}
'@
    }

    @{
      name = 'd 参数个数偏多'
      file = 'error'
      expected = @('3 d')
      source = @'
int f(int a) { return a; }
int main() {
    int x = f(1, 2);
    return 0;
}
'@
    }

    @{
      name = 'e 赋值 int 给 char'
      file = 'error'
      expected = @('2 e')
      source = @'
int main() {
    char c = 1;
    return 0;
}
'@
    }

    @{
      name = 'e 赋值 char 给 int'
      file = 'error'
      expected = @('2 e')
      source = @'
int main() {
    int i = 'a';
    return 0;
}
'@
    }

    @{
      name = 'e 实参 int 传给 char 形参'
      file = 'error'
      expected = @('3 e')
      source = @'
void f(char c) { return; }
int main() {
    f(1);
    return 0;
}
'@
    }

    @{
      name = 'e 实参 char 传给 int 形参'
      file = 'error'
      expected = @('3 e')
      source = @'
void f(int i) { return; }
int main() {
    f('a');
    return 0;
}
'@
    }

    @{
      name = 'e 数组传给标量形参'
      file = 'error'
      expected = @('4 e')
      source = @'
void f(int i) { return; }
int main() {
    int a[3] = {1,2,3};
    f(a);
    return 0;
}
'@
    }

    @{
      name = 'e 标量传给数组形参'
      file = 'error'
      expected = @('4 e')
      source = @'
void f(int a[]) { return; }
int main() {
    int x = 1;
    f(x);
    return 0;
}
'@
    }

    @{
      name = 'e 返回类型不匹配'
      file = 'error'
      expected = @('3 e')
      source = @'
int f() {
    char c = 'a';
    return c;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e case 标签类型与 switch 表达式不符'
      file = 'error'
      expected = @('4 e')
      source = @'
int main() {
    int i = 1;
    switch (i) {
        case 'a':
            i = 2;
    }
    return 0;
}
'@
    }

    @{
      name = 'e 对非数组做下标访问'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int a = 1;
    a[0] = 2;
    return 0;
}
'@
    }

    @{
      name = 'e 数组下标不是 int'
      file = 'error'
      expected = @('4 e')
      source = @'
int main() {
    int a[3] = {1,2,3};
    char c = 'a';
    a[c] = 1;
    return 0;
}
'@
    }

    @{
      name = 'e 变量当函数调用'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int v = 1;
    v();
    return 0;
}
'@
    }

    @{
      name = 'e int/char 变量当函数调用（多个调用点、带实参）'
      file = 'error'
      expected = @('5 e', '6 e', '8 e', '9 e')
      source = @'
int main() {
    int val_i = 10;
    char val_c = 'z';

    val_i();
    val_i(1, 2);

    val_c();
    val_c('a');

    return 0;
}
'@
    }

    @{
      name = 'f void 函数出现带值 return'
      file = 'error'
      expected = @('2 f')
      source = @'
void f() {
    return 1;
}
int main() { return 0; }
'@
    }

    @{
      name = 'j/d/i/k 形参表崩溃点与后续恢复（用户样例）'
      file = 'error'
      expected = @('1 j', '6 d', '10 i', '11 k')
      source = @'
void f0( {

}

void f1(int x, int y, int z) {
    f0(2024);
}

int main() {
    f1(1, 2, 3)
    int a[2;

    return 0;
}
'@
    }

    @{
      name = 'g 有返回值函数缺少 return'
      file = 'error'
      expected = @('3 g')
      source = @'
int f() {
    int a = 1;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e/f/g 裸 return 与末尾形态混合（用户样例）'
      file = 'error'
      expected = @('3 e', '8 g', '17 f', '20 f', '28 g', '31 e')
      source = @'
int fun1() {
    {
        return;  // 3 e
        return 3;
    }

    //8 g
}

void fun2() {
    {
        return;
    }

    {
        // 17 f
        return 3;
    }
    // 20 f
    return 5 + 5;

    return;

    5 + 5;
}

// 28 g
int fun3() {}

int fun4() {
    return;  // 31 e
    return 1;
}

int main() {
    return 0;
}
'@
    }

    @{
      name = 'g main 缺少 return'
      file = 'error'
      expected = @('3 g')
      source = @'
int main() {
    int a = 1;
}
'@
    }

    @{
      name = 'h 修改常量'
      file = 'error'
      expected = @('3 h')
      source = @'
const int a = 1;
int main() {
    a = 2;
    return 0;
}
'@
    }

    @{
      name = 'h 修改常量数组元素'
      file = 'error'
      expected = @('3 h')
      source = @'
const int a[3] = {1,2,3};
int main() {
    a[0] = 2;
    return 0;
}
'@
    }

    @{
      name = 'l printf 参数个数偏少'
      file = 'error'
      expected = @('2 l')
      source = @'
int main() {
    printf("%d %d", 1);
    return 0;
}
'@
    }

    @{
      name = 'l printf 参数个数偏多'
      file = 'error'
      expected = @('2 l')
      source = @'
int main() {
    printf("%d", 1, 2);
    return 0;
}
'@
    }

    @{
      name = 'm 循环外 break'
      file = 'error'
      expected = @('2 m')
      source = @'
int main() {
    break;
    return 0;
}
'@
    }

    @{
      name = 'm 循环外 continue'
      file = 'error'
      expected = @('2 m')
      source = @'
int main() {
    continue;
    return 0;
}
'@
    }

    @{
      name = 'm switch 内 continue'
      file = 'error'
      expected = @('5 m')
      source = @'
int main() {
    int i = 1;
    switch (i) {
        case 1:
            continue;
    }
    return 0;
}
'@
    }

    @{
      name = 'n 重复 case 值'
      file = 'error'
      expected = @('7 n')
      source = @'
int main() {
    int i = 1;
    switch (i) {
        case 1:
            i = 2;
            break;
        case 1:
            i = 3;
    }
    return 0;
}
'@
    }

    @{
      name = 'n 多个 default'
      file = 'error'
      expected = @('7 n')
      source = @'
int main() {
    int i = 1;
    switch (i) {
        default:
            i = 2;
            break;
        default:
            i = 3;
    }
    return 0;
}
'@
    }

    @{
      name = 'i 缺少分号'
      file = 'error'
      expected = @('2 i')
      source = @'
int main() {
    int a = 1
    return 0;
}
'@
    }

    @{
      name = 'j 缺少右小括号'
      file = 'error'
      expected = @('1 j')
      source = @'
int f(int a {
    return a;
}
int main() { return 0; }
'@
    }

    @{
      name = 'k 缺少右中括号'
      file = 'error'
      expected = @('2 k')
      source = @'
int main() {
    int a[3 = {1,2,3};
    return 0;
}
'@
    }

    # ==================== printf 格式字符与类型（e / l） ====================

    @{
      name = 'e printf %d 配 char'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    char c = 'a';
    printf("%d", c);
    return 0;
}
'@
    }

    @{
      name = 'e printf %c 配 int'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int i = 1;
    printf("%c", i);
    return 0;
}
'@
    }

    @{
      name = 'e printf %s 配 int'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int i = 1;
    printf("%s", i);
    return 0;
}
'@
    }

    @{
      name = '正确 printf 三种格式符搭配'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int','2 c Char','2 s CharArray')
      source = @'
int f() {
    int i = 1;
    char c = 'a';
    char s[10] = "hi";
    printf("%d %c %s\n", i, c, s);
    printf("100%%\n");
    return 0;
}
int main() { return 0; }
'@
    }

    @{
      name = 'l printf 转义百分号不消耗实参'
      file = 'error'
      expected = @('3 l')
      source = @'
int main() {
    int i = 1;
    printf("100%% %d %d", i);
    return 0;
}
'@
    }

    # ==================== 混合运算与类型不匹配（e） ====================

    @{
      name = 'e int 与 char 算术混合'
      file = 'error'
      expected = @('4 e')
      source = @'
int main() {
    int i = 1;
    char c = 'a';
    i = i + c;
    return 0;
}
'@
    }

    @{
      name = 'e int 与 char 关系比较'
      file = 'error'
      expected = @('4 e')
      source = @'
int main() {
    int i = 1;
    char c = 'a';
    if (i < c) {
        i = 2;
    }
    return 0;
}
'@
    }

    @{
      name = '正确 int 与 char 各自运算'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int','2 c Char')
      source = @'
int f() {
    int i = 1;
    char c = 'a';
    i = i + 2 * 3 - 4 / 2 % 3;
    c = c + 'b';
    if (i > 0 && c != 'z' || i == 0) {
        i = 1;
    }
    return i;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e 数组参与算术运算'
      file = 'error'
      expected = @('4 e')
      source = @'
int main() {
    int a[3] = {1,2,3};
    int b[3] = {1,2,3};
    int c = a + b;
    return 0;
}
'@
    }

    @{
      name = 'e 函数返回 char 值给 int 函数'
      file = 'error'
      expected = @('4 e')
      source = @'
int f() {
    int i = 1;
    if (i > 0) {
        return 'a';
    }
    return i;
}
int main() { return 0; }
'@
    }

    @{
      name = 'c 函数名当变量使用'
      file = 'error'
      expected = @('3 c')
      source = @'
int f() { return 1; }
int main() {
    int x = f;
    return 0;
}
'@
    }

    # ==================== 非法转换与数组误用 ====================
    #
    # 注意：文法里只有 LVal -> Ident ['[' Exp ']']，即只有"标识符"能带下标。
    # 因此 f()[0]、1[0] 这类写法在文法层就不成立（会走语法错误恢复，报 i），
    # 不属于语义阶段该管的 e。文档第 16 页也保证"会导致语法树不能正确建立的错误都不会出现"，
    # 所以这里不构造这类用例。

    @{
      name = 'e 对 char 变量做下标'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    char c = 'a';
    c[0] = 1;
    return 0;
}
'@
    }

    # ==================== 语句与作用域（m） ====================

    @{
      name = '正确 switch 内 break'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int')
      source = @'
int f() {
    int i = 0;
    switch (i) {
        case 0:
            i = 1;
            break;
        default:
            i = 2;
    }
    return i;
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 while 内 break 与 continue'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int')
      source = @'
int f() {
    int i = 0;
    while (i < 10) {
        i = i + 1;
        if (i == 3) {
            continue;
        }
        if (i == 7) {
            break;
        }
    }
    return i;
}
int main() { return 0; }
'@
    }

    @{
      name = 'm 嵌套块内的 break 在循环外'
      file = 'error'
      expected = @('3 m')
      source = @'
int main() {
    {
        break;
    }
    return 0;
}
'@
    }

    @{
      name = '正确 switch 内的 while 里 continue'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int')
      source = @'
int f() {
    int i = 0;
    switch (i) {
        case 0:
            while (i < 5) {
                i = i + 1;
                continue;
            }
    }
    return i;
}
int main() { return 0; }
'@
    }

    # ==================== return 完备性（f / g） ====================

    # 文档第 13 页：「只需要考虑函数末尾是否存在 return 语句，无需考虑控制流」。
    # 因此末尾是 if/else（即使两个分支都 return）仍要报 g；只有末尾直接是带值 return 才放行。

    @{
      name = 'g 末尾 if/else 两分支都 return'
      file = 'error'
      expected = @('8 g')
      source = @'
int f() {
    int i = 1;
    if (i > 0) {
        return 1;
    } else {
        return 0;
    }
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 末尾直接是 return'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int')
      source = @'
int f() {
    int i = 1;
    if (i > 0) {
        return 1;
    }
    return 0;
}
int main() { return 0; }
'@
    }

    @{
      name = 'g 末尾 while 内含 return'
      file = 'error'
      expected = @('5 g')
      source = @'
int f() {
    while (1) {
        return 1;
    }
}
int main() { return 0; }
'@
    }

    @{
      name = 'g 末尾 switch 内含 return'
      file = 'error'
      expected = @('7 g')
      source = @'
int f() {
    int i = 0;
    switch (i) {
        case 1:
            return 1;
    }
}
int main() { return 0; }
'@
    }

    @{
      name = 'e 条件用数组或 void'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int a[3] = {1,2,3};
    if (a) { }
    return 0;
}
'@
    }

    @{
      name = 'e if 条件用 void 函数调用'
      file = 'error'
      expected = @('3 e')
      source = @'
void v() { return; }
int main() {
    if (v()) { }
    return 0;
}
'@
    }

    @{
      name = 'e while 条件用数组'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int a[3];
    while (a) { }
    return 0;
}
'@
    }

    @{
      name = '正确 条件用 int 或 char'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int','2 c Char')
      source = @'
int f() {
    int i = 1;
    char c = 'a';
    if (i) { }
    while (c) { }
    return 0;
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 void 函数末尾 return;'
      file = 'symbol'
      expected = @('1 f VoidFunc')
      source = @'
void f() {
    return;
}
int main() { return 0; }
'@
    }

    @{
      name = 'g 非 void 函数末尾是赋值语句'
      file = 'error'
      expected = @('4 g')
      source = @'
int f() {
    int i = 1;
    i = i + 1;
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 char 函数返回 char'
      file = 'symbol'
      expected = @('1 f CharFunc','2 c Char')
      source = @'
char f() {
    char c = 'a';
    return c;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e char 函数返回 int'
      file = 'error'
      expected = @('3 e')
      source = @'
char f() {
    int i = 1;
    return i;
}
int main() { return 0; }
'@
    }

    # ==================== 字符串初始化 char[] ====================

    @{
      name = '正确 字符串初始化 char[] 与 char'
      file = 'symbol'
      expected = @('1 A CharArray','1 C Char','1 f IntFunc','2 L CharArray')
      source = @'
char A[10] = "hello";
char C = 'x';
int f() {
    char L[5] = "abcd";
    if (L[0] == 'a' && A[0] == 'h') {
        return 1;
    }
    return 0;
}
int main() { return 0; }
'@
    }

    # 注意：InitVal 的文法候选项只有 Exp | '{' ... '}'，不含 StringConst
    #（只有 ConstInitVal 才有 StringConst）。所以 "int a[10] = \"hello\";" 在文法层
    # 就不成立，会走语法错误恢复，不是语义阶段的 e。

    # ==================== static 行为 ====================

    @{
      name = '正确 static 变量初始化只求值一次'
      file = 'symbol'
      expected = @('1 f IntFunc','2 s StaticInt','2 t StaticInt')
      source = @'
int f() {
    static int s = 0;
    static int t;
    s = s + 1;
    t = t + 1;
    return s + t;
}
int main() { return 0; }
'@
    }

    @{
      name = 'b static 变量与普通变量重名'
      file = 'error'
      expected = @('3 b')
      source = @'
int f() {
    static int s = 0;
    int s = 1;
    return s;
}
int main() { return 0; }
'@
    }

    # ==================== 初值与常量表达式 ====================

    @{
      name = '正确 局部变量初值可用已声明变量'
      file = 'symbol'
      expected = @('1 f IntFunc','2 a Int','2 b Int')
      source = @'
int f() {
    int a = 1;
    int b = a + 1;
    return b;
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 常量初始化与常量数组长度'
      file = 'symbol'
      expected = @('1 N ConstInt','1 A ConstIntArray','1 B IntArray','1 f IntFunc','2 c Char','2 x Int')
      source = @'
const int N = 3;
const int A[N] = {1, 2, 3};
int B[N];
int f() {
    char c = (char) N;
    int x = A[0] + B[0];
    if (x > N) {
        x = 0;
    }
    return x;
}
int main() { return 0; }
'@
    }

    @{
      name = 'c 常量表达式中引用变量'
      file = 'error'
      expected = @('3 c')
      source = @'
int main() {
    int n = 3;
    int a[n];
    return 0;
}
'@
    }

    @{
      name = 'c 常量表达式中调用函数'
      file = 'error'
      expected = @('3 c')
      source = @'
int g() { return 3; }
int main() {
    int a[g()];
    return 0;
}
'@
    }

    # ==================== 数组传参与函数 ====================

    @{
      name = '正确 数组实参传给数组形参'
      file = 'symbol'
      expected = @('1 f VoidFunc','2 a IntArray','3 t IntArray')
      source = @'
void f(int a[]) {
    a[0] = 1;
    return;
}
int main() {
    int t[5] = {1,2,3,4,5};
    f(t);
    return 0;
}
'@
    }

    @{
      name = 'e int[] 实参传给 char[] 形参'
      file = 'error'
      expected = @('4 e')
      source = @'
void f(char a[]) { return; }
int main() {
    int t[3] = {1,2,3};
    f(t);
    return 0;
}
'@
    }

    # 关于"先定义后使用"：本实现（与 2024 参考实现一致）先注册全部全局声明、
    # 再逐个分析函数体，因此函数体内可以引用"在其后定义"的全局声明（变量与函数均可）。
    # 文档第 9 页说作用域"从该声明处开始到文件结尾"，但同时又保证
    # "所有会导致语法树不能正确建立的错误都不会出现"，且验收样例里也没有前向引用，
    # 所以这里不做断言。若后续确认必须报错，再补前向引用检查。

    # ==================== 一元运算符的限制 ====================

    @{
      name = 'e 逻辑非出现在普通表达式'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int a = 1;
    a = !a;
    return 0;
}
'@
    }

    @{
      name = '正确 逻辑非出现在条件中'
      file = 'symbol'
      expected = @('1 f IntFunc','2 a Int')
      source = @'
int f() {
    int a = 1;
    if (!a) {
        a = 0;
    }
    while (!a) {
        a = 1;
    }
    return a;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e 相邻两个相同的一元运算符'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int i = 1;
    int a = +++i;
    return 0;
}
'@
    }

    @{
      name = '正确 相邻不同的一元运算符'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int','2 a Int')
      source = @'
int f() {
    int i = 1;
    int a = +-+i;
    return a;
}
int main() { return 0; }
'@
    }

    @{
      name = 'e 相邻 + + 但后续运算符不同'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int i = 1;
    int a = ++-i;
    return 0;
}
'@
    }

    @{
      name = 'e 三个相同运算符只报一次'
      file = 'error'
      expected = @('3 e')
      source = @'
int main() {
    int i = 1;
    int a = +++i;
    return 0;
}
'@
    }

    @{
      name = '正确 带空格的相邻不同运算符'
      file = 'symbol'
      expected = @('1 f IntFunc','2 i Int','2 a Int')
      source = @'
int f() {
    int i = 1;
    int a = + - + i;
    return a;
}
int main() { return 0; }
'@
    }

    # ==================== 整数字面量范围 ====================

    @{
      name = '正确 INT_MIN 写法'
      file = 'symbol'
      expected = @('1 f IntFunc','2 a Int')
      source = @'
int f() {
    int a = -2147483648;
    return a;
}
int main() { return 0; }
'@
    }

    @{
      name = '正确 最大整数字面量'
      file = 'symbol'
      expected = @('1 f IntFunc','2 a Int')
      source = @'
int f() {
    int a = 2147483647;
    return a;
}
int main() { return 0; }
'@
    }
  )
}
