"""Run unmodified public code-generation ZIP fixtures against the current compiler."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import difflib
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--zip', dest='archives', action='append', type=Path)
parser.add_argument('--jdk', type=Path, default=Path('C:/Program Files/Java/jdk-21'))
parser.add_argument('--llvm', type=Path, default=ROOT/'build/llvm12/Library/bin')
parser.add_argument('--runtime', type=Path, default=ROOT/'runtime/llvm12/libsysy.ll')
parser.add_argument('--out', type=Path, default=ROOT/'build/official-llvm')
parser.add_argument('--timeout', type=int, default=20)
args = parser.parse_args()
archives = args.archives or sorted((Path.home()/'Downloads').glob('2026代码生成作业公共测试程序库-[ABC].zip'))
assert len(archives) == 3, f'Expected three test ZIPs, found: {archives}'
OUT = args.out.resolve()
OUT.mkdir(parents=True, exist_ok=True)
CLASSES = OUT/'classes'
JAVA = args.jdk/'bin/java.exe'
TOOLS = args.llvm.resolve()

def digest(data):
    return hashlib.sha256(data).hexdigest()

def source_snapshot():
    return {str(p.relative_to(ROOT)): digest(p.read_bytes())
            for p in sorted(ROOT.glob('src/**/*.java')) + [ROOT/'src/config.json']}

source_before = source_snapshot()
user_before = {name: (ROOT/name).read_bytes() if (ROOT/name).exists() else None
               for name in ('testfile.txt', 'error.txt', 'symbol.txt', 'llvm_ir.txt')}

def run(command, *, cwd=ROOT, data=b'', timeout=60):
    result = subprocess.run([str(c) for c in command], cwd=cwd, input=data,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f'exit={result.returncode}\n'+result.stderr.decode('utf-8', errors='replace'))
    return result

def text(data):
    # Preserve all spaces and terminal newlines; only platform line endings differ.
    return data.decode('utf-8-sig').replace('\r\n', '\n')

def difference(expected, actual):
    lines = list(difflib.unified_diff(expected.splitlines(keepends=True), actual.splitlines(keepends=True),
                                    fromfile='official', tofile='actual', n=2))
    return ''.join(lines[:35]) or f'expected={expected[-100:]!r}\nactual={actual[-100:]!r}'

cases, archive_info = [], []
for archive in archives:
    group = re.search(r'-([ABC])\.zip$', archive.name).group(1)
    with ZipFile(archive) as z:
        members = [n for n in z.namelist() if n.endswith('/testfile.txt') and '__MACOSX' not in n]
        members.sort(key=lambda n: int(re.search(r'testcase(\d+)/testfile.txt$', n).group(1)))
        assert len(members) == 20, (archive, len(members))
        archive_info.append({'path': str(archive.resolve()), 'sha256': digest(archive.read_bytes()), 'cases': len(members)})
        for member in members:
            number = int(re.search(r'testcase(\d+)/testfile.txt$', member).group(1))
            name = f'{group}{number:02}'
            directory = OUT/f'case_{name}'
            directory.mkdir(parents=True, exist_ok=True)
            expected = directory/'expected'
            expected.mkdir(exist_ok=True)
            folder = str(PurePosixPath(member).parent)
            for filename in ('testfile.txt', 'in.txt', 'ans.txt', 'lexer.txt', 'parser.txt', 'symbol.txt'):
                info = z.getinfo(folder+'/'+filename)
                assert info.file_size < 20_000_000
                content = z.read(info)
                (directory/'testfile.txt' if filename == 'testfile.txt' else expected/filename).write_bytes(content)
            # Ignore no executable/archive paths: only the six explicitly named text data files are read.
            cases.append({'name': name, 'group': group, 'member': member, 'archive': archive.name,
                          'directory': str(directory), 'source_sha256': digest((directory/'testfile.txt').read_bytes())})

print(f'Loaded {len(cases)} original official cases', flush=True)
run([args.jdk/'bin/javac.exe', '--release', '17', '-encoding', 'UTF-8', '-d', CLASSES,
     *sorted(ROOT.glob('src/**/*.java')), ROOT/'testgen/java/CompilerProbe.java'], timeout=90)
versions = {}
for name in ('llvm-as', 'opt', 'llvm-link', 'lli'):
    version = text(run([TOOLS/(name+'.exe'), '--version']).stdout)
    assert 'version 12.0.0' in version, version
    versions[name] = version.strip()
run([TOOLS/'llvm-as.exe', args.runtime.resolve(), '-o', OUT/'runtime.bc'])
run([JAVA, '-cp', CLASSES, 'CompilerProbe', OUT], timeout=90)

for case in cases:
    directory = Path(case['directory'])
    frontend, failures = {}, []
    stage = 'frontend'
    try:
        actual = directory/'actual'
        for product in ('lexer.txt', 'parser.txt', 'symbol.txt'):
            expected_text = text((directory/'expected'/product).read_bytes()).rstrip('\n')
            observed_text = text((actual/product).read_bytes()).rstrip('\n') if (actual/product).exists() else '<missing>'
            frontend[product] = expected_text == observed_text
            if not frontend[product]:
                failures.append({'stage': product, 'detail': difference(expected_text, observed_text)})
        for diagnostic in ('lexer.errors.txt', 'parser.errors.txt', 'semantic.errors.txt', 'crash.txt'):
            path = actual/diagnostic
            if path.exists() and path.read_bytes().strip():
                failures.append({'stage': diagnostic, 'detail': text(path.read_bytes())})

        stage = 'compile'
        run([JAVA, '-cp', CLASSES, 'Compiler', '--stage', 'llvm'], cwd=directory, timeout=30)
        if (directory/'error.txt').exists():
            raise RuntimeError(text((directory/'error.txt').read_bytes()))
        stage = 'llvm-as'
        run([TOOLS/'llvm-as.exe', 'llvm_ir.txt', '-o', 'program.bc'], cwd=directory)
        stage = 'opt-verify'
        run([TOOLS/'opt.exe', '-verify', '-disable-output', 'program.bc'], cwd=directory)
        stage = 'llvm-link'
        run([TOOLS/'llvm-link.exe', 'program.bc', OUT/'runtime.bc', '-o', 'linked.bc'], cwd=directory)
        stage = 'execute'
        executed = run([TOOLS/'lli.exe', 'linked.bc'], cwd=directory,
                       data=(directory/'expected/in.txt').read_bytes(), timeout=args.timeout)
        (actual/'stdout.txt').write_bytes(executed.stdout)
        (actual/'stderr.txt').write_bytes(executed.stderr)
        case['exit_code'] = executed.returncode
        expected_output = text((directory/'expected/ans.txt').read_bytes())
        actual_output = text(executed.stdout)
        case['runtime_output_match'] = expected_output == actual_output
        case['runtime_output_bytes'] = len(executed.stdout)
        if not case['runtime_output_match']:
            failures.append({'stage': 'stdout', 'detail': difference(expected_output, actual_output)})
        if executed.stderr:
            failures.append({'stage': 'stderr', 'detail': text(executed.stderr)})
        case['ir_verified'] = True
    except Exception as exception:
        failures.append({'stage': stage, 'detail': str(exception)})
    case['frontend'] = frontend
    case['failures'] = failures
    print(('PASS' if not failures else 'FAIL')+' '+case['name']+
          ('' if not failures else ' '+', '.join(f['stage'] for f in failures)), flush=True)
    # Persist partial progress and diagnostics in case a future run is interrupted.
    (directory/'result.json').write_text(json.dumps(case, ensure_ascii=False, indent=2), encoding='utf-8')

assert source_snapshot() == source_before, 'Compiler source changed during testing'
assert all(((ROOT/name).read_bytes() if (ROOT/name).exists() else None) == data for name, data in user_before.items()), 'Root input/output changed'
summary = {}
for group in 'ABC':
    group_cases = [c for c in cases if c['group'] == group]
    summary[group] = {'total': len(group_cases), 'passed': sum(not c['failures'] for c in group_cases),
                      'ir_verified': sum(c.get('ir_verified', False) for c in group_cases),
                      'runtime_output_match': sum(c.get('runtime_output_match', False) for c in group_cases),
                      'frontend': {product: sum(c['frontend'].get(product, False) for c in group_cases)
                                   for product in ('lexer.txt', 'parser.txt', 'symbol.txt')}}
report = {'date': '2026-11-02', 'archives': archive_info, 'versions': versions,
          'runtime': str(args.runtime.resolve()), 'runtime_sha256': digest(args.runtime.read_bytes()),
          'timeout_seconds': args.timeout, 'summary': summary, 'compiler_source_sha256': source_before,
          'original_root_files_unchanged': True, 'cases': cases}
(OUT/'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')

rows = ['# 官方代码生成测试结果', '', '测试日期：2026-11-02。测试对象为本次运行时的当前工作区源码，使用 JDK 的 `--release 17` 重新编译。', '',
        '直接读取 Downloads 中的 A/B/C 三个官方 ZIP，每包 20 个原始用例，不修改源码、输入或标准答案。', '',
        '| 测试包 | 用例数 | 词法答案一致 | 语法答案一致 | 符号表一致 | LLVM 验证通过 | 执行输出一致 | 全项通过 |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
for group, s in summary.items():
    rows.append(f"| {group} | {s['total']} | {s['frontend']['lexer.txt']} | {s['frontend']['parser.txt']} | {s['frontend']['symbol.txt']} | {s['ir_verified']} | {s['runtime_output_match']} | {s['passed']} |")
passed = sum(not c['failures'] for c in cases)
rows += ['', f'总计：**{passed}/{len(cases)} 全项通过**。', '',
         '执行顺序：词法/语法/语义答案比对 → 生成 LLVM IR → LLVM 12.0.0 llvm-as → opt -verify → 链接课程运行时 → lli。每个程序执行限时 '+str(args.timeout)+' 秒，同时检查退出状态、stderr 和标准输出。', '',
         '前端文本统一 CRLF/LF 并忽略文件末尾的换行数量；运行时输出只统一 CRLF/LF，保留所有空格及结尾换行。预期全部来自包内 lexer.txt/parser.txt/symbol.txt/ans.txt，未用编译器输出生成预期。', '',
         '根目录 testfile.txt/error.txt/symbol.txt/llvm_ir.txt 和编译器源码未改动；所有运行产物保留于 build/official-llvm。', '',
         '复现：', '', '```powershell', 'python tests/run-official-llvm-tests.py', '```', '',
         '详细结果：[results.json](../build/official-llvm/results.json)。每例目录保存官方输入答案、前端结果、llvm_ir.txt、链接后 bitcode、实际 stdout/stderr 和 result.json。', '',
         '## 输入文件校验', '', '| 文件 | SHA-256 |', '| --- | --- |']
for archive in archive_info:
    rows.append(f"| {Path(archive['path']).name} | `{archive['sha256']}` |")
bad = [c for c in cases if c['failures']]
if bad:
    rows += ['', '## 未通过项目', '']
    for case in bad:
        rows += [f"### {case['name']} — {case['archive']} / {case['member']}", '']
        for failure in case['failures']:
            rows += [f"阶段：`{failure['stage']}`", '', '```text', failure['detail'], '```', '']
else:
    rows += ['', '全部 60 个用例的前端答案、IR 验证、执行输出及正常退出状态均通过。本结果说明当前代码通过这三个公共测试包；其他输入仍需相应测试覆盖。', '']
(ROOT/'docs/官方代码生成测试结果.md').write_text('\n'.join(rows), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)
print(f'TOTAL {passed}/{len(cases)} passed; report: docs/官方代码生成测试结果.md', flush=True)
sys.exit(bool(bad))
