"""Independent token/tree oracles and a deliberately bounded semantic model."""
from __future__ import annotations

import re
from dataclasses import dataclass

from .grammar import GenerationError, Token, Tree


KEYWORDS = {word: word.upper() + "TK" for word in (
    "const int char void main if else while switch case default break continue return static printf".split())}
TOKEN_CODES = dict(KEYWORDS, **{
    "+": "PLUS", "-": "MINU", "*": "MULT", "/": "DIV", "%": "MOD", "!": "NOT",
    "&&": "AND", "||": "OR", "==": "EQL", "!=": "NEQ", "<": "LSS", "<=": "LEQ",
    ">": "GRE", ">=": "GEQ", "=": "ASSIGN", ";": "SEMICN", ",": "COMMA", ":": "COLON",
    "(": "LPARENT", ")": "RPARENT", "[": "LBRACK", "]": "RBRACK", "{": "LBRACE", "}": "RBRACE",
    "++": "INC", "--": "DEC",
})
CATEGORY_CODES = {"Ident": "IDENFR", "IntConst": "INTCON", "CharConst": "CHARCON", "StringConst": "STRCON"}
HIDDEN_NODES = {"Decl", "BType", "BlockItem"}
EXPRESSION_NAMES = {"Exp", "ConstExp", "Cond", "LOrExp", "LAndExp", "EqExp", "RelExp",
                    "AddExp", "MulExp", "UnaryExp", "PrimaryExp", "LVal", "Number"}


class UnsupportedSemantics(GenerationError):
    pass


class InvalidProgram(GenerationError):
    pass


def tokenize(source, codes):
    operators = sorted((s for s in codes if not s.isidentifier()), key=lambda s: (-len(s), s))
    tokens, pos, line = [], 0, 1
    while pos < len(source):
        c = source[pos]
        if c.isspace():
            line += c == "\n"
            pos += 1
            continue
        if source.startswith("//", pos):
            end = source.find("\n", pos)
            pos = len(source) if end < 0 else end
            continue
        if source.startswith("/*", pos):
            end = source.find("*/", pos + 2)
            if end < 0:
                raise GenerationError("未闭合的块注释")
            line += source[pos:end + 2].count("\n")
            pos = end + 2
            continue
        if c in "'\"":
            match = re.match(r'''(?:'(?:\\.|[^'\\\n])*'|"(?:\\.|[^"\\\n])*")''', source[pos:])
            if not match:
                raise GenerationError("未闭合的字符或字符串")
            text = match.group()
            category = "CharConst" if c == "'" else "StringConst"
        else:
            match = re.match(r"[A-Za-z_]\w*|[0-9]+", source[pos:])
            if match:
                text = match.group()
                category = "IntConst" if text[0].isdigit() else ("" if text in codes else "Ident")
            else:
                text = next((s for s in operators if source.startswith(s, pos)), None)
                if text is None:
                    raise GenerationError(f"没有 token 编码的终结符: {source[pos:pos + 12]!r}；请使用 --tokens")
                category = ""
        tokens.append(Token(text, category, line))
        pos += len(text)
    return tokens


def token_line(token, codes):
    return f"{CATEGORY_CODES[token.category] if token.category else codes[token.text]} {token.text}"


def lexer_output(tokens, codes):
    return "\n".join(token_line(t, codes) for t in tokens)


def parser_output(tree, codes, hidden=()):
    lines = []
    suppressed = HIDDEN_NODES | set(hidden)

    def visit(node):
        for child in node.children:
            if isinstance(child, Token):
                lines.append(token_line(child, codes))
            else:
                visit(child)
        if node.name not in suppressed:
            lines.append(f"<{node.name}>")

    visit(tree)
    return "\n".join(lines)


def subtrees(node, name=None):
    return [c for c in node.children if isinstance(c, Tree) and (name is None or c.name == name)]


def text(node):
    return " ".join(t.text for t in node.tokens())


@dataclass
class Variable:
    type: str
    constant: bool = False


