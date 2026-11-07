"""Package verified build outputs, excluding all private runtime/signing material."""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import zipfile

root=Path(__file__).resolve().parents[1]
out=root/'artifacts';out.mkdir(exist_ok=True)
release=root/'android/app/build/outputs/apk/release/app-release.apk'
if not release.exists():raise SystemExit('Assemble and verify the signed release first')
shutil.copyfile(release,out/'LoopPhoneManager-release.apk')
(root/'backend/downloads').mkdir(exist_ok=True)
shutil.copyfile(release,root/'backend/downloads/LoopPhoneManager.apk')
checksum=base64.urlsafe_b64encode(hashlib.sha256(release.read_bytes()).digest()).decode().rstrip('=')
(out/'apk-checksum.txt').write_text(checksum+'\n')
report=root/'android/app/build/reports/lint-results-release.html'
if report.exists():shutil.copyfile(report,out/'android-lint.html')
private=root/'.signing'
if (private/'signing.json').exists():
    settings=json.loads((private/'signing.json').read_text())
    backup={**settings,'store_file':'loop-release.jks'}
    with zipfile.ZipFile(out/'LoopPhoneManager-PRIVATE-signing-backup.zip','w',zipfile.ZIP_DEFLATED) as z:
        z.write(private/'loop-release.jks','loop-release.jks')
        z.writestr('signing.json',json.dumps(backup,indent=2)+'\n')
        if (private/'cloud-admin.json').exists(): z.write(private/'cloud-admin.json','cloud-admin.json')
        z.writestr('KEEP-PRIVATE.txt','This backup contains the private Android signing key, its passwords, and the private cloud administrator API credential when present. Store it securely. Never upload it to GitHub or put it on the public server. Use the same identity to sign future updates. The source/deployment ZIP excludes these secrets.\n')
    (out/'LoopPhoneManager-PRIVATE-signing-backup.zip').chmod(0o600)
skip={'.venv','.gradle','build','data','__pycache__','artifacts','.git','.signing','node_modules','dist','.wrangler','.cache'}
files=[]
for path in root.rglob('*'):
    relative=path.relative_to(root)
    if not path.is_file() or any(part in skip for part in relative.parts) or path.name in {'.env','local.properties','.npmrc'}:continue
    if (path.name.startswith('.env.') or path.name.startswith('.dev.vars')) and not path.name.endswith('.example'):continue
    if path.suffix in {'.jks','.keystore','.key','.sqlite'}:raise RuntimeError('Private material found outside exclusions')
    files.append(path)
archive=out/'LoopPhoneManager-complete-build.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in files:z.write(path,Path('LoopPhoneManager')/path.relative_to(root))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert 'LoopPhoneManager/backend/downloads/LoopPhoneManager.apk' in z.namelist()
    assert not any('/.signing/' in name or name.endswith('signing.json') for name in z.namelist())
print('Packaged signed APK, complete source/deployment archive and separate private signing backup.')
