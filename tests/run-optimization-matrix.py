"""Feature-switch independence: LLVM verifier/lli and course MARS, in isolated directories."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse, json, os, subprocess
from extreme_cases import EXTREME_CASES

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--out',type=Path,default=ROOT/'build/extreme-matrix')
p.add_argument('--jdk',type=Path,default=Path('C:/Program Files/Java/jdk-21'))
p.add_argument('--mars',type=Path,default=Path.home()/'Downloads/mars-2026-BUAA.jar')
p.add_argument('--resume',action='store_true',help='Keep passed entries and rerun only missing/failed entries')
args=p.parse_args(); out=args.out.resolve(); out.mkdir(parents=True,exist_ok=True)
classes=out/'classes'; java=args.jdk/'bin/java.exe'; llvm=ROOT/'build/llvm12/Library/bin'
def run(cmd,cwd=ROOT,stdin=''):
    r=subprocess.run(list(map(str,cmd)),cwd=cwd,input=stdin.encode(),capture_output=True,timeout=45)
    assert r.returncode==0, r.stderr.decode(errors='replace')
    return r.stdout.decode('utf-8-sig').replace('\r\n','\n')
run([args.jdk/'bin/javac.exe','--release','17','-encoding','UTF-8','-d',classes,
     *sorted(ROOT.glob('src/**/*.java')),*sorted(ROOT.glob('ui/**/*.java')),
     ROOT/'tests/OptimizationRunner.java',ROOT/'tests/support/MemoryPreferencesFactory.java'])
features=['enableMem2Reg','enableGlobalScalarPromotion','enableIrCleanup','enableInlining','enableTailRecursion','enableGvn','enableGcm',
          'enableMemoryForwarding','enableGlobalRegisters','enableConstantDivision','enableRangeAnalysis','enablePeephole','enableLoopLayout','enableDelaySlotRemoval']
modes=[('', 'all-on')]+[(flag,flag+'-off') for flag in features]+[(','.join(features),'all-off'),
       ('enableGlobalRegisters,enablePeephole,enableLoopLayout','ssa-stack'),
       ('enableGlobalScalarPromotion,enableIrCleanup,enableInlining,enableTailRecursion,enableGvn,enableGcm,enableMemoryForwarding,enableGlobalRegisters,enablePeephole,enableLoopLayout','mem2reg-only')]
names={'extreme-phi-cycle','extreme-loop-pressure','extreme-inline-loop-phi','extreme-memory-alias',
       'extreme-gcm-zero-trip','extreme-tail-recursion','extreme-char-phi','extreme-global-scalar','extreme-array-parameter'}
names.update({'extreme-range-positive','extreme-range-overflow','extreme-range-negative','extreme-range-step'})
cases=[c for c in EXTREME_CASES if c[0] in names]
def check(task):
    (disabled,mode),(name,source,stdin,expected,code)=task
    directory=out/mode/name; directory.mkdir(parents=True,exist_ok=True)
    (directory/'testfile.txt').write_text(source,encoding='utf-8')
    run([java,'-cp',classes,'OptimizationRunner',disabled,'--optimize'],directory)
    assert not (directory/'error.txt').exists(), (directory/'error.txt').read_text()
    run([llvm/'llvm-as.exe','llvm_ir.txt','-o','program.bc'],directory)
    run([llvm/'opt.exe','-verify','-disable-output','program.bc'],directory)
    run([llvm/'llvm-link.exe','program.bc',ROOT/'runtime/llvm12/libsysy.ll','-o','linked.bc'],directory)
    assert run([llvm/'lli.exe','linked.bc'],directory,stdin)==expected
    for delayed in (False,True):
        if delayed: run([java,'-cp',classes,'OptimizationRunner',disabled,'--optimize','--delay-slots'],directory)
        stdout=run([java,'-Djava.util.prefs.PreferencesFactory=MemoryPreferencesFactory','-cp',
                    str(classes)+os.pathsep+str(args.mars),'io.github.dpetersanderson.mars.Mars',
                    '--no-copyright','--messages-to-stderr','--assemble-error-exit-code=2',
                    '--simulate-error-exit-code=3','--max-steps=50000000',
                    *(['--delayed-branching'] if delayed else []),'mips.txt'],directory,stdin)
        assert stdout==expected,(mode,name,delayed,expected,stdout)
    return {'mode':mode,'case':name,'result':'passed'}
results=[]; failures=[]; retry_history=[]
if args.resume and (out/'results.json').exists():
    previous=json.loads((out/'results.json').read_text(encoding='utf-8'))
    results=previous['passed']; retry_history=previous.get('retry_history',[])+previous['failures']
tasks=[(mode,case) for mode in modes for case in cases]
completed={(r['mode'],r['case']) for r in results}
tasks=[task for task in tasks if (task[0][1],task[1][0]) not in completed]
# Create parent/child directories serially; avoid Windows directory-creation races in workers.
for mode,case in tasks: (out/mode[1]/case[0]).mkdir(parents=True,exist_ok=True)
def safe(task):
    try:return check(task)
    except Exception as e:return {'mode':task[0][1],'case':task[1][0],'error':str(e)}
with ThreadPoolExecutor(max_workers=4) as pool:
    for result in pool.map(safe,tasks):
        (failures if 'error' in result else results).append(result)
        print('FAIL' if 'error' in result else 'PASS',result['mode'],result['case'],result.get('error',''),flush=True)
(out/'results.json').write_text(json.dumps({'passed':results,'failures':failures,'configurations':len(modes),
    'cases_per_configuration':len(cases),'mars_modes':2,'retry_history':retry_history},ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Optimization matrix: {len(results)} passed, {len(failures)} failed',flush=True)
raise SystemExit(bool(failures))
