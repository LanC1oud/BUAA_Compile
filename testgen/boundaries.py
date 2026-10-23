"""Deterministic, reviewed frontend fixtures independent of the compiler under test.

Grammar alone says nothing about mutability, type rules or error codes. Baseline
fixtures follow the course contract; extension fixtures state an explicit contract.
Unsupported extension shapes are reported, never silently assigned semantics.
"""
from __future__ import annotations

import random
import re
import textwrap
from collections import Counter
from dataclasses import dataclass
from difflib import SequenceMatcher

from .grammar import GenerationError, Term, Token, Tree
from .oracle import KEYWORDS, lexer_output, parser_output, subtrees, text, tokenize


@dataclass
class Fixture:
    name: str
    source: str
    tags: tuple[str, ...]
    repair: str | None = None
    reject: bool = False
    contract: str = "course-frontend"


def clean(source):
    return textwrap.dedent(source).strip() + "\n"


def program(body, globals_="", helpers=""):
    return clean(globals_ + "\n" + helpers + "\nint main() {\n" + body + "\nreturn 0;\n}")


def symbol_inventory(tree):
    """Declaration/scope projection for reviewed valid fixtures, not a validator."""
    rows = []
    next_scope = 1

    def push():
        nonlocal next_scope
        next_scope += 1
        return next_scope

    def declare(scope, name, label):
        rows.append((scope, len(rows), f"{scope} {name} {label}"))

    def visit(node, scope):
        if node.name in ("ConstDecl", "VarDecl"):
            base = text(subtrees(node, "BType")[0]).title()
            qualifier = "Const" if node.name == "ConstDecl" else "Static" if any(
                isinstance(c, Token) and c.text == "static" for c in node.children) else ""
            for definition in subtrees(node):
                if definition.name not in ("ConstDef", "VarDef"):
                    continue
                array = bool(subtrees(definition, "ConstExp"))
                declare(scope, definition.children[0].text, qualifier + base + ("Array" if array else ""))
            return
        if node.name in ("FuncDef", "MainFuncDef"):
            if node.name == "FuncDef":
                name = next(t.text for t in node.children if isinstance(t, Token) and t.category == "Ident")
                declare(1, name, text(subtrees(node, "FuncType")[0]).title() + "Func")
            child_scope = push()
            for params in subtrees(node, "FuncFParams"):
                for param in subtrees(params, "FuncFParam"):
                    name = next(t.text for t in param.children if isinstance(t, Token) and t.category == "Ident")
                    array = any(isinstance(c, Token) and c.text == "[" for c in param.children)
                    declare(child_scope, name, text(subtrees(param, "BType")[0]).title() + ("Array" if array else ""))
            for child in subtrees(subtrees(node, "Block")[0]):
                visit(child, child_scope)
            return
        if node.name == "Block":
            scope = push()
        for child in subtrees(node):
            visit(child, scope)

    visit(tree, 1)
    return "\n".join(r[2] for r in sorted(rows))


def expected_errors(source):
    # Each fixture explicitly states the winning error per source line.
    return [(line, m.group(1)) for line, value in enumerate(source.splitlines(), 1)
            if (m := re.search(r"//\s*@expect\s+([a-z])\b", value))]


def nearest_else(tree):
    """Resolve the baseline's dangling-else ambiguity toward the nearest if."""
    children = tuple(nearest_else(c) if isinstance(c, Tree) else c for c in tree.children)
    tree = Tree(tree.name, children, tree.production)

    def is_if(node):
        return isinstance(node, Tree) and node.name == "Stmt" and node.children and isinstance(node.children[0], Token) and node.children[0].text == "if"

    def attach(node, suffix):
        if not is_if(node):
            return None
        # The final branch is the only possible unclosed if on this path.
        pos = 6 if len(node.children) == 7 else 4
        nested = attach(node.children[pos], suffix)
        if nested is not None:
            values = list(node.children)
            values[pos] = nested
            return Tree(node.name, tuple(values), node.production)
        if len(node.children) == 5:
            return Tree(node.name, node.children + suffix, node.production)
        return None

    if is_if(tree) and len(children) == 7:
        inner = attach(children[4], children[5:])
        if inner is not None:
            tree = Tree(tree.name, children[:4] + (inner,), tree.production)
    return tree


