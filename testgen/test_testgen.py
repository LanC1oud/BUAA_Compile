"""Run from repository root: python -m unittest testgen.test_testgen -v."""
import contextlib
import io
import json
import random
import tempfile
import unittest
from pathlib import Path

from .__main__ import main
from .grammar import GenerationError, Grammar, GrammarError, Token
from .oracle import TOKEN_CODES, lexer_output, parser_output, tokenize


class GrammarTests(unittest.TestCase):
    def test_comment_arrow_and_merge(self):
        grammar = Grammar("E -> IntConst", "// E → E '+' IntConst // addition\n| '(' E ')'")
        self.assertEqual(len(grammar.by_name["E"]), 3)
        tree = grammar.parse(tokenize("1 + 2 + 3", TOKEN_CODES), "E")
        self.assertEqual(parser_output(tree, TOKEN_CODES),
                         "INTCON 1\n<E>\nPLUS +\nINTCON 2\n<E>\nPLUS +\nINTCON 3\n<E>")

    def test_ebnf_optional_repeat_and_group(self):
        grammar = Grammar("S -> ['const'] IntConst {('+' | '-') IntConst}")
        for source in ("1", "const 1 - 2 + 3"):
            tree = grammar.parse(tokenize(source, TOKEN_CODES), "S")
            self.assertEqual([t.text for t in tree.tokens()], source.split())

    def test_nullable_child_already_completed(self):
        grammar = Grammar("S -> A A\nA -> ['+']")
        grammar.parse([], "S")
        grammar.parse(tokenize("+", TOKEN_CODES), "S")

    def test_indirect_left_recursion(self):
        grammar = Grammar("A -> B '+' IntConst | IntConst\nB -> A")
        tree = grammar.parse(tokenize("1 + 2 + 3", TOKEN_CODES), "A")
        self.assertEqual(len(list(tree.tokens())), 5)

    def test_no_unbounded_derivation(self):
        grammar = Grammar("S -> S '+' IntConst | IntConst")
        for seed in range(40):
            tree = grammar.derive("S", random.Random(seed), depth=5)
            self.assertLessEqual(len(list(tree.tokens())), 9)
            grammar.parse(list(tree.tokens()), "S")

    def test_repetition_cap(self):
        grammar = Grammar("S -> IntConst {'+' IntConst}")
        for repeat in range(3):
            for seed in range(30):
                tree = grammar.derive("S", random.Random(seed), depth=30, repeat=repeat)
                self.assertLessEqual(sum(t.text == "+" for t in tree.tokens()), repeat)

    def test_invalid_grammars(self):
        for source in ("A -> B", "A -> A", "A -> ('+'", "not a grammar", "A -> +"):
            with self.subTest(source=source), self.assertRaises(GrammarError):
                Grammar(source)

    def test_depth_failure_is_explained(self):
        grammar = Grammar("A -> B\nB -> IntConst")
        with self.assertRaises(GenerationError):
            grammar.derive("A", random.Random(0), depth=1)

    def test_comments_do_not_break_quoted_terminal(self):
        grammar = Grammar("S -> '//' IntConst // comment")
        grammar.parse([Token("//"), Token("1", "IntConst")], "S")

    def test_replace_and_deduplicate(self):
        grammar = Grammar("E -> IntConst", "E -> IntConst | '(' E ')'")
        self.assertEqual(len(grammar.by_name["E"]), 2)
        replaced = Grammar("E -> IntConst", "E -> CharConst", replace=True)
        self.assertEqual(len(replaced.by_name["E"]), 1)

    def test_maximal_munch_and_whitespace(self):
        tokens = tokenize("x+++y; x + + y; x+/* gap */+y;", TOKEN_CODES)
        self.assertEqual(lexer_output(tokens, TOKEN_CODES).splitlines()[:5],
                         ["IDENFR x", "INC ++", "PLUS +", "IDENFR y", "SEMICN ;"])
        self.assertEqual(sum(t.text == "++" for t in tokens), 1)


