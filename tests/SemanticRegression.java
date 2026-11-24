import frontend.Lexer;
import frontend.Parser;
import frontend.ParserWatcher;
import frontend.Traverser;
import utils.Controller;

/** Run with JDK 17+: javac -encoding UTF-8 -d build/semantic-tests <src files> tests/SemanticRegression.java */
public class SemanticRegression {
    private static int passed;
    private static int failed;

    private static void check(String name, String source, String expected) throws Exception {
        Controller.errors.clear();
        Controller.symbols.clear();
        try {
            var tokens = new Lexer(source, name).lex().emit();
            var ast = new Parser(tokens, new ParserWatcher()).parse().emit();
            new Traverser(ast).spawn();
            String actual = Controller.errors.toString();
            if (!actual.equals(expected)) {
                failed++;
                System.out.println("FAIL " + name + ": expected [" + expected.replace('\n', ',')
                        + "], got [" + actual.replace('\n', ',') + "]");
            } else {
                passed++;
            }
        } catch (RuntimeException ex) {
            failed++;
            System.out.println("FAIL " + name + ": " + ex);
        }
    }

    public static void main(String[] args) throws Exception {
        check("return must be last", "int f(){\nreturn 1;\n;\n}\nint main(){return 0;}", "4 g");
        check("nested return is not final", "int f(){\nif(1) return 1;\n}\nint main(){return 0;}", "3 g");
        check("bare final return", "int f(){\nreturn;\n}\nint main(){return 0;}", "");
        check("void returned as int", "void f(){}\nint g(){\nreturn f();\n}\nint main(){return 0;}", "3 e");
        check("array returned as int", "int f(){int a[2];\nreturn a;\n}\nint main(){return 0;}", "2 e");
        check("call variable", "int main(){int x;\nx();\nreturn 0;}", "2 e");
        check("function used as variable", "int f(){return 1;}\nint main(){\nint x=f;\nreturn 0;}", "3 e");
        check("variable shadows function", "void f(){}\nint main(){int f;\nf();\nreturn 0;}", "3 e");
        check("undefined call", "int main(){\nmissing();\nreturn 0;}", "2 c");
        check("undefined variable", "int main(){\nmissing=1;\nreturn 0;}", "2 c");
        check("char array length", "int main(){\nint a['a'];\nreturn 0;}", "2 e");
        check("constant array element mismatch", "const int a[1]={'a'};\nint main(){return 0;}", "1 e");
        check("array initializer void", "void f(){}\nint main(){\nint a[1]={f()};\nreturn 0;}", "3 e");
        check("array initializer array", "int main(){int a[1];\nint b[1]={a};\nreturn 0;}", "2 e");
        check("short string is truncated", "char s[2]=\"abc\";\nint main(){return 0;}", "");
        check("exact string needs no terminator", "const char s[2]=\"ab\";\nint main(){return 0;}", "");
        check("string initializing int array", "int a[8]=\"ab\";\nint main(){return 0;}", "1 e");
        check("void switch selector", "void f(){}\nint main(){\nswitch(f()){default:break;}\nreturn 0;}", "3 e");
        check("array switch selector", "int main(){int a[1];\nswitch(a){case 1:break;}\nreturn 0;}", "2 e");
        check("not produces int", "int main(){\nif(!'a' + 1);\nreturn 0;}", "");
        check("not cannot mix with char", "int main(){\nif(!'a' + 'b');\nreturn 0;}", "2 e");
        check("duplicate unary in longer chain", "int main(){int x=1;\nx=++-+x;\nreturn 0;}", "2 e");
        check("different unary operators", "int main(){int x=1;\nx=+-+x;\nreturn 0;}", "");
        check("normal scopes", "int f(int x){ {char x='a';} return x;}\nint main(){int f=1;return 0;}", "");
        check("parameter and body same scope", "int f(int x){\nint x;\nreturn 1;}\nint main(){return 0;}", "2 b");
        check("continue in switch", "int main(){\nswitch(1){case 1:continue;}\nreturn 0;}", "2 m");
        check("continue switch in loop", "int main(){while(1){switch(1){case 1:continue;}}return 0;}", "");
        check("forward call", "int f(){\nreturn g();\n}\nint g(){return 1;}\nint main(){return 0;}", "2 c");
        check("recursive call", "int f(int a){return f(a);}\nint main(){return 0;}", "");
        check("runtime get_string returns void", "int main(){char s[8];\nint x=get_string(s,8);\nreturn 0;}", "2 e");
        check("invalid const array does not abort later errors", "const int a[1]={missing};\nint main(){\nunknown=1;\nreturn 0;}", "1 c\n3 c");
        check("constant index rejects variable", "int i=0;\nconst int a[1]={1};\nconst int x=a[i];\nint main(){return 0;}", "3 c");
        check("function and variable collision", "int f;\nint f(){return 1;}\nint main(){return 0;}", "2 b");
        check("duplicate function body still checked", "int f(){return 1;}\nint f(){\nmissing=1;\nreturn 1;}\nint main(){return 0;}", "2 b\n3 c");
        check("valid printf types", "int main(){char s[2]=\"ab\";printf(\"%d%c%s\",1,'a',s);return 0;}", "");
        check("whole array assignment", "int main(){int a[2],b[2];\na=b;\nreturn 0;}", "2 e");
        check("array condition location", "int main(){int a[2];\nif(a);\nreturn 0;}", "2 e");
        check("void condition location", "void f(){}\nint main(){\nif(f());\nreturn 0;}", "3 e");
        check("char condition", "int main(){if('a');return 0;}", "");
        check("duplicate formal preserves arity", "int f(int a,\nint a){return 1;}\nint main(){f(1,2);return 0;}", "2 b");
        check("const array initializer scalar type", "const char a[1]={1};\nint main(){return 0;}", "1 e");
        check("array argument same element different lengths", "void f(char a[]){}\nint main(){char a[2];f(a);return 0;}", "");
        check("array argument wrong element type", "void f(char a[]){}\nint main(){int a[2];\nf(a);\nreturn 0;}", "3 e");
        check("printf argument count", "int main(){\nprintf(\"%d%c\",1);\nreturn 0;}", "2 l");
        check("printf argument type", "int main(){\nprintf(\"%d\",'a');\nreturn 0;}", "2 e");
        check("switch case type", "int main(){\nswitch('a'){case 1:break;}\nreturn 0;}", "2 e");
        check("switch duplicate case", "int main(){switch(1){case 1:break;\ncase 1:break;}return 0;}", "2 n");
        check("switch duplicate default", "int main(){switch(1){default:break;\ndefault:break;}return 0;}", "2 n");
        check("assign constant", "int main(){const int a=1;\na=2;\nreturn 0;}", "2 h");
        check("void return with expression", "void f(){\nreturn 1;\n}\nint main(){return 0;}", "2 f");
        check("global initializer const context", "int a=1;\nint b=a;\nint main(){return 0;}", "2 c");
        check("error recovery inside const array", "const int a[1]={get_int()};\nint main(){\nmissing=1;\nreturn 0;}", "1 c\n3 c");
        check("unary char wrap affects array length", "const char n=-(char)1;\nint a[(int)n]={1,2};\nint main(){return 0;}", "");
        check("binary char wrap affects array length", "const char n=((char)255+(char)2)/(char)2;\nint a[(int)n+(int)1]={1,2};\nint main(){return 0;}", "2 e");
        check("missing semicolon still reported", "int main(){\nint x=1\nreturn 0;\n}", "2 i");
        check("missing parenthesis still reported", "int main(){\nprintf(\"ok\";\nreturn 0;}", "2 j");
        check("missing bracket still reported", "int main(){\nint a[2;\nreturn 0;}", "2 k");
        check("single ampersand still reported", "int main(){\nif(1 & 1);\nreturn 0;}", "2 a");
        check("symbol table scope and qualifiers", "const char s[2]=\"ab\";\nint f(int p){static int x;{char p='a';}return p;}\nint main(){return 0;}", "");
        String expectedSymbols = "1 s ConstCharArray\n1 f IntFunc\n2 p Int\n2 x StaticInt\n3 p Char";
        if (Controller.symbols.toString().equals(expectedSymbols)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL symbol table contents: " + Controller.symbols);
        }
        check("bare return missing semicolon", "void f(){\nreturn\n}\nint main(){return 0;}", "2 i");
        check("empty call missing closing parenthesis", "void f(){}\nint main(){\nf(;\nreturn 0;}", "3 j");
        check("recover return then collect semantic errors", "void f(){\nreturn\n}\nint main(){\nunknown=1;\nbreak;\nprintf(\"%d\");\nreturn 0;}", "2 i\n5 c\n6 m\n7 l");
        check("recover empty call then collect semantic errors", "void f(){}\nint main(){\nf(;\nunknown=1;\nbreak;\nreturn 0;}", "3 j\n4 c\n5 m");
        check("bare return missing semicolon before if", "void f(){\nreturn\nif(1);\n}\nint main(){return 0;}", "2 i");
        check("bare return missing semicolon before printf", "void f(){\nreturn\nprintf(\"ok\");\n}\nint main(){return 0;}", "2 i");
        check("bare return missing semicolon before int declaration", "void f(){\nreturn\nint x;\n}\nint main(){return 0;}", "2 i");
        check("bare return missing semicolon before char declaration", "void f(){\nreturn\nchar x;\n}\nint main(){return 0;}", "2 i");
        check("empty call missing parenthesis in return", "int f(){return 1;}\nint main(){\nreturn f(;\n}", "3 j");
        check("empty call missing parenthesis before multiplication", "int f(){return 1;}\nint main(){\nint x=f(*2;\nreturn 0;}", "3 j");
        check("empty call missing parenthesis in outer argument", "int f(){return 1;}\nvoid g(int a,int b){}\nint main(){\ng(f(,2);\nreturn 0;}", "4 j");
        check("empty call missing parenthesis at end of condition", "int f(){return 1;}\nint main(){\nif(f(){}\nreturn 0;}", "3 j");
        check("bare return and next function still checked", "void f(){\nreturn\n}\nvoid g(){\nunknown=1;\n}\nint main(){return 0;}", "2 i\n5 c");
        check("CLI ten-error recovery fixture", java.nio.file.Files.readString(
                java.nio.file.Path.of("tests/semantic-recovery.sysy")),
                "2 a\n3 i\n7 c\n9 b\n10 e\n11 d\n12 l\n13 e\n14 m\n16 h");
        System.out.println("Semantic regression: " + passed + " passed, " + failed + " failed");
        if (failed != 0) System.exit(1);
    }
}