def recovery_tree(tree, canonical, actual):
    """Project a repaired derivation onto actual tokens, omitting missing delimiters.

    The only permitted edits are deleted ; ) ] or single &/| lexer recovery.
    Insertions or arbitrary replacements cannot manufacture a parser oracle.
    """
    signature = lambda tokens: [(t.category, t.text) for t in tokens]
    mapping = {}
    for op, a, b, c, d in SequenceMatcher(a=signature(canonical), b=signature(actual), autojunk=False).get_opcodes():
        if op == "equal":
            mapping.update((id(canonical[i]), actual[c + i - a]) for i in range(a, b))
        elif op == "delete" and all(t.text in (";", ")", "]") for t in canonical[a:b]):
            pass
        elif op == "replace" and b - a == d - c == 1 and (canonical[a].text, actual[c].text) in (("&&", "&"), ("||", "|")):
            mapping[id(canonical[a])] = actual[c]
        else:
            raise GenerationError("边界用例的恢复变换超出独立参考模型")

    def project(node):
        return Tree(node.name, tuple(project(c) if isinstance(c, Tree) else mapping[id(c)]
                                     for c in node.children if isinstance(c, Tree) or id(c) in mapping), node.production)

    return project(tree)


def materialize(fixture, grammar, codes, hide, seed):
    source = fixture.source
    # a fixtures have a documented recovery rule; ordinary valid &/| are absent.
    local_codes = dict(codes, **{"&": "AND", "|": "OR"}) if fixture.repair else codes
    tokens = tokenize(source, local_codes)
    expected = {"lexer.txt": lexer_output(tokens, local_codes)}
    metadata = {"mode": "program", "kind": "boundary", "name": fixture.name, "seed": seed,
                "tags": list(fixture.tags), "oracle": "reviewed-fixture+independent-grammar-parser",
                "contract": fixture.contract, "productions_used": {},
                "terminals_used": dict(Counter(t.category or t.text for t in tokens))}
    if fixture.reject:
        try:
            grammar.parse(tokens)
        except GenerationError as error:
            if not str(error).startswith("输入不符合"):
                raise
        else:
            raise GenerationError(f"拒绝用例已被输入文法接受，需重新约定边界: {fixture.name}")
        metadata["expected_outcome"] = "syntax-rejection"
        metadata["stage_outputs"] = {"lexer": "lexer.txt", "syntax": "rejection (no prescribed error code)"}
    else:
        canonical = tokenize(fixture.repair or source, codes)
        tree = nearest_else(grammar.parse(canonical))
        usage = Counter(str(n.production) for n in tree.nodes())
        if fixture.repair:
            tree = recovery_tree(tree, canonical, tokens)
        errors = expected_errors(source)
        render = lambda pairs: "\n".join(f"{line} {code}" for line, code in pairs)
        expected.update({"parser.txt": parser_output(tree, local_codes, hide), "error.txt": render(errors),
                         "lexer.errors.txt": render((l, c) for l, c in errors if c == "a"),
                         "parser.errors.txt": render((l, c) for l, c in errors if c in "aijk")})
        if not errors:
            expected["symbol.txt"] = symbol_inventory(tree)
        metadata["productions_used"] = dict(usage)
        metadata["stage_outputs"] = {"lexer": "lexer.txt", "syntax": "parser.txt",
                                     "semantic": "error.txt" if errors else "symbol.txt"}
        metadata["tags"] += ["error:" + c for c in sorted({c for _, c in errors})]
    return {"source": source, "expected": expected, "metadata": metadata}


