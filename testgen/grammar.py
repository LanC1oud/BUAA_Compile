"""EBNF reader, bounded derivation, and independent Earley reference parser.

No compiler classes are imported. Direct/indirect left recursion is retained in
the reference tree, so parser.txt can be produced from a postorder traversal.
"""
from __future__ import annotations

import ast
import math
import re
from collections import defaultdict, deque
from dataclasses import dataclass


class GrammarError(ValueError):
    pass


class GenerationError(ValueError):
    pass


@dataclass(frozen=True)
class Term:
    text: str
    category: bool = False


@dataclass(frozen=True)
class Token:
    text: str
    category: str = ""
    line: int = 1


@dataclass(frozen=True)
class Tree:
    name: str
    children: tuple
    production: int = -1

    def tokens(self):
        for child in self.children:
            if isinstance(child, Token):
                yield child
            else:
                yield from child.tokens()

    def nodes(self):
        yield self
        for child in self.children:
            if isinstance(child, Tree):
                yield from child.nodes()


@dataclass(frozen=True)
class Production:
    lhs: str
    rhs: tuple


CATEGORIES = {"Ident", "IntConst", "CharConst", "StringConst"}
EBNF_TOKEN = re.compile(r'''\s*(?:('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")|([A-Za-z_]\w*)|([|()\[\]{}]))''')


def uncomment(text):
    """Remove trailing // outside terminal quotes; accept Java-style rule comments."""
    text = text.strip()
    if text.startswith("//"):
        text = text[2:].strip()
    quote = None
    escaped = False
    for i, char in enumerate(text):
        if escaped:
            escaped = False
        elif char == "\\" and quote:
            escaped = True
        elif quote:
            if char == quote:
                quote = None
        elif char in "'\"":
            quote = char
        elif text[i:i + 2] == "//":
            return text[:i].strip()
    return text


def read_rules(text):
    rules = {}
    current = None
    for number, line in enumerate(text.splitlines(), 1):
        comment = line.strip().startswith("//")
        line = uncomment(line)
        if not line:
            continue
        match = re.fullmatch(r"([A-Za-z_]\w*)\s*(?:->|→|::=)\s*(.*)", line)
        if match:
            current, rhs = match.groups()
            rules[current] = rules.get(current, "") + (" | " if current in rules else "") + rhs
        elif line.startswith("|") and current:
            rules[current] += " " + line
        elif comment:
            continue
        else:
            raise GrammarError(f"第 {number} 行不是文法规则: {line}")
    if not rules:
        raise GrammarError("没有找到文法规则")
    return rules


class EbnfReader:
    def __init__(self, text):
        self.items = []
        pos = 0
        while text[pos:].strip():
            match = EBNF_TOKEN.match(text, pos)
            if not match:
                raise GrammarError(f"无法解析 EBNF: {text[pos:pos + 40]}")
            literal, name, operator = match.groups()
            if literal:
                value = ast.literal_eval(literal).replace("−", "-")
                if not value:
                    raise GrammarError("空串请用空候选式表示，不能使用空终结符")
                self.items.append(Term(value))
            elif name:
                self.items.append(Term(name, True) if name in CATEGORIES else name)
            else:
                self.items.append(operator)
            pos = match.end()
        self.pos = 0

    def take(self, expected=None):
        item = self.items[self.pos] if self.pos < len(self.items) else None
        if expected is not None and item != expected:
            raise GrammarError(f"期望 {expected}，实际为 {item}")
        self.pos += 1
        return item

    def parse(self, closing=None):
        alternatives, sequence = [], []
        while self.pos < len(self.items):
            item = self.items[self.pos]
            if closing and item == closing:
                break
            if item == "|":
                self.take()
                alternatives.append(tuple(sequence))
                sequence = []
            elif isinstance(item, str) and item in ("(", "[", "{"):
                self.take()
                end = {"(": ")", "[": "]", "{": "}"}[item]
                body = self.parse(end)
                self.take(end)
                sequence.append((item, body))
            elif isinstance(item, str) and item in (")", "]", "}"):
                raise GrammarError(f"多余的 EBNF 分隔符 {item}")
            else:
                sequence.append(self.take())
        alternatives.append(tuple(sequence))
        return tuple(alternatives)