class BatchTests(unittest.TestCase):
    def run_cli(self, arguments):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return main(arguments)

    def test_reproducibility_and_reference_outputs(self):
        with tempfile.TemporaryDirectory() as temp:
            outputs = [Path(temp) / name for name in ("first", "second")]
            for output in outputs:
                self.assertEqual(self.run_cli(["--count", "6", "--seed", "42", "--out", str(output)]), 0)
            first = outputs[0]
            for file in first.rglob("*"):
                if file.is_file():
                    self.assertEqual(file.read_bytes(), (outputs[1] / file.relative_to(first)).read_bytes())
            manifest = json.loads((first / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["uncovered_candidates"], [])
            symbols = (first / "case_0001/expected/symbol.txt").read_text(encoding="utf-8")
            self.assertIn("6 arr IntArray", symbols)
            self.assertNotIn(" main ", symbols)

    def test_inc_rule_is_actually_exercised(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "inc"
            code = self.run_cli(["--rule", "UnaryExp -> '++' LVal | LVal '++'", "--alternative", "5",
                                 "--count", "3", "--out", str(output)])
            self.assertEqual(code, 0)
            for case in output.glob("case_*"):
                tokens = (case / "expected/lexer.txt").read_text(encoding="utf-8")
                self.assertIn("INC ++", tokens)
                self.assertIn("<UnaryExp>", (case / "expected/parser.txt").read_text(encoding="utf-8"))
                self.assertEqual((case / "expected/error.txt").read_text(), "")

    def test_new_operator_with_contract(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(__file__).parent / "examples"
            code = self.run_cli(["--rules", str(root / "power.ebnf"), "--replace", "--focus", "PowerExp",
                                 "--tokens", str(root / "power-tokens.json"),
                                 "--semantics", str(root / "power-semantics.json"), "--count", "2", "--out", temp])
            self.assertEqual(code, 0)
            self.assertIn("POW **", (Path(temp) / "case_0002/expected/lexer.txt").read_text(encoding="utf-8"))

    def test_syntax_only_has_no_fake_semantic_expectation(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertEqual(self.run_cli(["--mode", "syntax", "--rule", "Pair -> '(' IntConst ',' IntConst ')'",
                                           "--count", "1", "--out", temp]), 0)
            self.assertTrue((Path(temp) / "case_0001/expected/parser.txt").exists())
            self.assertFalse((Path(temp) / "case_0001/expected/error.txt").exists())

    def test_negative_oracle_line_number(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertEqual(self.run_cli(["--negative", "h", "--count", "1", "--out", temp]), 0)
            case = Path(temp) / "case_0001"
            source = (case / "testfile.txt").read_text(encoding="utf-8").splitlines()
            line = source.index("    k = 1;") + 1
            self.assertEqual((case / "expected/error.txt").read_text(), f"{line} h")
            self.assertFalse((case / "expected/symbol.txt").exists())

    def test_recovery_oracle_omits_only_missing_delimiter(self):
        for code, delimiter in {"i": ";", "j": ")", "k": "]"}.items():
            with self.subTest(code=code), tempfile.TemporaryDirectory() as temp:
                self.assertEqual(self.run_cli(["--negative", code, "--count", "1", "--out", temp]), 0)
                case = Path(temp) / "case_0001"
                stage_errors = (case / "expected/parser.errors.txt").read_text()
                self.assertTrue(stage_errors.endswith(" " + code))
                line = int(stage_errors.split()[0])
                source_line = (case / "testfile.txt").read_text(encoding="utf-8").splitlines()[line - 1]
                self.assertNotIn(delimiter, source_line)
                # The lexer token lines in the recovery trace match the actual
                # incomplete source exactly; enclosing nonterminals still finish.
                trace = (case / "expected/parser.txt").read_text(encoding="utf-8").splitlines()
                emitted = [line for line in trace if not line.startswith("<")]
                self.assertEqual(emitted, (case / "expected/lexer.txt").read_text(encoding="utf-8").splitlines())

    def test_protect_existing_files_and_reject_unknown_semantics(self):
        with tempfile.TemporaryDirectory() as temp:
            sentinel = Path(temp) / "keep.txt"
            sentinel.write_text("keep", encoding="utf-8")
            self.assertEqual(self.run_cli(["--out", temp]), 2)
            self.assertEqual(sentinel.read_text(), "keep")
        with tempfile.TemporaryDirectory() as temp:
            tokens = Path(temp) / "tokens.json"
            tokens.write_text('{"@": "AT"}', encoding="utf-8")
            output = Path(temp) / "result"
            code = self.run_cli(["--rule", "UnaryExp -> '@' UnaryExp", "--alternative", "5", "--tokens", str(tokens),
                                 "--out", str(output), "--count", "1"])
            self.assertEqual(code, 2)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