def baseline_fixtures(scale):
    fixtures = []

    def add(name, body, *tags, globals_="", helpers="", repair=None):
        fixtures.append(Fixture(name, program(body, globals_, helpers), tuple(tags),
                                program(repair, globals_, helpers) if repair else None))

    add("integer-boundaries", "int a=0,b=1,c=2147483647,d=(-2147483647-1);\na=a+b; c=c-1; d=d+1;",
        "literal:int32", "expression:sign", "declaration:multiple")
    add("char-cast-boundaries", "char a=(char)0,b=(char)127,c=(char)128,d=(char)255,e=(char)256,f=(char)-1;\nint x=(int)a+(int)d;",
        "literal:char", "cast:truncation", "type:explicit-cast")
    add("arithmetic-precedence", "int x=2+3*4-8/2%3,y=(2+3)*(4-1),z=-7/3;\nx=x+-y; x=x- -y;",
        "expression:precedence", "expression:associativity", "expression:negative-division")
    add("all-relations", "int x=0,y=1;\nif(x<y && x<=y || x>y && x>=y || x==y || x!=y) x=1;\nif(!x || !(!y)) y=2;",
        "condition:all-operators", "condition:short-circuit")
    add("short-circuit-calls", "if(0 && side()) ;\nif(1 || side()) ;",
        "condition:short-circuit", "call:side-effects", helpers="int side(){ printf(\"hit\"); return 1; }")
    add("empty-and-dangling-else", "int x=0;\n; {} {{;}}\nif(1) if(0) x=1; else x=2;\nif(0) ; else if(1) ; else ;",
        "statement:empty", "statement:dangling-else", "scope:empty-block")
    add("array-initializers", "int one[1]={1}, partial[4]={1}, empty[2]={};\nchar chars[4]=\"abc\", blank[1]=\"\";\none[0]=partial[3]; chars[3]=(char)0;",
        "array:length-one", "array:partial-init", "array:empty-init", "array:last-element", "string:empty", "constant:string-init",
        globals_='const char letters[4]="abc",empty_string[1]="";')
    add("constant-array-size", "int a[n+1];\na[0]=table[1];",
        "constant:expression", "array:constant-size", globals_="const int n=2,table[2]={7,8};")
    add("frontend-only-runtime-hazards", "int a[1]={0},x=1;\nx=a[-1]; x=a[1]; x=1/0;",
        "frontend-only", "array:out-of-bounds", "expression:zero-divisor")
    add("escaped-literals", r"char a='\n',b='\t',c='\r',d='\0',e='\\',f='\'';" + "\n" + r'char s[8]="a\n\t\r\0\\\'";',
        "literal:escapes", "string:escapes")
    add("printf-types", 'int x=1; char c=\'a\',s[2]="a";\nprintf("%d%c%s",x,c,s);\nprintf("");',
        "printf:all-types", "printf:empty-format")
    add("lexical-comments", "int x=1; /* block\ncomment */\nx=x+/* gap */+x; // trailing\nx=x- /* gap */-x;",
        "lexer:comment-adjacency", "lexer:multiline-comment", "expression:spaced-unary")
    long_name = "variable_" + "x" * (128 * scale)
    add("long-identifier", f"int {long_name}=1;\n{long_name}={long_name}+1;", "lexer:long-identifier")
    add("scope-shadowing", "int x=1;\n{char x='a'; {int x=2; x=x+1;} x='b';}\nx=x+1;",
        "scope:shadowing", "scope:sibling-and-nested", globals_="int x;")
    add("static-storage", "static int a=1,b[2]={1,2};\nstatic char c='a',s[2]=\"a\";\na=b[1];",
        "declaration:static", "type:static-array")
    add("array-parameters", "int a[2]={1,2}; char s[2]=\"a\";\na[0]=head(a); s[0]=first(s);",
        "call:array-parameters", "type:char-function", helpers="int head(int a[]){return a[0];}\nchar first(char s[]){return s[0];}")
    add("recursive-and-void-calls", "int x=rec(4);\nnoop();",
        "call:recursion", "call:void", "call:zero-parameters",
        helpers="int rec(int n){if(n==0)return 1;return n*rec(n-1);}\nvoid noop(){return;}")
    count = 16 * scale
    parameters = ",".join(f"int p{i}" for i in range(count))
    add("many-parameters", "int x=many(" + ",".join(str(i) for i in range(count)) + ");",
        "stress:parameters", helpers=f"int many({parameters}){{return p0;}}")
    add("long-expression", "int x=" + "+".join(str(i % 8) for i in range(64 * scale)) + ";",
        "stress:expression-chain", "expression:left-recursion")
    add("deep-parentheses", "int x=" + "(" * (16 * scale) + "1" + ")" * (16 * scale) + ";",
        "stress:parentheses", "expression:recursion")
    add("deep-scopes", "int x=0;\n" + "{int x=1;\n" * (8 * scale) + "x=x+1;\n" + "}\n" * (8 * scale),
        "stress:scopes", "scope:shadowing")
    add("loop-switch-control", "int x=0;\nwhile(x<2){\nx=x+1;\nswitch(x){case 1:continue;case 2:break;default:break;}\nif(x==2)break;\n}",
        "control:loop-switch", "control:continue-in-switch-in-loop", "control:break")
    add("char-switch", "char c='a';\nswitch(c){case 'a':c='b';break;case 'b':;default:;}\nswitch(0){}",
        "switch:char-label", "switch:empty", "switch:fallthrough")
    add("undefined-and-scope-errors", "unknown=1; // @expect c\n{int local=1;}\nlocal=2; // @expect c\nint before=later; // @expect c\nint later=1;",
        "error:undefined", "scope:use-before-declaration", "scope:out-of-scope")
    add("duplicate-declarations", "int x=1;\nint x=2; // @expect b\n{int y=1; int y=2;} // @expect b",
        "error:duplicate", "scope:duplicate-local")
    add("duplicate-parameters", "", "error:duplicate", "scope:parameter-local",
        helpers="int f(int x,int x){ // @expect b\nint x; // @expect b\nreturn 0;\n}")
    add("call-count-and-type", "int a[1]={1}; char c='a';\nf(); // @expect d\nf(1,2); // @expect d\nf(c); // @expect e\nf(a); // @expect e",
        "call:argument-boundaries", "type:array-vs-scalar", helpers="int f(int x){return x;}")
    add("array-and-scalar-types", "int a[1]={0},x=0; char c='a';\nx=c; // @expect e\nx=a; // @expect e\na[c]=1; // @expect e\nx[0]=1; // @expect e",
        "type:array-vs-scalar", "array:char-index", "array:scalar-index")
    add("const-targets", "k=1; // @expect h\nca[0]=1; // @expect h", "constant:assignment", "array:const-element",
        globals_="const int k=1,ca[1]={1};")
    fixtures.append(Fixture("return-errors", clean("""
        void v(){
        return 1; // @expect f
        }
        int f(){
        int x=1;
        } // @expect g
        int bare(){
        return; // @expect e
        }
        int main(){
        return 0;
        }
    """), ("return:void-value", "return:missing", "return:bare-in-nonvoid")))
    add("printf-errors", 'char c=\'a\';\nprintf("%d"); // @expect l\nprintf("",1); // @expect l\nprintf("%d",c); // @expect e',
        "printf:argument-count", "printf:argument-type")
    add("switch-duplicates", "switch(1){\ncase 1:;\ncase 1:; // @expect n\ndefault:;\ndefault:; // @expect n\n}",
        "switch:duplicate-case", "switch:duplicate-default")
    add("switch-label-type", "char c='a';\nswitch(c){\ncase 1:; // @expect e\n}", "switch:label-type")
    add("loop-control-errors", "break; // @expect m\ncontinue; // @expect m\nswitch(1){default:continue;} // @expect m",
        "control:outside-loop", "control:continue-in-switch")
    repaired = "int a[1]={0},x=0;\nx=1; // @expect i\nprintf(\"ok\"); // @expect j\na[0]=1; // @expect k"
    broken = repaired.replace("x=1;", "x=1").replace('printf("ok");', 'printf("ok";').replace("a[0]=1", "a[0=1")
    add("recovery-three-delimiters", broken, "recovery:multiple-lines", "recovery:ijk", repair=repaired)
    repaired = "int x=0;\nif(x && x) ; // @expect a\nif(x || x) ; // @expect a"
    add("single-logical-token", repaired.replace("&&", "&").replace("||", "|"),
        "lexer:invalid-and-or", "recovery:lexical", repair=repaired)
    repaired = "unknown=1; // @expect i"
    add("same-line-priority", repaired.replace("=1;", "=1"), "error:priority", "recovery:undefined-with-missing-semicolon", repair=repaired)
    # Whitespace/EOF variants preserve tokens and semantic answers, but line
    # positions are recomputed from the final source.
    fixtures.append(Fixture("eof-without-newline", "int main(){return 0;}// eof", ("lexer:eof-comment", "lexer:no-final-newline")))
    fixtures.append(Fixture("crlf-tabs", "\r\nint\tmain()\r\n{\r\n\treturn\t0;\r\n}\r\n", ("lexer:crlf", "lexer:tabs")))
    return fixtures


