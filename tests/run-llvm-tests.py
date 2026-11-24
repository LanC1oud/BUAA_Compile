"""Compile -> llvm-as -> opt -verify -> link official runtime -> lli, with timeouts."""
from pathlib import Path
import argparse
import json
import subprocess
import sys
from llvm_cases import CASES

ROOT = Path(__file__).resolve().parents[1]
CASES.append(('official-comprehensive', (ROOT/'tests/official-llvm.sysy').read_text(encoding='utf-8'),
              'Az9!bB2?xy7#', 'raw=Az9!bB2?xy7#\nupper=AZ9!BB2?XY7#\nU=2 L=4 D=3 O=3\nscore=29\nreverse=#7YX?2BB!9ZA\n', 0))
parser = argparse.ArgumentParser()
parser.add_argument('--jdk', default='C:/Program Files/Java/jdk-21')
parser.add_argument('--llvm', default=str(ROOT/'build/llvm12/Library/bin'))
parser.add_argument('--runtime', default=str(ROOT/'runtime/llvm12/libsysy.ll'))
args = parser.parse_args()
JDK, LLVM = Path(args.jdk), Path(args.llvm)
OUT = ROOT/'build/llvm-tests'
CLASSES = OUT/'classes'
OUT.mkdir(parents=True, exist_ok=True)

def run(command, *, cwd=ROOT, stdin='', expected=0, timeout=15):
    result = subprocess.run([str(c) for c in command], cwd=cwd, input=stdin.encode(),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if result.returncode != expected:
        raise AssertionError(f'{command[0]} exit {result.returncode}, expected {expected}\n'+result.stderr.decode(errors='replace'))
    return result.stdout.decode('utf-8').replace('\r\n','\n')

sources = sorted(ROOT.glob('src/**/*.java')) + sorted(ROOT.glob('ui/**/*.java'))
run([JDK/'bin/javac.exe', '--release', '17', '-encoding', 'UTF-8', '-d', CLASSES,
     *sources, ROOT/'tests/LlvmPipelineRegression.java'], timeout=90)
versions = {}
for tool in ('llvm-as', 'opt', 'llvm-link', 'lli'):
    version = run([LLVM/(tool+'.exe'), '--version'])
    assert 'version 12.0.0' in version, version
    versions[tool] = version.strip()
runtime = Path(args.runtime).resolve()
assert runtime.is_file(), f'Prepare official runtime first: {runtime}'
run([LLVM/'llvm-as.exe', runtime, '-o', OUT/'runtime.bc'])
run([JDK/'bin/java.exe','-cp',CLASSES,'LlvmPipelineRegression',OUT/'model.ll'])
run([LLVM/'llvm-as.exe',OUT/'model.ll','-o',OUT/'model.bc'])
run([LLVM/'opt.exe','-verify','-disable-output',OUT/'model.bc'])

passed, failures = [], []
for name, source, stdin, expected, code in CASES:
    case_dir=OUT/name; case_dir.mkdir(exist_ok=True)
    (case_dir/'testfile.txt').write_text(source,encoding='utf-8')
    try:
        run([JDK/'bin/java.exe','-cp',CLASSES,'Compiler','--stage','llvm'],cwd=case_dir)
        assert not (case_dir/'error.txt').exists(), (case_dir/'error.txt').read_text()
        run([LLVM/'llvm-as.exe','llvm_ir.txt','-o','program.bc'],cwd=case_dir)
        run([LLVM/'opt.exe','-verify','-disable-output','program.bc'],cwd=case_dir)
        run([LLVM/'llvm-link.exe','program.bc',OUT/'runtime.bc','-o','linked.bc'],cwd=case_dir)
        actual=run([LLVM/'lli.exe','linked.bc'],cwd=case_dir,stdin=stdin,expected=code,timeout=5)
        assert actual==expected, f'expected {expected!r}, actual {actual!r}'
        passed.append(name)
        print('PASS',name,flush=True)
    except Exception as e:
        failures.append({'name':name,'error':str(e)})
        print('FAIL',name,str(e),flush=True)

# Frontend failure must remove the previous successful IR, even on parser recovery.
case_dir=OUT/'minimal'
for source in ('int main(){missing=1;return 0;}', 'int main(){int a=1\nreturn 0;}'):
    (case_dir/'llvm_ir.txt').write_text('stale IR',encoding='utf-8')
    (case_dir/'testfile.txt').write_text(source,encoding='utf-8')
    run([JDK/'bin/java.exe','-cp',CLASSES,'Compiler','--stage','llvm'],cwd=case_dir)
    assert not (case_dir/'llvm_ir.txt').exists() and (case_dir/'error.txt').read_text().strip()

report={'versions':versions,'runtime':str(runtime),'passed':passed,'failures':failures,
        'state_and_model':'passed','error_artifact_cleanup':'passed'}
(OUT/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'LLVM execution: {len(passed)} passed, {len(failures)} failed',flush=True)
sys.exit(bool(failures))
