"""Import user-supplied official goldens and semantics-preserving variants.

Archives are read as data, never extracted or executed. Compiler output is never
used to construct a reference. Renaming and line-prefix transforms update only
those official answers whose transformation is mathematically determined.
"""
from __future__ import annotations

import hashlib
import re
from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile

from .grammar import GenerationError
from .oracle import KEYWORDS

RUNTIME = {"get_int", "get_char", "get_string", "put_int", "put_char", "put_string", "printf", "main"}
LEXEMES = re.compile(r'''//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b[A-Za-z_]\w*\b''')


def rename_source(source, mapping):
    return LEXEMES.sub(lambda m: mapping.get(m.group(), m.group()), source)


def rename_trace(trace, mapping):
    return "\n".join("IDENFR " + mapping.get(line[7:], line[7:]) if line.startswith("IDENFR ") else line
                     for line in trace.splitlines())


def rename_symbols(symbols, mapping):
    rows = []
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) != 3:
            raise GenerationError(f"官方 symbol.txt 格式错误: {line!r}")
        fields[1] = mapping.get(fields[1], fields[1])
        rows.append(" ".join(fields))
    return "\n".join(rows)


def shift_errors(errors, offset):
    rows = []
    for line in errors.splitlines():
        fields = line.split()
        if len(fields) != 2 or not fields[0].isdigit():
            raise GenerationError(f"官方 error.txt 格式错误: {line!r}")
        rows.append(f"{int(fields[0]) + offset} {fields[1]}")
    return "\n".join(rows)


def load_archives(paths, variants=1, seed=2026):
    cases = []
    for archive_index, path in enumerate(paths):
        archive = Path(path)
        try:
            with ZipFile(archive) as zip_file:
                members = {info.filename: info for info in zip_file.infolist() if not info.is_dir()}
                sources = sorted((n for n in members if PurePosixPath(n).name == "testfile.txt"),
                                 key=lambda n: (int(re.search(r"testcase(\d+)", n).group(1)) if re.search(r"testcase(\d+)", n) else 0, n))
                if not sources:
                    raise GenerationError(f"压缩包内未找到 testfile.txt: {archive}")

                def read(name):
                    if members[name].file_size > 20_000_000:
                        raise GenerationError(f"官方文件超过读取上限: {name}")
                    return zip_file.read(name).decode("utf-8-sig").replace("\r\n", "\n")

                for member in sources:
                    folder = str(PurePosixPath(member).parent)
                    source = read(member)
                    expected = {}
                    for name in ("lexer.txt", "parser.txt", "symbol.txt", "error.txt"):
                        full = folder + "/" + name
                        if full in members:
                            expected[name] = read(full).rstrip("\n")
                    if "symbol.txt" in expected:
                        expected.setdefault("error.txt", "")
                        expected["lexer.errors.txt"] = ""
                        expected["parser.errors.txt"] = ""
                    if "error.txt" not in expected:
                        raise GenerationError(f"官方用例既无符号答案也无错误答案: {member}")
                    auxiliary = {}
                    for original, output in (("in.txt", "input.txt"), ("ans.txt", "output.txt")):
                        if folder + "/" + original in members:
                            auxiliary[output] = read(folder + "/" + original)
                    names = {m.group() for m in LEXEMES.finditer(source)
                             if re.fullmatch(r"[A-Za-z_]\w*", m.group()) and m.group() not in KEYWORDS and m.group() not in RUNTIME}
                    digest = hashlib.sha256(zip_file.read(member)).hexdigest()
                    for variant in range(variants + 1):
                        mapping = {name: f"tg{abs(seed)}_{archive_index}_{variant}_{name}" for name in sorted(names)} if variant else {}
                        prefix = (f"// official metamorphic variant {variant}\n" + "\n" * variant) if variant else ""
                        offset = prefix.count("\n")
                        transformed = {}
                        for name, content in expected.items():
                            if name in ("lexer.txt", "parser.txt"):
                                content = rename_trace(content, mapping)
                            elif name == "symbol.txt":
                                content = rename_symbols(content, mapping)
                            elif name.endswith("errors.txt") or name == "error.txt":
                                content = shift_errors(content, offset)
                            transformed[name] = content
                        final_source = prefix + rename_source(source, mapping)
                        error_codes = sorted({line.split()[1] for line in transformed["error.txt"].splitlines()})
                        cases.append({"source": final_source, "expected": transformed, "auxiliary": auxiliary,
                                      "metadata": {"mode": "program", "kind": "official-variant" if variant else "official",
                                                   "seed": seed, "provenance": {"archive": archive.name, "member": member,
                                                                                "source_sha256": digest, "variant": variant},
                                                   "oracle": "official-golden" if not variant else "official-golden-transformed",
                                                   "tags": ["official", "metamorphic" if variant else "original"] + ["error:" + c for c in error_codes],
                                                   "assert_only_supplied_stages": True,
                                                   "stage_outputs": {"semantic": "error.txt" if error_codes else "symbol.txt"},
                                                   "productions_used": {}}})
        except (BadZipFile, UnicodeError) as error:
            raise GenerationError(f"无法读取官方压缩包 {archive}: {error}") from error
    return cases