def extension_fixtures(grammar, codes, semantics):
    fixtures, limitations = [], []
    inc_shapes = []
    for p in grammar.productions:
        if p.lhs == "UnaryExp" and len(p.rhs) == 2:
            if isinstance(p.rhs[0], Term) and p.rhs[0].text in ("++", "--") and p.rhs[1] == "LVal":
                inc_shapes.append((p.rhs[0].text, True))
            if p.rhs[0] == "LVal" and isinstance(p.rhs[1], Term) and p.rhs[1].text in ("++", "--"):
                inc_shapes.append((p.rhs[1].text, False))
    globals_ = "const int k=1,ca[2]={1,2}; const char kc='a';"
    helpers = "int id(int p){return p;}\nchar cid(char p){return p;}"
    declarations = "int x=1,y=2,a[2]={1,2},i=0; char c='a',chars[2]=\"a\";\n"
    for op, prefix in inc_shapes:
        direction = "pre" if prefix else "post"
        key = ("inc" if op == "++" else "dec") + "-" + direction
        expr = lambda target: op + target if prefix else target + op
        contract = f"{op} direct-LVal; mutable scalar/element; result keeps type; const h; invalid type e; const-context o (extension contract)"

        def add(suffix, body, *tags, reject=False):
            fixtures.append(Fixture(key + "-" + suffix, program(declarations + body, globals_, helpers),
                                    ("extension:" + op, "extension:" + direction) + tags, reject=reject, contract=contract))

        for target, label in (("x", "int"), ("c", "char"), ("a[0]", "first-element"), ("a[1]", "last-element"), ("chars[0]", "char-element"), ("a[i]", "variable-index")):
            add("target-" + label, expr(target) + ";", "extension:mutable-target", "extension:target-" + label)
        e = expr("x")
        for label, body in (
            ("precedence", "y=2*" + e + "+3; y=- " + e + ";"),
            ("call", "id(" + e + "); cid(" + expr("c") + ");"),
            ("init", "int z=" + e + ";"),
            ("condition", "if(" + e + ">0) y=1;"),
            ("loop", "while(" + e + "<3){break;}"),
            ("switch", "switch(" + e + "){default:break;}"),
            ("printf", 'printf("%d%c",' + e + "," + expr("c") + ");"),
            ("cast", "c=(char)" + e + ";"),
            ("return", "return " + e + ";"),
            ("shadow", "{int k=1;" + expr("k") + ";}"),
            ("value-boundaries", "x=2147483647; " + e + "; x=(-2147483647-1); " + e + "; c=(char)255; " + expr("c") + ";"),
            ("nested-index", expr("a[" + expr("i") + "]") + ";"),
            ("const-scalar", expr("k") + "; // @expect h"),
            ("const-char", expr("kc") + "; // @expect h"),
            ("const-array", expr("ca[0]") + "; // @expect h"),
            ("undefined", expr("missing") + "; // @expect c"),
            ("whole-array", expr("a") + "; // @expect e"),
            ("function-target", expr("id") + "; // @expect e"),
            ("char-index", expr("a[c]") + "; // @expect e"),
            ("result-type", "c=" + e + "; // @expect e"),
            ("const-context", "const int bad=" + e + "; // @expect o"),
            ("array-size", "int bad[" + e + "]; // @expect o"),
        ):
            add(label, body, "extension:context-" + label)
        # Maximal munch and comments are different tokenization boundaries.
        spaced = op + "/**/x" if prefix else "x/**/" + op
        add("comment-gap", spaced + ";", "lexer:extension-comment")
        if not prefix:
            add("adjacent-binary", "y=x" + op + "+y;", "lexer:longest-match")
        invalid = [op + "1", op + "(x+1)", op + "(x)", op + "id(x)", op + "-x"] if prefix else ["1" + op, "(x+1)" + op, "(x)" + op, "id(x)" + op]
        invalid += [op + op + "x", "x" + op + op, op + "x" + op, op]
        for index, value in enumerate(invalid):
            # An input grammar may deliberately allow a formerly invalid shape;
            # such a fixture must not be mislabeled as a syntax rejection.
            candidate = program(declarations + value + ";", globals_, helpers)
            try:
                grammar.parse(tokenize(candidate, codes))
            except GenerationError as error:
                if not str(error).startswith("输入不符合"):
                    raise
                add("reject-" + str(index), value + ";", "extension:invalid-target-or-chain", reject=True)
            else:
                limitations.append(f"{key}: 输入文法接受 {value!r}，未将其标为拒绝用例；需补充其语义契约")
    # Known binary contracts can be exercised independently of the operator name.
    for op, result in semantics.get("operators", {}).items():
        is_binary = any(len(p.rhs) == 3 and isinstance(p.rhs[0], str) and isinstance(p.rhs[2], str)
                        and isinstance(p.rhs[1], Term) and p.rhs[1].text == op for p in grammar.productions)
        if not is_binary:
            limitations.append(f"运算符 {op!r} 不是直接的二元产生式，未生成专属边界")
            continue
        operator_owners = [p.lhs for p in grammar.productions if any(isinstance(s, Term) and s.text == op for s in p.rhs)]
        def reachable(start, owner):
            try:
                grammar.path(start, owner)
                return True
            except GenerationError:
                return False
        expression_context = any(reachable("Exp", owner) for owner in operator_owners)
        condition_context = any(reachable("Cond", owner) for owner in operator_owners)
        if not (expression_context or condition_context):
            limitations.append(f"运算符 {op!r} 无法嵌入 Exp/Cond，未生成二元表达式边界")
            continue
        def use(expression, type_="int"):
            return f"{type_} x={expression};" if expression_context else f"if({expression}) ;"
        contract = f"binary {op}: equal basic operand types; result={result}; type error e; frontend only"
        bodies = [
            ("int32", use(f"2147483647 {op} 0"), ("literal:int32",)),
            ("char", use(f"'a' {op} 'b'", "int" if result == "int" else "char"), ("type:char-operands",)),
            ("mixed", use(f"1 {op} 'a'") + " // @expect e", ("type:mixed",)),
            ("array", f"int a[1]={{1}};\n" + use(f"a {op} 1") + " // @expect e", ("type:array-vs-scalar",)),
            ("chain", use(f"1 {op} 2 {op} 3"), ("expression:associativity",)),
            ("group", use(f"(1 {op} 2) {op} 3"), ("expression:parentheses",)),
        ]
        for label, body, tags in bodies:
            candidate = Fixture(f"binary-{op}-{label}", program(body), ("extension:" + op,) + tags, contract=contract)
            try:
                grammar.parse(tokenize(candidate.source, codes))
            except GenerationError as error:
                if not str(error).startswith("输入不符合"):
                    raise
                limitations.append(f"二元 {op!r} 的 {label} 组合不被输入文法接受，未加入该语义用例")
            else:
                fixtures.append(candidate)
        candidate = Fixture(f"binary-{op}-missing-right", program(f"int x=1 {op} ;" if expression_context else f"if(1 {op} ) ;"),
                            ("extension:" + op, "extension:missing-operand"), reject=True, contract=contract)
        try:
            grammar.parse(tokenize(candidate.source, codes))
        except GenerationError as error:
            if not str(error).startswith("输入不符合"):
                raise
            fixtures.append(candidate)
        else:
            limitations.append(f"二元 {op!r} 接受空右操作数，未作为语法拒绝用例")
    handled = {op for op, _ in inc_shapes} | set(semantics.get("operators", {}))
    baseline = {"+", "-", "*", "/", "%", "!", "&&", "||", "==", "!=", "<", ">", "<=", ">=", "=", ";", ",", ":", "(", ")", "[", "]", "{", "}"}
    new_tokens = {s.text for p in grammar.productions for s in p.rhs if isinstance(s, Term) and not s.category
                  and s.text not in KEYWORDS and s.text not in baseline}
    for op in sorted(new_tokens - handled):
        limitations.append(f"新终结符 {op!r} 没有可用的专属边界策略；仅通用推导/随机部分可涉及，不能声明边界完备")
    limitations += ["有穷测试不证明文法/语义完备；未覆盖全部嵌套深度、错误组合和执行路径",
                    "仅验证前端阶段；不验证求值、短路副作用、整数溢出、越界或运行时输出",
                    "固定基线边界遵循课程文法；替换基础声明/语句规则时可能需要适配固定用例",
                    "新增声明、作用域或复杂语句需要专属语义契约和边界策略，无法仅由 BNF 推出"]
    return fixtures, list(dict.fromkeys(limitations))


def boundary_cases(grammar, codes, semantics, scale=1, seed=2026, hide=()):
    extensions, limitations = extension_fixtures(grammar, codes, semantics)
    fixtures = baseline_fixtures(scale) + extensions
    random.Random(seed).shuffle(fixtures)  # Selection is exhaustive; seed only orders it.
    cases = []
    for fixture in fixtures:
        try:
            cases.append(materialize(fixture, grammar, codes, hide, seed))
        except GenerationError as error:
            raise GenerationError(f"边界用例 {fixture.name}: {error}") from error
    return cases, limitations
