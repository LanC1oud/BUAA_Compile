from __future__ import annotations

import argparse
import difflib
import json
import random
import subprocess
import sys
from collections import Counter
from pathlib import Path

from .grammar import GenerationError, Grammar, GrammarError, Term, Token, Tree
from .oracle import (TOKEN_CODES, SemanticModel, UnsupportedSemantics,
                     lexer_output, parser_output, tokenize)
from .official import load_archives

PACKAGE = Path(__file__).resolve().parent
ROOT = PACKAGE.parent


def json_text(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig")) if path else {}


def scaffold(fragment, statements, rng, max_args):
    lines = [f"const int k = {rng.randrange(1, 10)};"]
    for argc in range(max_args + 1):
        params = ", ".join(f"int p{i}" for i in range(argc))
        result = " + ".join(f"p{i}" for i in range(argc)) or "1"
        lines.append(f"int fn{argc}({params}) {{ return {result}; }}")
    lines.extend(["int main() {", f"    int x = {rng.randrange(1, 10)}, y = {rng.randrange(1, 10)}, z = {rng.randrange(1, 10)};",
                  "    int arr[4] = {1, 2, 3, 4};"])
    if statements:
        # Arbitrary generated break/continue are legal inside this bounded context.
        lines.extend(["    while (0) {", "        " + fragment, "    }"])
    else:
        lines.extend(["    if (" + fragment + ") {", "        x = x + 1;", "    } else {", "        y = y - 1;", "    }"])
    lines.extend(["    return 0;", "}"])
    return "\n".join(lines) + "\n"


NEGATIVES = {
    "c": "missing_name = 1;",
    "h": "k = 1;",
    "e": "x = 'a';",
    "m": "break;",
    "d": "fn0(1);",
    "l": 'printf("%d");',
    "i": "x = 1;",
    "j": 'printf("ok");',
    "k": "arr[0] = 1;",
}
RECOVERY = {"i": ";", "j": ")", "k": "]"}


def generate_case(grammar, options, codes, semantics, index, alternative):
    # Per-case seed is recorded, so a case can be regenerated independently.
    seed = options.seed + index
    rng = random.Random(seed)
    rejected = Counter()
    for _ in range(options.attempts):
        try:
            if options.mode == "syntax":
                tree = grammar.derive(options.focus, rng, options.depth, options.repeat,
                                      options.focus, alternative)
                source = " ".join(t.text for t in tree.tokens()) + "\n"
                tokens = tokenize(source, codes)
                tree = grammar.parse(tokens, options.focus)
                symbols = None
            else:
                start = "Stmt" if options.focus in ("Stmt", "Block", "BlockItem", "Decl", "VarDecl", "ConstDecl") else "Cond"
                fragment = grammar.derive(start, rng, options.depth, options.repeat,
                                          options.focus, alternative, program=True)
                source = scaffold(" ".join(t.text for t in fragment.tokens()), start == "Stmt", rng, options.repeat + 1)
                tokens = tokenize(source, codes)
                tree = grammar.parse(tokens)
                symbols = SemanticModel(semantics.get("operators"), semantics.get("transparent", [])).analyze(tree)
            break
        except UnsupportedSemantics:
            raise
        except (GenerationError, RecursionError) as error:
            rejected[str(error)] += 1
    else:
        reasons = "; ".join(f"{k}（{v} 次）" for k, v in rejected.most_common(3))
        raise GenerationError(f"{options.focus} 候选式 {alternative + 1} 在 {options.attempts} 次尝试后仍未生成合法用例: {reasons}")
    error_text = ""
    parser_errors = ""
    if options.negative:
        code = list(NEGATIVES)[index % len(NEGATIVES)] if options.negative == "mixed" else options.negative
        lines = source.splitlines()
        return_line = len(lines) - 2
        lines.insert(return_line, "    " + NEGATIVES[code])
        error_text = f"{return_line + 1} {code}"
        complete_source = "\n".join(lines) + "\n"
        complete_tokens = tokenize(complete_source, codes)
        tree = grammar.parse(complete_tokens)
        if code in RECOVERY:
            # Derive the recovery trace independently: a missing delimiter is not
            # consumed/emitted, while its enclosing grammar nodes still finish.
            omitted = RECOVERY[code]
            lines[return_line] = lines[return_line].replace(omitted, "", 1)

            def remove_delimiter(node):
                return Tree(node.name, tuple(remove_delimiter(c) if isinstance(c, Tree) else c
                                             for c in node.children if not (isinstance(c, Token)
                                             and c.line == return_line + 1 and c.text == omitted)), node.production)

            tree = remove_delimiter(tree)
            parser_errors = error_text
        source = "\n".join(lines) + "\n"
        tokens = tokenize(source, codes)
        symbols = None
    expected = {"lexer.txt": lexer_output(tokens, codes), "parser.txt": parser_output(tree, codes, options.hide)}
    if options.mode == "program":
        expected["error.txt"] = error_text
        expected["lexer.errors.txt"] = ""
        expected["parser.errors.txt"] = parser_errors
        if symbols is not None:
            expected["symbol.txt"] = symbols
    usage = Counter(str(n.production) for n in tree.nodes())
    return {"source": source, "expected": expected,
            "metadata": {"seed": seed, "focus": options.focus, "alternative": alternative + 1,
                         "mode": options.mode, "semantic_oracle": options.mode == "program",
                         "stage_outputs": {"lexer": "lexer.txt", "syntax": "parser.errors.txt" if parser_errors else "parser.txt",
                                           "semantic": "error.txt" if error_text else "symbol.txt"} if options.mode == "program" else {},
                         "negative": options.negative, "rejections": dict(rejected), "productions_used": dict(usage),
                         "terminals_used": dict(Counter(t.category or t.text for t in tokens))}}


def write_batch(output, cases, grammar, options):
    output.mkdir(parents=True, exist_ok=True)
    coverage = Counter()
    for index, case in enumerate(cases):
        path = output / f"case_{index + 1:04d}"
        (path / "expected").mkdir(parents=True)
        (path / "testfile.txt").write_text(case["source"], encoding="utf-8", newline="\n")
        for name, content in case["expected"].items():
            (path / "expected" / name).write_text(content, encoding="utf-8", newline="\n")
        (path / "case.json").write_text(json_text(case["metadata"]), encoding="utf-8")
        for name, content in case.get("auxiliary", {}).items():
            (path / name).write_text(content, encoding="utf-8", newline="\n")
        if "alternative" in case["metadata"]:
            coverage[str(case["metadata"]["alternative"])] += 1
    (output / "effective.ebnf").write_text("\n".join(f"{k} -> {v}" for k, v in grammar.rules.items()) + "\n", encoding="utf-8")
    usage = Counter()
    tags = Counter()
    terminals = Counter()
    for case in cases:
        usage.update(case["metadata"].get("productions_used", {}))
        tags.update(case["metadata"].get("tags", []))
        terminals.update(case["metadata"].get("terminals_used", {}))
    real_ids = {str(i) for i, p in enumerate(grammar.productions) if not p.lhs.startswith("@")}
    actual_focus = {str(i + 1): usage[str(pid)] for i, pid in enumerate(grammar.by_name[options.focus]) if usage[str(pid)]}
    uncovered = [i + 1 for i, pid in enumerate(grammar.by_name[options.focus]) if not usage[str(pid)]]
    all_terminals = {s.text for p in grammar.productions for s in p.rhs if isinstance(s, Term)}
    manifest = {"version": 2, "seed": options.seed, "count": len(cases), "mode": options.mode,
                "focus": options.focus, "candidate_coverage": dict(coverage), "uncovered_candidates": uncovered,
                "coverage": {"scheduled_focus_candidates": dict(coverage), "tags": dict(tags),
                             "actual_focus_candidates": actual_focus,
                             "actual_production_uses": dict(usage),
                             "actual_terminal_uses": dict(terminals),
                             "uncovered_terminals": sorted(all_terminals - terminals.keys()),
                             "covered_productions": sorted(real_ids & usage.keys(), key=int),
                             "uncovered_productions": sorted(real_ids - usage.keys(), key=int),
                             "measured_cases": sum(bool(c["metadata"].get("productions_used")) for c in cases),
                             "note": "产生式覆盖来自独立参考树，含标明的错误恢复树；合成 EBNF 组不单独计数，内部符号另查终结符覆盖；官方原例/变体未重解析，不计入覆盖；不代表语义或路径完备"},
                "boundary_limitations": getattr(options, "boundary_limitations", []),
                "options": {k: v for k, v in vars(options).items() if k not in ("out", "jdk")},
                "productions": {str(i): {"lhs": p.lhs, "rhs": [
                    {"terminal": s.text, "category": s.category} if isinstance(s, Term) else s for s in p.rhs]}
                    for i, p in enumerate(grammar.productions)}}
    (output / "manifest.json").write_text(json_text(manifest), encoding="utf-8")
    return uncovered


def check_batch(output, jdk):
    """Use the real compiler only on the actual side of a comparison."""
    manifest = read_json(output / "manifest.json")
    samples = sorted(p for p in output.glob("case_*") if p.is_dir())
    if not samples or len(samples) != manifest.get("count") or any(not (p / "testfile.txt").is_file() for p in samples):
        raise GenerationError("批次文件缺失或数量与 manifest.json 不一致，停止校验")
    build = ROOT / "build" / "testgen-check"
    build.mkdir(parents=True, exist_ok=True)
    java_bin = Path(jdk) / "bin" if jdk else None
    suffix = ".exe" if sys.platform == "win32" else ""
    javac = str(java_bin / ("javac" + suffix)) if java_bin else "javac"
    java = str(java_bin / ("java" + suffix)) if java_bin else "java"
    sources = sorted((ROOT / "src").rglob("*.java")) + [PACKAGE / "java" / "CompilerProbe.java"]
    argfile = build / "sources.args"
    argfile.write_text("\n".join('"' + p.as_posix() + '"' for p in sources), encoding="utf-8")
    try:
        compiled = subprocess.run([javac, "-J-Duser.language=en", "-J-Duser.country=US", "-encoding", "UTF-8", "-d", str(build), "@" + str(argfile)],
                                  capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise GenerationError(f"无法构建校验桥接程序: {error}；请用 --jdk 指定 JDK 17+") from error
    if compiled.returncode:
        raise GenerationError("编译器构建失败（生成的测试文件已保留）:\n" + compiled.stdout + compiled.stderr)
    try:
        run = subprocess.run([java, "-Dfile.encoding=UTF-8", "-cp", str(build), "CompilerProbe", str(output)],
                             capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise GenerationError(f"运行当前编译器失败: {error}；测试文件已保留") from error
    failures = []
    results = []
    for case in samples:
        first_failure = len(failures)
        expected = case / "expected"
        actual = case / "actual"
        for name in ("lexer.txt", "parser.txt", "symbol.txt", "error.txt"):
            (case / (name + ".diff")).unlink(missing_ok=True)
        comparisons = [(name, name) for name in ("lexer.txt", "parser.txt", "symbol.txt") if (expected / name).exists()]
        if (expected / "error.txt").exists():
            comparisons.append(("error.txt", "semantic.errors.txt"))
        for stage in ("lexer", "parser"):
            error_path = actual / f"{stage}.errors.txt"
            expected_errors = expected / f"{stage}.errors.txt"
            if not expected_errors.exists():
                continue
            reference_errors = expected_errors.read_text(encoding="utf-8").strip()
            actual_errors = error_path.read_text(encoding="utf-8").strip() if error_path.exists() else "<missing file>"
            if actual_errors != reference_errors:
                failures.append({"case": case.name, "file": error_path.name, "reason": "该阶段错误信息与预期不一致"})
        metadata = read_json(case / "case.json")
        status = (actual / "status.txt").read_text(encoding="utf-8").splitlines() if (actual / "status.txt").exists() else []
        intentional_rejection = metadata.get("expected_outcome") == "syntax-rejection"
        acceptable_rejection = intentional_rejection and status == ["parser", "java.lang.IllegalStateException"]
        if intentional_rejection and not acceptable_rejection:
            parser_errors = actual / "parser.errors.txt"
            lexer_errors = actual / "lexer.errors.txt"
            lex_lines = set(lexer_errors.read_text(encoding="utf-8").splitlines()) if lexer_errors.exists() else set()
            parse_lines = set(parser_errors.read_text(encoding="utf-8").splitlines()) if parser_errors.exists() else set()
            diagnosed = status == ["complete"] and bool(parse_lines - lex_lines)
            if not diagnosed:
                failures.append({"case": case.name, "file": "status.txt", "reason": "非法文法输入未得到可识别的语法拒绝；任意崩溃不算拒绝成功"})
        if (actual / "crash.txt").exists() and not acceptable_rejection:
            failures.append({"case": case.name, "file": "crash.txt", "reason": (actual / "crash.txt").read_text(encoding="utf-8")})
        for wanted, got in comparisons:
            reference = (expected / wanted).read_text(encoding="utf-8").rstrip("\r\n")
            observed = (actual / got).read_text(encoding="utf-8").rstrip("\r\n") if (actual / got).exists() else "<missing file>"
            if reference != observed:
                diff = "".join(difflib.unified_diff([s + "\n" for s in reference.splitlines()], [s + "\n" for s in observed.splitlines()],
                                                    fromfile="expected/" + wanted, tofile="actual/" + got))
                (case / (wanted + ".diff")).write_text(diff, encoding="utf-8")
                failures.append({"case": case.name, "file": wanted, "reason": "与独立预期不一致"})
        identity = metadata.get("name") or metadata.get("provenance", {}).get("member") or metadata.get("focus")
        for failure in failures[first_failure:]:
            failure["name"] = identity
            failure["kind"] = metadata.get("kind", "random")
        results.append({"case": case.name, "name": identity, "kind": metadata.get("kind", "random"),
                        "passed": first_failure == len(failures), "variant": metadata.get("provenance", {}).get("variant")})
    report = {"passed": not failures and run.returncode == 0, "failures": failures,
              "summary": {"total": len(results), "passed": sum(c["passed"] for c in results),
                          "failed": sum(not c["passed"] for c in results)}, "cases": results,
              "compiler_exit": run.returncode, "stdout": run.stdout, "stderr": run.stderr}
    (output / "check-report.json").write_text(json_text(report), encoding="utf-8")
    print(f"校验：{'通过' if report['passed'] else '失败'}，{len(failures)} 项差异。报告：{output / 'check-report.json'}")
    return report["passed"]


def main(argv=None):
    parser = argparse.ArgumentParser(description="按 EBNF 生成可复现的 SysY 批量测试及独立前端预期结果")
    parser.add_argument("--rule", action="append", default=[], help="新增文法，格式 Name -> ...；可重复")
    parser.add_argument("--rules", help="UTF-8 新增文法文件")
    parser.add_argument("--base", default=str(PACKAGE / "sysy.ebnf"), help="基础 EBNF 文件")
    parser.add_argument("--replace", action="store_true", help="替换同名规则；默认合并并去重")
    parser.add_argument("--focus", "--start", dest="focus", help="重点覆盖的非终结符；默认第一个新增规则，或 EqExp")
    parser.add_argument("--mode", choices=["program", "syntax"], default="program")
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--suite", choices=["random", "boundary", "mixed", "official"], default="random",
                        help="random 随机；boundary 边界目录；mixed 两者；official 仅导入官方包")
    parser.add_argument("--scale", type=int, default=1, help="边界压力倍率 1～4（链长、嵌套、形参数）")
    parser.add_argument("--official-zip", action="append", default=[], help="导入官方 ZIP，可重复；可与其它 suite 合并")
    parser.add_argument("--official-variants", type=int, default=1, help="每个官方原例附加的重命名/空行变体数 0～10")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--depth", type=int, default=18)
    parser.add_argument("--repeat", type=int, default=2, help="每个 EBNF 重复项最多重复几次（0～3）")
    parser.add_argument("--attempts", type=int, default=300, help="每个用例的最大生成尝试次数")
    parser.add_argument("--alternative", type=int, help="只覆盖指定候选式，从 1 开始；默认轮流覆盖")
    parser.add_argument("--tokens", help="自定义终结符到 token 输出名的 JSON 映射")
    parser.add_argument("--semantics", help="自定义运算符类型契约 JSON：operators / transparent")
    parser.add_argument("--hide", action="append", default=[], help="parser.txt 不输出的额外非终结符")
    parser.add_argument("--negative", choices=list(NEGATIVES) + ["mixed"], help="在合法程序中注入一个已知语义错误")
    parser.add_argument("--out", default="build/testgen-output", help="新的或空的输出目录；不覆盖已有文件")
    parser.add_argument("--check", action="store_true", help="生成后与当前 Java 编译器比较")
    parser.add_argument("--check-only", action="store_true", help="校验 --out 中已生成的完整程序批次")
    parser.add_argument("--jdk", help="--check 使用的 JDK 17+ 安装目录")
    options = parser.parse_args(argv)
    try:
        output = Path(options.out).resolve()
        if options.check_only:
            manifest = read_json(output / "manifest.json")
            if not manifest or manifest.get("mode") != "program":
                raise GenerationError("--check-only 需要包含 manifest.json 的完整程序批次")
            return 0 if check_batch(output, options.jdk) else 1
        if not (1 <= options.count <= 10000 and 1 <= options.depth <= 60 and 0 <= options.repeat <= 3 and options.attempts > 0):
            raise GenerationError("参数范围：count=1～10000，depth=1～60，repeat=0～3，attempts>0")
        if not (1 <= options.scale <= 4 and 0 <= options.official_variants <= 10):
            raise GenerationError("参数范围：scale=1～4，official-variants=0～10")
        if options.suite == "official" and not options.official_zip:
            raise GenerationError("--suite official 需要至少一个 --official-zip")
        if options.mode == "syntax" and (options.suite != "random" or options.official_zip):
            raise GenerationError("边界和官方套件需要完整程序模式")
        if options.negative and options.suite not in ("random", "mixed"):
            raise GenerationError("--negative 只应用于 random/mixed 中的随机部分")
        if options.mode == "syntax" and (options.negative or options.check):
            raise GenerationError("片段模式不提供语义预期或完整编译器校验；请使用 --mode program")
        if output.exists() and (not output.is_dir() or any(output.iterdir())):
            raise GenerationError(f"输出目录不为空，拒绝覆盖: {output}")
        additions = "\n".join(options.rule)
        if options.rules:
            additions += "\n" + Path(options.rules).read_text(encoding="utf-8-sig")
        grammar = Grammar(Path(options.base).read_text(encoding="utf-8-sig"), additions, options.replace)
        options.focus = options.focus or (grammar.focus_rules[0] if grammar.focus_rules else "EqExp")
        if options.focus not in grammar.by_name:
            raise GenerationError(f"未知非终结符 {options.focus}")
        if options.alternative is not None and not 1 <= options.alternative <= len(grammar.by_name[options.focus]):
            raise GenerationError("--alternative 超出目标规则候选式范围")
        codes = dict(TOKEN_CODES)
        custom_codes = read_json(options.tokens)
        if not isinstance(custom_codes, dict) or any(not isinstance(k, str) or not isinstance(v, str) or not re_token(v) for k, v in custom_codes.items()):
            raise GenerationError("--tokens 需要 JSON 对象，值为 token 输出名（字母数字下划线）")
        codes.update(custom_codes)
        for production in grammar.productions:
            for symbol in production.rhs:
                if isinstance(symbol, Term) and not symbol.category and symbol.text not in codes:
                    raise GenerationError(f"新终结符 {symbol.text!r} 缺少 token 输出名，请用 --tokens 指定")
        semantics = read_json(options.semantics)
        if (not isinstance(semantics, dict) or not isinstance(semantics.get("operators", {}), dict)
                or any(v not in ("same", "int") for v in semantics.get("operators", {}).values())
                or not isinstance(semantics.get("transparent", []), list)
                or any(not isinstance(n, str) for n in semantics.get("transparent", []))):
            raise GenerationError("--semantics 的 operators 应映射到 same 或 int")
        cases = []
        if options.suite in ("random", "mixed"):
            for index in range(options.count):
                alternative = (options.alternative - 1) if options.alternative else index % len(grammar.by_name[options.focus])
                cases.append(generate_case(grammar, options, codes, semantics, index, alternative))
        if options.suite in ("boundary", "mixed"):
            from .boundaries import boundary_cases
            boundary, limitations = boundary_cases(grammar, codes, semantics, options.scale, options.seed, options.hide)
            cases.extend(boundary)
            options.boundary_limitations = limitations
        cases.extend(load_archives(options.official_zip, options.official_variants, options.seed))
        uncovered = write_batch(output, cases, grammar, options)
        print(f"已生成 {len(cases)} 个用例：{output}")
        print(f"重点规则：{options.focus}；解析树未覆盖候选式：{uncovered or '无'}；seed={options.seed}")
        if options.suite == "official":
            print("官方用例直接使用所附答案，不重解析；产生式覆盖未测量。")
        for limitation in getattr(options, "boundary_limitations", [])[:-4]:
            print("覆盖限制：" + limitation)
        if options.mode == "syntax":
            print("片段模式：仅提供 lexer/parser 预期，不声明符号与语义合法性。")
        return 0 if not options.check or check_batch(output, options.jdk) else 1
    except (GenerationError, GrammarError, OSError, ValueError) as error:
        print(f"testgen: {error}", file=sys.stderr)
        return 2


def re_token(value):
    return bool(value) and all(c.isascii() and (c.isalnum() or c == "_") for c in value)


if __name__ == "__main__":
    sys.exit(main())
