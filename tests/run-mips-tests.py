"""Java 17 compile -> MIPS -> course MARS -> exact stdout + runtime errors + stats."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import hashlib
import json
import os
import subprocess
import sys
from llvm_cases import CASES
from mips_cases import EXTRA_CASES
from mips_optimization_cases import OPTIMIZATION_CASES
from mips_round2_cases import ROUND2_CASES
from extreme_cases import EXTREME_CASES

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--jdk', type=Path, default=Path('C:/Program Files/Java/jdk-21'))
parser.add_argument('--mars', type=Path, default=Path.home()/'Downloads/mars-2026-BUAA.jar')
parser.add_argument('--official-dir', type=Path, help='Existing extracted public fixtures (case_*/expected/in.txt, ans.txt)')
parser.add_argument('--out', type=Path, default=ROOT/'build/mips-tests')
parser.add_argument('--filter', default='')
parser.add_argument('--jobs', type=int, default=4, help='Independent case workers')
parser.add_argument('--source-root', type=Path, default=ROOT, help='Compile a saved compiler snapshot for comparison')
parser.add_argument('--verify-llvm', action='store_true', help='Verify and execute generated IR with LLVM 12 as a second oracle')
parser.add_argument('--disable', default='', help='Comma separated Configure feature switches to turn off')
args = parser.parse_args()
OUT = args.out.resolve(); OUT.mkdir(parents=True, exist_ok=True)
CLASSES = OUT/'classes'
JAVA = args.jdk/'bin'/('java.exe' if os.name == 'nt' else 'java')
JAVAC = args.jdk/'bin'/('javac.exe' if os.name == 'nt' else 'javac')
assert args.mars.is_file(), f'Missing course MARS: {args.mars}'

def run(command, *, cwd=ROOT, stdin='', timeout=25):
    process = subprocess.run([str(c) for c in command], cwd=cwd, input=stdin.encode('utf-8'),
                             capture_output=True, timeout=timeout)
    if process.returncode:
        raise AssertionError(f'exit={process.returncode}\n{process.stdout.decode(errors="replace")}\n'
                             + process.stderr.decode(errors='replace'))
    return process

def text(data):
    return data.decode('utf-8-sig').replace('\r\n', '\n')

run([JAVAC, '--release', '17', '-encoding', 'UTF-8', '-d', CLASSES,
     *sorted(args.source_root.glob('src/**/*.java')), *sorted(args.source_root.glob('ui/**/*.java')),
     ROOT/'tests/MipsPipelineRegression.java', ROOT/'tests/MipsAllocationRegression.java',
     ROOT/'tests/support/MemoryPreferencesFactory.java', ROOT/'tests/OptimizationRunner.java',
     *([ROOT/'tests/MipsSsaRegression.java'] if args.source_root.resolve() == ROOT else [])], timeout=90)
mars = [JAVA, '-Djava.util.prefs.PreferencesFactory=MemoryPreferencesFactory', '-cp',
        str(CLASSES)+os.pathsep+str(args.mars.resolve()), 'io.github.dpetersanderson.mars.Mars',
        '--no-copyright', '--messages-to-stderr', '--assemble-error-exit-code=2',
        '--simulate-error-exit-code=3', '--max-steps=50000000']
version_result = run([*mars[:5], '--version'])
version = text(version_result.stdout + version_result.stderr).strip()
assert '2026-BUAA' in version, version
print(version, flush=True)
print(text(run([JAVA, '-cp', CLASSES, 'MipsPipelineRegression', OUT/'pipeline']).stdout).strip(), flush=True)
for model_name in ('model.s', 'model-opt.s'):
    for delayed in (False, True):
        model = run([*mars, *(['--delayed-branching'] if delayed else []), model_name], cwd=OUT/'pipeline')
        assert text(model.stdout) == '2147483647' + '1' + '254' + '0110100101' + '1' + '2147483647' + '1' + '253', repr(model.stdout)
        assert not text(model.stderr).strip(), repr(model.stderr)

if args.source_root.resolve() == ROOT:
    print(text(run([JAVA, '-cp', CLASSES, 'MipsSsaRegression', OUT/'pipeline']).stdout).strip(), flush=True)
    a,b,c,total = 1,2,3,0
    for _ in range(37): total += a*100+b*10+c; a,b,c=b,c,a
    words = [str(total)]
    for n in [0,1,127,65535,2147483647,2147483648,2147483649,4294901759,4294967294,4294967295]:
        for d in [3,5,7,10,31,127,65535,123456789,2147483647,2147483649,4294967294,4294967295]:
            for v in (n//d,n%d): words.append(str(v if v<2147483648 else v-4294967296))
    expected_ssa = ' '.join(words)+' '
    for model_name in ('ssa-plain.s','ssa-opt.s','ssa-no-global.s','ssa-no-peephole.s'):
        for delayed in (False,True):
            model=run([*mars,*(['--delayed-branching'] if delayed else []),model_name],cwd=OUT/'pipeline')
            assert text(model.stdout)==expected_ssa, model_name+': '+repr(model.stdout)
            assert not text(model.stderr).strip(), repr(model.stderr)
    if args.verify_llvm:
        llvm=ROOT/'build/llvm12/Library/bin'
        run([llvm/'llvm-as.exe','ssa.ll','-o','ssa.bc'],cwd=OUT/'pipeline')
        run([llvm/'opt.exe','-verify','-disable-output','ssa.bc'],cwd=OUT/'pipeline')
print(text(run([JAVA, '-cp', CLASSES, 'MipsAllocationRegression', OUT/'pipeline']).stdout).strip(), flush=True)
for model_name in ('allocation.s', 'allocation-opt.s'):
    for delayed in (False, True):
        model = run([*mars, *(['--delayed-branching'] if delayed else []), model_name], cwd=OUT/'pipeline')
        assert text(model.stdout) == '1502510' + '0110100101', repr(model.stdout)
        assert not text(model.stderr).strip(), repr(model.stderr)

cases = list(CASES) + EXTRA_CASES + OPTIMIZATION_CASES + ROUND2_CASES + EXTREME_CASES + [
    ('official-comprehensive', (ROOT/'tests/official-llvm.sysy').read_text(encoding='utf-8'),
     'Az9!bB2?xy7#', 'raw=Az9!bB2?xy7#\nupper=AZ9!BB2?XY7#\nU=2 L=4 D=3 O=3\nscore=29\nreverse=#7YX?2BB!9ZA\n', 0)]
if args.official_dir:
    for directory in sorted(args.official_dir.glob('case_*')):
        if not (directory/'expected/ans.txt').exists(): continue
        cases.append(('public-'+directory.name, (directory/'testfile.txt').read_text(encoding='utf-8-sig'),
                      text((directory/'expected/in.txt').read_bytes()),
                      text((directory/'expected/ans.txt').read_bytes()), 0))

def run_case(case):
    name, source, stdin, expected, _exit_code = case
    statistics = {}
    directory = OUT/name; directory.mkdir(exist_ok=True)
    (directory/'testfile.txt').write_text(source, encoding='utf-8')
    try:
        ir_before = None
        for optimized in (False, True):
            options = ['--optimize'] if optimized else ['--no-optimize']
            run([JAVA, '-cp', CLASSES, 'OptimizationRunner', args.disable, *options, '--ir-before', 'before.ll'], cwd=directory)
            assert not (directory/'error.txt').exists(), (directory/'error.txt').read_text()
            ir = (directory/'llvm_ir.txt').read_bytes()
            before = (directory/'before.ll').read_bytes()
            assert ir_before is None or before == ir_before, 'unoptimized IR snapshot changed'
            if not optimized: assert ir == before, 'disabled optimization changed IR'
            ir_before = before
            if args.verify_llvm:
                llvm = ROOT/'build/llvm12/Library/bin'
                run([llvm/'llvm-as.exe', 'llvm_ir.txt', '-o', 'program.bc'], cwd=directory)
                run([llvm/'opt.exe', '-verify', '-disable-output', 'program.bc'], cwd=directory)
                run([llvm/'llvm-link.exe', 'program.bc', ROOT/'runtime/llvm12/libsysy.ll', '-o', 'linked.bc'], cwd=directory)
                result = subprocess.run([str(llvm/'lli.exe'), 'linked.bc'], cwd=directory,
                                        input=stdin.encode(), capture_output=True, timeout=10)
                assert result.returncode == _exit_code and text(result.stdout) == expected, 'LLVM execution mismatch: '+repr(result.stdout)
            for delayed in (False, True):
                if optimized and delayed and args.source_root.resolve() == ROOT:
                    run([JAVA, '-cp', CLASSES, 'OptimizationRunner', args.disable, *options,
                         '--delay-slots', '--ir-before', 'before.ll'], cwd=directory)
                    assert (directory/'llvm_ir.txt').read_bytes() == ir, 'delay slot setting changed IR'
                mode = f'{"opt" if optimized else "plain"}-{"delay" if delayed else "course"}'
                stat_file = f'{mode}.json'
                extra = ['--delayed-branching'] if delayed else []
                result = run([*mars, *extra, f'--instruction-statistics={stat_file}', 'mips.txt'],
                             cwd=directory, stdin=stdin)
                actual = text(result.stdout)
                assert actual == expected, f'{mode}: expected {expected!r}, actual {actual!r}'
                diagnostics = text(result.stderr).strip()
                assert not diagnostics, f'{mode}: MARS diagnostics: {diagnostics}'
                assert (directory/stat_file).is_file(), 'simulation did not finish with statistics'
                statistics[name+'/'+mode] = json.loads((directory/stat_file).read_text(encoding='utf-8'))
        return name, statistics, None
    except Exception as error:
        return name, statistics, str(error)

selected = [case for case in cases if args.filter in case[0]]
for case in selected: (OUT/case[0]).mkdir(exist_ok=True)
passed, failures, statistics = [], [], {}
with ThreadPoolExecutor(max_workers=max(1,args.jobs)) as pool:
    for name, case_statistics, error in pool.map(run_case, selected):
        statistics.update(case_statistics)
        if error is None:
            passed.append(name); print('PASS',name,flush=True)
        else:
            failures.append({'name':name,'error':error}); print('FAIL',name,error,flush=True)

# Alternate successful, semantic-error and syntax-error compilations in one directory.
directory = OUT/'cleanup'; directory.mkdir(exist_ok=True)
for source in ('int main(){return 0;}', 'int main(){missing=1;return 0;}',
               'int main(){return 0;}', 'int main(){int x=1\nreturn 0;}', 'int main(){return 0;}'):
    (directory/'testfile.txt').write_text(source, encoding='utf-8')
    run([JAVA, '-cp', CLASSES, 'Compiler', '--ir-before', 'before.ll'], cwd=directory)
    success = source == 'int main(){return 0;}'
    for name in ('mips.txt', 'llvm_ir.txt', 'before.ll'):
        assert (directory/name).exists() == success, f'stale or missing {name}'
    assert (directory/'error.txt').exists() != success

report = {'mars': str(args.mars.resolve()), 'version': version,
          'disabled_features': args.disable,
          'mars_sha256': hashlib.sha256(args.mars.read_bytes()).hexdigest(),
          'passed': passed, 'failures': failures, 'modes_per_case': 4,
          'pipeline_model_batch_cleanup': 'passed', 'statistics': statistics}
comparisons = []
for name in passed:
    plain, optimized = statistics[name+'/plain-course'], statistics[name+'/opt-course']
    comparisons.append({'name': name, 'plain_cycles': plain['finalCycles'], 'optimized_cycles': optimized['finalCycles'],
                        'plain_instructions': plain['totalInstructionCount'],
                        'optimized_instructions': optimized['totalInstructionCount']})
plain = sum(c['plain_cycles'] for c in comparisons)
optimized = sum(c['optimized_cycles'] for c in comparisons)
report['performance'] = {'plain_cycles': plain, 'optimized_cycles': optimized,
                         'reduction_percent': (1-optimized/plain)*100 if plain else 0, 'cases': comparisons}
(OUT/'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'MIPS execution: {len(passed)} passed, {len(failures)} failed (4 modes each)', flush=True)
print(f'Course weighted cycles: {plain} -> {optimized} ({report["performance"]["reduction_percent"]:.2f}% reduction)', flush=True)
sys.exit(bool(failures))