class SemanticModel:
    """Validate generated programs; reject uncertain rules rather than inventing results.

    This is not a general SysY replacement compiler. It models the initialized,
    integer-oriented profile emitted by this tool. Constants in array sizes and
    initializers are restricted to literals; arbitrary syntax mode has no oracle.
    """
    def __init__(self, operators=None, transparent=()):
        self.operators = {op: "same" for op in ["+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!="]}
        self.operators.update({"&&": "int", "||": "int"})
        self.operators.update(operators or {})
        self.transparent = set(transparent)
        self.scopes = [(1, {})]
        self.counter = 1
        self.symbols = []
        self.functions = {}

    def push(self):
        self.counter += 1
        self.scopes.append((self.counter, {}))

    def declare(self, name, type_, const=False, static=False):
        scope, variables = self.scopes[-1]
        if name in variables or (scope == 1 and name in self.functions):
            raise InvalidProgram("重复声明")
        variables[name] = Variable(type_, const)
        label = ("Const" if const else "Static" if static else "") + ("Char" if type_.startswith("char") else "Int")
        if type_.endswith("[]"):
            label += "Array"
        self.symbols.append((scope, len(self.symbols), f"{scope} {name} {label}"))

    def lookup(self, name):
        for _, variables in reversed(self.scopes):
            if name in variables:
                return variables[name]
        raise InvalidProgram(f"未定义变量 {name}")

    @staticmethod
    def basic(type_):
        if type_ not in ("int", "char"):
            raise InvalidProgram("需要基本类型")
        return type_

    def lvalue(self, node, const=False):
        name = node.children[0].text
        var = self.lookup(name)
        if const and not var.constant:
            raise InvalidProgram("常量上下文引用了变量")
        type_ = var.type
        indices = subtrees(node)
        if indices:
            if not type_.endswith("[]") or self.expression(indices[0], False, const) != "int":
                raise InvalidProgram("数组或下标类型非法")
            type_ = type_[:-2]
        return type_, var

    def expression(self, node, condition=False, const=False):
        nodes = subtrees(node)
        children = node.children
        if node.name == "LVal":
            return self.lvalue(node, const)[0]
        if node.name == "Number":
            return "char" if children[0].category == "CharConst" else "int"
        if node.name == "UnaryExp":
            inc = [c for c in children if isinstance(c, Token) and c.text in ("++", "--")]
            if inc:
                if len(children) != 2 or len(inc) != 1 or len(nodes) != 1 or nodes[0].name != "LVal":
                    raise UnsupportedSemantics("自增/自减契约只支持 '++' LVal、LVal '++' 及对应的 '--' 形式")
                type_, var = self.lvalue(nodes[0], False)
                if var.constant or const:
                    raise InvalidProgram("不能修改常量或在常量上下文中自增")
                return self.basic(type_)
            if children and isinstance(children[0], Token) and children[0].category == "Ident":
                name = children[0].text
                if name not in self.functions or const:
                    raise InvalidProgram("未定义调用或常量上下文调用")
                result, parameters = self.functions[name]
                params = next((c for c in nodes if c.name == "FuncRParams"), None)
                actual = [self.expression(c, condition) for c in subtrees(params, "Exp")] if params else []
                if actual != parameters:
                    raise InvalidProgram("函数实参与形参不匹配")
                return result
            if nodes and nodes[0].name == "UnaryOp":
                op = text(nodes[0])
                operand = nodes[1]
                if operand.name == "UnaryExp":
                    inner = subtrees(operand, "UnaryOp")
                    if inner and text(inner[0]) == op:
                        raise InvalidProgram("相邻相同普通单目运算符")
                value_type = self.basic(self.expression(operand, condition, const))
                if op == "!":
                    if not condition:
                        raise InvalidProgram("逻辑非只能位于条件中")
                    return "int"
                if op not in ("+", "-"):
                    raise UnsupportedSemantics(f"未知单目运算符 {op}")
                return value_type
            btype = next((c for c in nodes if c.name == "BType"), None)
            if btype:
                self.basic(self.expression(nodes[-1], condition, const))
                return text(btype)
        if len(nodes) == 1 and not any(isinstance(c, Token) and c.text not in ("(", ")") for c in children):
            result = self.expression(nodes[0], condition, const)
            return "int" if node.name in ("LOrExp", "LAndExp") else result
        if len(children) == 3 and isinstance(children[1], Token) and len(nodes) == 2:
            op = children[1].text
            if op not in self.operators:
                raise UnsupportedSemantics(f"未知运算符 {op}；请用 --semantics 定义其类型契约")
            left = self.basic(self.expression(nodes[0], condition, const))
            right = self.basic(self.expression(nodes[1], condition, const))
            if op not in ("&&", "||") and left != right:
                raise InvalidProgram("混合基本类型运算")
            return "int" if self.operators[op] == "int" else left
        if node.name in self.transparent and len(nodes) == 1:
            return self.expression(nodes[0], condition, const)
        raise UnsupportedSemantics(f"没有 {node.name} 的语义契约: {text(node)}")

    def declaration(self, node):
        const = node.name == "ConstDecl"
        base = text(subtrees(node, "BType")[0])
        static = any(isinstance(c, Token) and c.text == "static" for c in node.children)
        for definition in subtrees(node, "ConstDef" if const else "VarDef"):
            name = definition.children[0].text
            length = subtrees(definition, "ConstExp")
            if length:
                # Full constant evaluation isn't needed by the bounded profile.
                raw = list(length[0].tokens())
                if len(raw) != 1 or raw[0].category != "IntConst" or int(raw[0].text) < 1:
                    raise InvalidProgram("生成模式只使用正整数字面量数组长度")
            type_ = base + ("[]" if length else "")
            self.declare(name, type_, const, static)
            init = subtrees(definition, "ConstInitVal" if const else "InitVal")
            if init:
                items = subtrees(init[0])
                for item in items:
                    actual = self.expression(item, False, const or len(self.scopes) == 1)
                    if actual != base:
                        raise InvalidProgram("初值类型不匹配")
                if not items and any(t.category == "StringConst" for t in init[0].tokens()) and type_ != "char[]":
                    raise InvalidProgram("字符串初值需要字符数组")

    def statement(self, node, loop=0, switch=0):
        if node.name == "Block":
            self.push()
            self.block(node, loop, switch)
            self.scopes.pop()
            return
        nodes = subtrees(node)
        first = node.children[0] if node.children else None
        keyword = first.text if isinstance(first, Token) else ""
        if keyword == "break" and not (loop or switch):
            raise InvalidProgram("break 不在循环或 switch 中")
        if keyword == "continue" and not loop:
            raise InvalidProgram("continue 不在循环中")
        if keyword == "printf":
            fmt = next(t.text[1:-1] for t in node.children if isinstance(t, Token) and t.category == "StringConst")
            expected = ["int" if x == "d" else "char" for x in re.findall(r"%(d|c)", fmt)]
            actual = [self.expression(n) for n in nodes]
            if actual != expected:
                raise InvalidProgram("printf 格式参数不匹配")
            return
        if nodes and nodes[0].name == "LVal":
            type_, var = self.lvalue(nodes[0])
            if var.constant or self.basic(type_) != self.expression(nodes[1]):
                raise InvalidProgram("非法赋值")
            return
        labels = set()
        selector = None
        if keyword == "switch":
            selector = self.basic(self.expression(nodes[0]))
        for child in nodes:
            if child.name == "Cond":
                self.basic(self.expression(child, True))
            elif child.name == "Exp":
                value = self.expression(child)
                if keyword == "return" and value != "int":
                    raise InvalidProgram("生成函数返回类型必须为 int")
            elif child.name == "Block":
                self.statement(child, loop, switch)
            elif child.name == "Stmt":
                self.statement(child, loop + (keyword == "while"), switch)
            elif child.name == "CaseStmt":
                number = subtrees(child, "Number")
                label = text(number[0]) if number else "default"
                if label in labels:
                    raise InvalidProgram("重复 case/default")
                labels.add(label)
                if number and self.expression(number[0]) != selector:
                    raise InvalidProgram("case 类型不匹配")
                for stmt in subtrees(child, "Stmt"):
                    self.statement(stmt, loop, switch + 1)
            else:
                raise UnsupportedSemantics(f"未知语句节点 {child.name}")

    def block(self, node, loop=0, switch=0):
        for item in subtrees(node, "BlockItem"):
            child = subtrees(item)[0]
            if child.name == "Decl":
                self.declaration(subtrees(child)[0])
            else:
                self.statement(child, loop, switch)

    def analyze(self, tree):
        for child in subtrees(tree):
            if child.name == "Decl":
                self.declaration(subtrees(child)[0])
            elif child.name in ("FuncDef", "MainFuncDef"):
                params = subtrees(child, "FuncFParams")
                formal = subtrees(params[0], "FuncFParam") if params else []
                if child.name == "FuncDef":
                    name = next(t.text for t in child.children if isinstance(t, Token) and t.category == "Ident")
                    result = text(subtrees(child, "FuncType")[0])
                    types = [text(subtrees(p, "BType")[0]) + ("[]" if len(p.children) > 2 else "") for p in formal]
                    self.functions[name] = (result, types)
                    self.symbols.append((1, len(self.symbols), f"1 {name} {result.title()}Func"))
                self.push()
                for parameter in formal:
                    name = next(t.text for t in parameter.children if isinstance(t, Token) and t.category == "Ident")
                    self.declare(name, text(subtrees(parameter, "BType")[0]))
                self.block(subtrees(child, "Block")[0])
                self.scopes.pop()
            else:
                raise UnsupportedSemantics(f"不支持的顶层节点 {child.name}")
        return "\n".join(row[2] for row in sorted(self.symbols))