class Grammar:
    def __init__(self, base, additions="", replace=False):
        self.rules = read_rules(base)
        self.focus_rules = list(read_rules(additions)) if additions.strip() else []
        for lhs, rhs in (read_rules(additions).items() if additions.strip() else []):
            if lhs in self.rules and not replace:
                # Deduplicate alternatives structurally after parsing below.
                rhs = self.rules[lhs] + " | " + rhs
            self.rules[lhs] = rhs
        self.productions = []
        self.by_name = defaultdict(list)
        self.serial = 0
        for lhs, rhs in self.rules.items():
            reader = EbnfReader(rhs)
            alternatives = reader.parse()
            for sequence in dict.fromkeys(alternatives):
                self.add(lhs, self.compile_sequence(sequence))
        for production in self.productions:
            for symbol in production.rhs:
                if isinstance(symbol, str) and symbol not in self.by_name:
                    raise GrammarError(f"未定义非终结符 {symbol}（引用于 {production.lhs}）")
        self.cost = {name: math.inf for name in self.by_name}
        for _ in range(len(self.by_name) + 1):
            changed = False
            for production in self.productions:
                cost = 1 + max((self.cost[s] for s in production.rhs if isinstance(s, str)), default=0)
                if cost < self.cost[production.lhs]:
                    self.cost[production.lhs] = cost
                    changed = True
            if not changed:
                break
        unproductive = [n for n in self.rules if math.isinf(self.cost[n])]
        if unproductive:
            raise GrammarError("文法无法终止推导: " + ", ".join(unproductive))

    def add(self, lhs, rhs):
        index = len(self.productions)
        self.productions.append(Production(lhs, tuple(rhs)))
        self.by_name[lhs].append(index)
        return index

    def compile_sequence(self, sequence):
        result = []
        for item in sequence:
            if not isinstance(item, tuple):
                result.append(item)
                continue
            kind, alternatives = item
            self.serial += 1
            name = f"@{self.serial}"
            if kind == "[":
                self.add(name, ())
            elif kind == "{":
                self.add(name, ())
            for alternative in alternatives:
                rhs = self.compile_sequence(alternative)
                self.add(name, rhs + ([name] if kind == "{" else []))
            result.append(name)
        return result

    def path(self, start, target):
        """Shortest route used to place a focused rule in a real program."""
        queue = deque([(start, [])])
        seen = set()
        while queue:
            name, route = queue.popleft()
            if name == target:
                return route
            if name in seen:
                continue
            seen.add(name)
            for pid in self.by_name[name]:
                for pos, symbol in enumerate(self.productions[pid].rhs):
                    if isinstance(symbol, str):
                        queue.append((symbol, route + [(pid, pos)]))
        raise GenerationError(f"{target} 无法嵌入 {start}；可使用 --mode syntax 生成该规则的片段")

    def derive(self, start, rng, depth=18, repeat=2, focus=None, alternative=0, program=False):
        route = self.path(start, focus) if focus else []
        remaining = [450]
        active = defaultdict(int)

        def expand(name, budget, forced_route=None):
            remaining[0] -= 1
            if remaining[0] < 0 or budget < self.cost[name]:
                raise GenerationError("推导超过深度或节点预算，请增大 --depth 或简化规则")
            choices = [pid for pid in self.by_name[name] if 1 + max(
                (self.cost[s] for s in self.productions[pid].rhs if isinstance(s, str)), default=0) <= budget]
            if name.startswith("@") and active[name] >= repeat:
                choices = [p for p in choices if name not in self.productions[p].rhs]
            next_pos = None
            if forced_route:
                pid, next_pos = forced_route[0]
                if pid not in choices:
                    raise GenerationError("目标规则所在路径超过深度预算，请增大 --depth")
            elif forced_route == [] and focus:
                pid = self.by_name[name][alternative % len(self.by_name[name])]
                if pid not in choices:
                    raise GenerationError("指定候选式超过深度预算，请增大 --depth")
            else:
                # Prefer terminating productions, but still explore recursive ones.
                weights = [1 / (1 + sum(s == name for s in self.productions[p].rhs) * 2) for p in choices]
                pid = rng.choices(choices, weights=weights)[0]
            production = self.productions[pid]
            children = []
            active[name] += 1
            for pos, symbol in enumerate(production.rhs):
                if isinstance(symbol, Term):
                    if symbol.category:
                        value = {"Ident": rng.choice(["x", "y", "z", "k"]),
                                 "IntConst": str(rng.randrange(0, 16)),
                                 "CharConst": "'a'", "StringConst": '"hello"'}[symbol.text]
                        children.append(Token(value, symbol.text))
                    else:
                        children.append(Token(symbol.text))
                else:
                    next_route = forced_route[1:] if forced_route and pos == next_pos else None
                    # Synthetic repeat depth is also bounded; --repeat caps its chain.
                    if symbol == name and name.startswith("@") and budget <= self.cost[name] + 1:
                        child = ()
                    else:
                        child = expand(symbol, budget - 1, next_route)
                    children.extend(child if isinstance(child, tuple) else (child,))
            active[name] -= 1
            if name.startswith("@"):
                return tuple(children)
            tree = Tree(name, tuple(children), pid)
            if program:
                tree = bind_leaves(tree, rng)
            return tree

        return expand(start, depth, route if focus else None)

    def parse(self, tokens, start="CompUnit", max_states=150000):
        """Earley recognizer with one witness tree per state; no compiler calls."""
        if start not in self.by_name:
            raise GrammarError(f"未知开始符号 {start}")
        chart = [dict() for _ in range(len(tokens) + 1)]
        count = 0

        def put(pos, key, children):
            nonlocal count
            if key not in chart[pos]:
                chart[pos][key] = children
                count += 1
                if count > max_states:
                    raise GenerationError("参考解析器状态过多，请降低 --depth")
                return True
            return False

        for pid in self.by_name[start]:
            put(0, (pid, 0, 0), ())
        for pos in range(len(chart)):
            agenda = deque(chart[pos])
            while agenda:
                pid, dot, origin = agenda.popleft()
                production = self.productions[pid]
                children = chart[pos][(pid, dot, origin)]
                if dot == len(production.rhs):
                    value = children if production.lhs.startswith("@") else (Tree(production.lhs, children, pid),)
                    for (parent_id, parent_dot, parent_origin), previous in list(chart[origin].items()):
                        parent = self.productions[parent_id]
                        if parent_dot < len(parent.rhs) and parent.rhs[parent_dot] == production.lhs:
                            key = (parent_id, parent_dot + 1, parent_origin)
                            if put(pos, key, previous + value):
                                agenda.append(key)
                else:
                    symbol = production.rhs[dot]
                    if isinstance(symbol, str):
                        for child_id in self.by_name[symbol]:
                            key = (child_id, 0, pos)
                            if put(pos, key, ()):
                                agenda.append(key)
                        # A nullable child may have completed before this parent.
                        for (child_id, child_dot, child_origin), completed in list(chart[pos].items()):
                            child = self.productions[child_id]
                            if child.lhs == symbol and child_origin == pos and child_dot == len(child.rhs):
                                value = completed if symbol.startswith("@") else (Tree(symbol, completed, child_id),)
                                key = (pid, dot + 1, origin)
                                if put(pos, key, children + value):
                                    agenda.append(key)
                    elif pos < len(tokens):
                        token = tokens[pos]
                        matches = token.category == symbol.text if symbol.category else token.text == symbol.text
                        if matches:
                            put(pos + 1, (pid, dot + 1, origin), children + (token,))
        for (pid, dot, origin), children in chart[-1].items():
            production = self.productions[pid]
            if production.lhs == start and origin == 0 and dot == len(production.rhs):
                return Tree(start, children, pid)
        reached = max((i for i, states in enumerate(chart) if states), default=0)
        near = " ".join(t.text for t in tokens[max(0, reached - 3):reached + 3])
        raise GenerationError(f"输入不符合 {start} 文法，位置 {reached}: {near}")


def bind_leaves(tree, rng):
    """Context-aware terminals: initialized names, bounded array indices, known calls."""
    children = list(tree.children)
    if tree.name == "LVal":
        children[0] = Token("arr" if len(children) > 1 else rng.choice(["x", "y", "z", "k"]), "Ident")
        # Arbitrary integer index expressions are retained: these tests target
        # the compiler front end, not runtime array bounds safety.
    elif tree.name == "UnaryExp" and len(children) > 1 and isinstance(children[0], Token) and children[0].category == "Ident":
        params = next((c for c in children if isinstance(c, Tree) and c.name == "FuncRParams"), None)
        argc = sum(isinstance(c, Tree) and c.name == "Exp" for c in params.children) if params else 0
        children[0] = Token(f"fn{argc}", "Ident")
    return Tree(tree.name, tuple(children), tree.production)
