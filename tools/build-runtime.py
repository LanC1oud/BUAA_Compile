"""Rebuild a typed-pointer test runtime from the unmodified course C runtime."""
from pathlib import Path
import hashlib, json, subprocess

ROOT=Path(__file__).resolve().parents[1]
TOOL=ROOT/'build/llvm12/Library/bin/clang-12.exe'
OUT=ROOT/'runtime/llvm12'
OUT.mkdir(parents=True,exist_ok=True)
version=subprocess.check_output([str(TOOL),'-cc1','-version'],text=True,stderr=subprocess.STDOUT)
assert 'version 12.0.0' in version, version
vswhere=Path('C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe')
vs=Path(subprocess.check_output([str(vswhere),'-latest','-property','installationPath'],text=True).strip())
vc=sorted((vs/'VC/Tools/MSVC').iterdir())[0]/'include'
sdk=Path('C:/Program Files (x86)/Windows Kits/10/Include/10.0.22621.0')
includes=[ROOT/'build/llvm12/Library/lib/clang/12.0.0/include',vc,sdk/'ucrt',sdk/'shared']
# Direct cc1 invocation avoids Clang 12's driver crash when probing Visual Studio 2026.
command=[str(TOOL),'-cc1','-triple','x86_64-pc-windows-msvc','-emit-llvm','-O0',
         '-fno-signed-char','-fms-extensions','-fms-compatibility','-fms-compatibility-version=19.29',
         '-D_CRT_SECURE_NO_WARNINGS']
for include in includes: command+=['-internal-isystem',str(include)]
command+=['runtime/libsysy.c','-o','runtime/llvm12/libsysy.ll']
subprocess.run(command,cwd=ROOT,check=True)
record={'clang':version.strip(),'command':command,'source_sha256':{
    name:hashlib.sha256((ROOT/'runtime'/name).read_bytes()).hexdigest() for name in ('libsysy.c','libsysy.h')}}
(OUT/'build.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
print('Built official runtime:',OUT/'libsysy.ll',flush=True)
