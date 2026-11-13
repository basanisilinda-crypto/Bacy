"""Strict version/package/signer gate for future GitHub Actions releases. No secrets logged."""
import hashlib,base64,json,os,re,subprocess
from pathlib import Path
import httpx
ROOT=Path(__file__).resolve().parents[1]
ORIGIN='https://ourxezawbfclmhbggskv.supabase.co'
BASE=ORIGIN+'/functions/v1/loop-manager'
CERT='f7cefaa71527767a786784e16626156c799ab374c6418061e33d6956321f6d51'
def local_version(module):
 s=(ROOT/f'android/{module}/build.gradle').read_text()
 return int(re.search(r'versionCode (\d+)',s).group(1)),re.search(r"versionName '([^']+)'",s).group(1)
def verify(module,package,sdk):
 apk=ROOT/f'android/{module}/build/outputs/apk/release/{module}-release.apk'
 cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],text=True)
 if 'certificate SHA-256 digest: '+CERT not in cert:raise SystemExit('Signing certificate mismatch: '+module)
 info=subprocess.check_output([str(sdk/'aapt'),'dump','badging',str(apk)],text=True)
 if "package: name='"+package+"'" not in info:raise SystemExit('Package identity mismatch: '+module)
 code,version=local_version(module)
 if "versionCode='"+str(code)+"'" not in info or "versionName='"+version+"'" not in info:raise SystemExit('Build/version mismatch: '+module)
 return apk.read_bytes(),code,version

def main():
 token=os.environ.get('LOOP_DEPLOY_TOKEN');sdk=Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])/'build-tools/36.0.0'
 if not token:raise SystemExit('Deployment token is missing; configure GitHub environment secrets.')
 published=0
 with httpx.Client(timeout=120,follow_redirects=False) as client:
  channels=[('app','za.co.loopmanager',BASE,'/api/admin/installer','/api/admin/provisioning/apk','loop-phone-installers'),('admin','za.co.bacy.admin',BASE,'/api/admin/admin-installer','/api/admin/mobile/installer','loop-phone-installers')]
  releases=[]
  for module,package,base,path,read,bucket in channels:
   raw,code,version=verify(module,package,sdk)
   current=client.get(base+read,headers={'Authorization':'Bearer '+token});current.raise_for_status();existing=current.json()
   old=int(existing.get('version_code',0))
   if code<old:raise SystemExit('Refusing version downgrade: '+module)
   if code==old:print(module+': version unchanged, skipped');continue
   releases.append((module,base,path,bucket,raw,code,version))
  for module,base,path,bucket,raw,code,version in releases:
   digest=hashlib.sha256(raw).digest();checksum=base64.urlsafe_b64encode(digest).decode().rstrip('=')
   result=client.put(base+path,content=raw,headers={'Authorization':'Bearer '+token,'Content-Type':'application/vnd.android.package-archive','X-App-Version':version,'X-App-Version-Code':str(code),'X-APK-SHA256':checksum});result.raise_for_status()
   meta=result.json();url=meta['apk_url'];expected=ORIGIN+'/storage/v1/object/public/'+bucket+'/'
   if not url.startswith(expected):raise SystemExit('Unexpected published artifact host')
   hosted=client.get(url);hosted.raise_for_status()
   if hashlib.sha256(hosted.content).digest()!=digest:raise SystemExit('Hosted APK checksum mismatch')
   published+=1;print(module+': signed version '+version+' published; hosted bytes verified')
 if not published:raise SystemExit('No newer version codes. Increment the changed app versionCode/versionName; rebuilding the same version does not trigger device updates.')
if __name__=='__main__':main()
