"""Publish the locally verified signed installer through the protected deployment API."""
import base64,hashlib,json,re,subprocess
from pathlib import Path
import httpx
root=Path(__file__).resolve().parents[1]
apk=root/'android/app/build/outputs/apk/release/app-release.apk'
key=json.loads((root/'.signing/cloud-admin.json').read_text())['token']
base='https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager'
version=re.search(r"versionName '([^']+)'",(root/'android/app/build.gradle').read_text()).group(1)
raw=apk.read_bytes();checksum=base64.urlsafe_b64encode(hashlib.sha256(raw).digest()).decode().rstrip('=')
with httpx.Client(timeout=120) as client:
    response=client.put(base+'/api/admin/installer',content=raw,headers={'Authorization':'Bearer '+key,'Content-Type':'application/vnd.android.package-archive','X-App-Version':version,'X-APK-SHA256':checksum})
    if response.status_code!=200:raise RuntimeError('Installer publish failed: '+str(response.status_code)+' '+response.text[:500])
    metadata=response.json()
    hosted=client.get(metadata['apk_url'])
    assert hosted.status_code==200
    assert hashlib.sha256(hosted.content).digest()==hashlib.sha256(raw).digest()
    (root/'artifacts/hosted-client-verification.apk').write_bytes(hosted.content)
    (root/'artifacts/installer-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
print('Signed release published and downloaded bytes verified:',len(raw),'bytes')
