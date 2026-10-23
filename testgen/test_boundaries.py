"""Reference-oracle regression tests; no compiler is used to build the goldens."""
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from .boundaries import baseline_fixtures, boundary_cases, nearest_else
from .grammar import Grammar
from .official import load_archives, rename_source, shift_errors
from .oracle import TOKEN_CODES, tokenize


BASE = (Path(__file__).parent / "sysy.ebnf").read_text(encoding="utf-8")


class OfficialTests(unittest.TestCase):
    def test_rename_preserves_literals_comments_and_identifier_boundaries(self):
        source = "x=xx; // x\n/* x */ printf(\"x\",'x'); x=1;"
        self.assertEqual(rename_source(source, {"x": "renamed"}),
                         "renamed=xx; // x\n/* x */ printf(\"x\",'x'); renamed=1;")
        self.assertEqual(shift_errors("1 c\n10 e", 3), "4 c\n13 e")

    def test_archive_original_and_variant_keep_official_answers(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "official.zip"
            with ZipFile(path, "w") as archive:
                archive.writestr("root/testcase1/testfile.txt", "int main(){\nmissing=1;\nreturn 0;\n}\n")
                archive.writestr("root/testcase1/error.txt", "2 c\n")
                archive.writestr("../../must-not-extract.txt", "ignored")
            cases = load_archives([path], variants=2, seed=-42)
            self.assertEqual(len(cases), 3)
            self.assertEqual(cases[0]["expected"], {"error.txt": "2 c"})
            self.assertEqual(cases[1]["expected"], {"error.txt": "4 c"})
            self.assertEqual(cases[2]["expected"], {"error.txt": "5 c"})
            self.assertIn("tg42_0_1_missing=1", cases[1]["source"])
            self.assertEqual(len(cases[0]["metadata"]["provenance"]["source_sha256"]), 64)
            self.assertEqual([p.name for p in Path(temp).iterdir()], ["official.zip"])

    def test_valid_official_variant_updates_all_names_but_not_io(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "official.zip"
            with ZipFile(path, "w") as archive:
                for name, value in {"testfile.txt": 'int f(int x){return x;} int main(){printf("x");return 0;}',
                                    "lexer.txt": "IDENFR f\nIDENFR x\nSTRCON \"x\"",
                                    "parser.txt": "IDENFR x\n<LVal>",
                                    "symbol.txt": "1 f IntFunc\n2 x Int", "in.txt": "x\n", "ans.txt": "x\n"}.items():
                    archive.writestr("testcase1/" + name, value)
            original, variant = load_archives([path])
            self.assertEqual(variant["expected"]["parser.txt"], "IDENFR tg2026_0_1_x\n<LVal>")
            self.assertEqual(variant["expected"]["symbol.txt"], "1 tg2026_0_1_f IntFunc\n2 tg2026_0_1_x Int")
            self.assertIn('STRCON "x"', variant["expected"]["lexer.txt"])
            self.assertEqual(original["auxiliary"], variant["auxiliary"])


class BoundaryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.grammar = Grammar(BASE)
        cls.cases, cls.limitations = boundary_cases(cls.grammar, TOKEN_CODES, {})
        cls.by_name = {case["metadata"]["name"]: case for case in cls.cases}

    def test_baseline_covers_all_fourteen_error_categories(self):
        codes = {line.split()[1] for case in self.cases for line in case["expected"]["error.txt"].splitlines()}
        self.assertEqual(codes, set("abcdefghijklmn"))
        self.assertEqual(len(self.cases), 39)
        self.assertIn("2147483647", self.by_name["integer-boundaries"]["source"])
        self.assertIn("(-2147483647-1)", self.by_name["integer-boundaries"]["source"])
        self.assertFalse(self.by_name["eof-without-newline"]["source"].endswith("\n"))

    def test_symbol_inventory_models_array_parameters_and_shadowing(self):
        symbols = self.by_name["array-parameters"]["expected"]["symbol.txt"]
        self.assertEqual(symbols, "1 head IntFunc\n1 first CharFunc\n2 a IntArray\n3 s CharArray\n4 a IntArray\n4 s CharArray")
        symbols = self.by_name["scope-shadowing"]["expected"]["symbol.txt"]
        self.assertEqual(symbols, "1 x Int\n2 x Int\n3 x Char\n4 x Int")

    def test_course_return_regression_and_recovery_phase_errors(self):
        expected = self.by_name["return-errors"]["expected"]
        self.assertEqual(expected["error.txt"], "2 f\n6 g\n8 e")
        recovery = self.by_name["recovery-three-delimiters"]["expected"]
        self.assertEqual(recovery["lexer.errors.txt"], "")
        self.assertEqual([line.split()[1] for line in recovery["parser.errors.txt"].splitlines()], ["i", "j", "k"])
        emitted = [line for line in recovery["parser.txt"].splitlines() if not line.startswith("<")]
        self.assertEqual(emitted, recovery["lexer.txt"].splitlines())
        priority = self.by_name["same-line-priority"]["expected"]["error.txt"]
        self.assertEqual(priority.split()[1], "i")

    def test_dangling_else_binds_to_nearest_if(self):
        tree = nearest_else(self.grammar.parse(tokenize("int main(){if(1)if(0);else;return 0;}", TOKEN_CODES)))
        statements = [n for n in tree.nodes() if n.name == "Stmt"]
        self.assertEqual(len(statements[0].children), 5)
        self.assertEqual(len(statements[1].children), 7)

    def test_seed_only_reorders_exhaustive_catalog_and_scale_increases_limits(self):
        other, _ = boundary_cases(self.grammar, TOKEN_CODES, {}, seed=7)
        self.assertEqual({c["metadata"]["name"] for c in other}, set(self.by_name))
        self.assertNotEqual([c["metadata"]["name"] for c in other], [c["metadata"]["name"] for c in self.cases])
        low = {f.name: f for f in baseline_fixtures(1)}
        high = {f.name: f for f in baseline_fixtures(4)}
        self.assertEqual(high["long-expression"].source.count("+"), 255)
        self.assertEqual(low["long-expression"].source.count("+"), 63)
        self.assertIn("p63", high["many-parameters"].source)

    def test_increment_contract_covers_targets_contexts_and_rejects(self):
        grammar = Grammar(BASE, "UnaryExp -> '++' LVal | LVal '++'")
        cases, _ = boundary_cases(grammar, TOKEN_CODES, {})
        by_name = {c["metadata"]["name"]: c for c in cases}
        for form in ("pre", "post"):
            self.assertTrue(by_name[f"inc-{form}-const-array"]["expected"]["error.txt"].endswith(" h"))
            self.assertTrue(by_name[f"inc-{form}-const-context"]["expected"]["error.txt"].endswith(" o"))
            self.assertTrue(by_name[f"inc-{form}-array-size"]["expected"]["error.txt"].endswith(" o"))
            self.assertEqual(by_name[f"inc-{form}-target-char-element"]["expected"]["error.txt"], "")
            self.assertIn(f"inc-{form}-call", by_name)
            self.assertIn(f"inc-{form}-return", by_name)
        rejected = [c for c in cases if c["metadata"].get("expected_outcome") == "syntax-rejection"]
        self.assertGreaterEqual(len(rejected), 15)
        for case in rejected:
            self.assertEqual(set(case["expected"]), {"lexer.txt"})
            self.assertIn("INC ++", case["expected"]["lexer.txt"])

    def test_only_generate_supported_increment_forms_and_expose_unknown_shapes(self):
        grammar = Grammar(BASE, "UnaryExp -> '++' LVal")
        cases, _ = boundary_cases(grammar, TOKEN_CODES, {})
        names = {c["metadata"]["name"] for c in cases}
        self.assertIn("inc-pre-call", names)
        self.assertFalse(any(n.startswith("inc-post") for n in names))
        grammar = Grammar(BASE, "UnaryExp -> '@' UnaryExp")
        _, limitations = boundary_cases(grammar, {**TOKEN_CODES, "@": "AT"}, {})
        self.assertTrue(any("'@'" in line and "专属边界策略" in line for line in limitations))

    def test_decrement_reuses_lvalue_contract_without_adding_unrequested_rules(self):
        grammar = Grammar(BASE, "UnaryExp -> '--' LVal | LVal '--'")
        cases, _ = boundary_cases(grammar, TOKEN_CODES, {})
        names = {c["metadata"]["name"] for c in cases}
        self.assertIn("dec-pre-call", names)
        self.assertIn("dec-post-call", names)
        self.assertFalse(any(n.startswith("inc-") for n in names))

    def test_binary_contracts_in_expression_and_condition_contexts(self):
        grammar = Grammar(BASE, "UnaryExp -> UnaryExp '**' PrimaryExp")
        cases, _ = boundary_cases(grammar, {**TOKEN_CODES, "**": "POW"}, {"operators": {"**": "int"}})
        char_case = next(c for c in cases if c["metadata"]["name"] == "binary-**-char")
        self.assertIn("2 x Int", char_case["expected"]["symbol.txt"])
        grammar = Grammar(BASE, "EqExp -> EqExp '^' RelExp")
        cases, limitations = boundary_cases(grammar, {**TOKEN_CODES, "^": "XOR"}, {"operators": {"^": "int"}})
        char_case = next(c for c in cases if c["metadata"]["name"] == "binary-^-char")
        self.assertIn("if('a' ^ 'b')", char_case["source"])
        self.assertTrue(any("group" in item for item in limitations))


if __name__ == "__main__":
    unittest.main()
