"""Local-only owner/reviewer setup; never exposed as a bootstrap API."""
import argparse
import base64
import getpass
import secrets
from urllib.parse import quote
from .app import db, password_hash, uid, audit

p=argparse.ArgumentParser()
p.add_argument('command',choices=['create-admin'])
p.add_argument('--email',required=True)
p.add_argument('--role',choices=['owner','reviewer','viewer'],default='owner')
a=p.parse_args()
password=getpass.getpass('New password (minimum 14 characters): ')
if len(password)<14: raise SystemExit('Use at least 14 characters')
if password!=getpass.getpass('Repeat password: '): raise SystemExit('Passwords differ')
secret=base64.b32encode(secrets.token_bytes(20)).decode().rstrip('=')
id=uid()
with db() as c:
    c.execute('INSERT INTO admins(id,email,password,totp,role) VALUES(?,?,?,?,?)',(id,a.email.strip().lower(),password_hash(password),secret,a.role))
    audit(c,'local_setup','admin_created',id,a.role)
print('Add this account to an authenticator; store the secret securely, then clear your terminal.')
print('Authenticator setup key:',secret)
print('otpauth://totp/Loop%20Manager:'+quote(a.email)+'?secret='+secret+'&issuer=Loop%20Manager')
