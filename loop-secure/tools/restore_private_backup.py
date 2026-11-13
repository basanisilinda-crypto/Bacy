"""Decrypt the owner's private backup locally. Requires cryptography; never print secrets."""
import argparse,hashlib,io,json,tarfile
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
p=argparse.ArgumentParser();p.add_argument('backup');p.add_argument('password_file');p.add_argument('--destination',default='.');a=p.parse_args()
b=Path(a.backup).read_bytes();magic=b'LOOPSECRETS1\n';n=len(magic);assert b[:n]==magic,'Unknown backup format';salt=b[n:n+16];nonce=b[n+16:n+28];password=Path(a.password_file).read_bytes().strip();key=hashlib.pbkdf2_hmac('sha256',password,salt,300000,32)
plain=AESGCM(key).decrypt(nonce,b[n+28:],magic);dest=Path(a.destination).resolve();dest.mkdir(parents=True,exist_ok=True)
with tarfile.open(fileobj=io.BytesIO(plain),mode='r:gz') as t:t.extractall(dest,filter='data')
private=dest/'.signing';private.chmod(0o700)
for f in private.iterdir():f.chmod(0o600)
s=private/'signing.json';data=json.loads(s.read_text());data['store_file']=str(private/'loop-release.jks');s.write_text(json.dumps(data));s.chmod(0o600)
print('Private configuration restored. No secret values displayed. Keep .signing out of Git.')
