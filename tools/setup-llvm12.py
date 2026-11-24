"""Install pinned LLVM tools locally; never change PATH or system installations."""
from pathlib import Path
import hashlib
import json
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'build' / 'llvm12'
DEST.mkdir(parents=True, exist_ok=True)
packages = [('llvm-tools', 'win-64/llvm-tools-12.0.0-ha327e53_1.tar.bz2'),
            ('clang-12', 'win-64/clang-12-12.0.0-default_h5c34c98_1.tar.bz2'),
            ('clang', 'win-64/clang-12.0.0-h57928b3_1.tar.bz2')]
records = []
for name, filename in packages:
    api = json.load(urllib.request.urlopen('https://api.anaconda.org/package/conda-forge/'+name, timeout=60))
    package = next(f for f in api['files'] if f['basename'] == filename)
    url = 'https://conda.anaconda.org/conda-forge/' + filename
    archive = DEST / (name+'-12.0.0.tar.bz2')
    print('Downloading', url, package['size'], flush=True)
    if not archive.exists() or archive.stat().st_size != package['size']:
        urllib.request.urlretrieve(url, archive)
    digest = hashlib.md5(archive.read_bytes()).hexdigest()
    assert digest == package['md5'], (digest, package['md5'])
    members = []
    with tarfile.open(archive, 'r|bz2') as tar:
        for member in tar:
            if not member.isfile() or not (name != 'llvm-tools' or member.name.endswith(('/llvm-as.exe', '/opt.exe', '/llvm-link.exe', '/lli.exe')) or member.name.endswith('.dll')):
                continue
            members.append(member)
            target = (DEST / member.name).resolve()
            assert target.is_relative_to(DEST.resolve())
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(tar.extractfile(member).read())
    records.append({'url':url, 'md5':digest, 'depends':package['attrs'].get('depends'), 'files':[m.name for m in members]})
(DEST / 'package.json').write_text(json.dumps({'version':'12.0.0', 'packages':records},indent=2),encoding='utf-8')
print('Installed', DEST, flush=True)
