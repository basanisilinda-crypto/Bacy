from pathlib import Path
import base64,hashlib,json
import httpx
root=Path(__file__).resolve().parents[1]
base='https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager'
secret=json.loads((root/'.signing/cloud-admin.json').read_text())['token']
with httpx.Client(timeout=60) as client:
    health=client.get(base+'/health');assert health.status_code==200,('Health',health.status_code,health.text)
    assert health.json()['status']=='ok'
    unauthorized=client.get(base+'/api/admin/overview');assert unauthorized.status_code==401,unauthorized.text
    overview=client.get(base+'/api/admin/overview',headers={'Authorization':'Bearer '+secret})
    assert overview.status_code==200,('Admin',overview.status_code,overview.text)
    assert isinstance(overview.json()['devices'],list)
    if '--expect-empty' in __import__('sys').argv: assert overview.json()['devices']==[], 'Fresh deployment should have an empty real register'
    config=client.get(base+'/api/admin/provisioning/apk',headers={'Authorization':'Bearer '+secret})
    assert config.status_code==200,config.text
    assert config.json()['qr_renderer_ready'] is True,'Cloud QR renderer failed'
    apk=client.get(config.json()['apk_url']);assert apk.status_code==200,apk.text[:100]
    local=(root/'artifacts/LoopPhoneManager-release.apk').read_bytes()
    assert hashlib.sha256(apk.content).digest()==hashlib.sha256(local).digest(),'Hosted APK differs from verified signed release'
    (root/'artifacts/hosted-client-verification.apk').write_bytes(apk.content)
    assert config.json()['apk_sha256']==base64.urlsafe_b64encode(hashlib.sha256(local).digest()).decode().rstrip('=')
    invalid=client.post(base+'/api/device/enroll',json={'enrollment_token':'invalid-real-token-123456','model':'No test device is created','consent':True})
    assert invalid.status_code==401,invalid.text
print('Live cloud checks passed: health, administrator authentication, real register access, exact signed APK bytes, provisioning checksum, and rejection of invalid enrollment.')
