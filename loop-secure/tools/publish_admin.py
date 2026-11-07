"""Publish the locally verified signed installer through the protected deployment API."""
import base64,hashlib,json,re,subprocess
from pathlib import Path
import httpx
root=Path(__file__).resolve().parents[1]
apk=root/'android/admin/build/outputs/apk/release/admin-release.apk'
key=json.loads((root/'.signing/cloud-admin.json').read_text())['token']
base='https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager'
version=re.search(r"versionName '([^']+)'",(root/'android/admin/build.gradle').read_text()).group(1)
raw=apk.read_bytes();checksum=base64.urlsafe_b64encode(hashlib.sha256(raw).digest()).decode().rstrip('=')
with httpx.Client(timeout=120) as client:
    original=client.get(base+'/api/admin/provisioning/apk',headers={'Authorization':'Bearer '+key});original.raise_for_status()
    response=client.put(base+'/api/admin/admin-installer',content=raw,headers={'Authorization':'Bearer '+key,'Content-Type':'application/vnd.android.package-archive','X-App-Version':version,'X-APK-SHA256':checksum})
    if response.status_code!=200:raise RuntimeError('Installer publish failed: '+str(response.status_code)+' '+response.text[:500])
    metadata=response.json()
    unchanged=client.get(base+'/api/admin/provisioning/apk',headers={'Authorization':'Bearer '+key});unchanged.raise_for_status()
    assert unchanged.json()['apk_url']==original.json()['apk_url'] and unchanged.json()['apk_sha256']==original.json()['apk_sha256'],'Preview must not replace the managed QR installer'
    hosted=client.get(metadata['apk_url'])
    assert hosted.status_code==200
    assert hashlib.sha256(hosted.content).digest()==hashlib.sha256(raw).digest()
    (root/'artifacts/hosted-admin-verification.apk').write_bytes(hosted.content)
    (root/'artifacts/Bacy-Admin.apk').write_bytes(raw)
    (root/'artifacts/admin-installer-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
print('Native administrator published and downloaded bytes verified:',len(raw),'bytes')
