"""Loop Manager: authenticated, persistent finance management API."""
import asyncio
import base64
import hashlib
import hmac
import io
import json
import os
import secrets
import sqlite3
import struct
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Literal

from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

ROOT = Path(__file__).parent
DATA = Path(os.environ.get('LOOP_DATA_DIR', ROOT / 'data'))
DATA.mkdir(parents=True, exist_ok=True, mode=0o700)
DEV = os.environ.get('LOOP_DEV') == '1'
PUBLIC_URL = os.environ.get('LOOP_PUBLIC_URL', 'https://localhost:8443').rstrip('/')
DAY = 86400

def persistent_secret(name, generate):
    path = DATA / name
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return path.read_bytes()
    with os.fdopen(fd, 'wb') as f:
        f.write(generate())
    return path.read_bytes()

signing_key = Ed25519PrivateKey.from_private_bytes(persistent_secret('signing.key', lambda: Ed25519PrivateKey.generate().private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())))
cipher = Fernet(persistent_secret('identity.key', Fernet.generate_key))
ENROLLMENT_HMAC_KEY=persistent_secret('enrollment-hmac.key',lambda:secrets.token_bytes(32))
PUBLIC_KEY = base64.b64encode(signing_key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()

@contextmanager
def db():
    c = sqlite3.connect(DATA / 'loop.sqlite', timeout=15)
    c.row_factory = sqlite3.Row
    c.execute('PRAGMA foreign_keys=ON')
    try:
        c.execute('BEGIN IMMEDIATE')
        yield c
        c.commit()
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()

with db() as c:
    c.executescript('''
    PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS admins(id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL, totp TEXT NOT NULL, last_step INTEGER NOT NULL DEFAULT 0, role TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY, admin_id TEXT NOT NULL REFERENCES admins(id), expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS customers(id TEXT PRIMARY KEY, name TEXT NOT NULL, identity BLOB NOT NULL, phone TEXT NOT NULL, reference TEXT UNIQUE NOT NULL, verified INTEGER NOT NULL, verification_note TEXT NOT NULL, consent INTEGER NOT NULL, created INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, customer_id TEXT UNIQUE NOT NULL REFERENCES customers(id), token TEXT UNIQUE, enrollment TEXT UNIQUE, enrollment_expires INTEGER, due INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 1, released INTEGER NOT NULL DEFAULT 0, policy TEXT NOT NULL, model TEXT, last_seen INTEGER, acknowledged_revision INTEGER NOT NULL DEFAULT 0, enforcement_status TEXT NOT NULL DEFAULT 'not_enrolled', enrollment_consent INTEGER NOT NULL DEFAULT 0, identifiers BLOB);
    CREATE TABLE IF NOT EXISTS enrollment_retries(request_key TEXT PRIMARY KEY,device_id TEXT NOT NULL REFERENCES devices(id),token_hash TEXT NOT NULL,expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS claims(id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id), request_key TEXT NOT NULL, amount INTEGER NOT NULL, bank_reference TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending', submitted INTEGER NOT NULL, reviewed INTEGER, reviewer TEXT, reason TEXT, receipt_reference TEXT, UNIQUE(device_id,request_key));
    CREATE UNIQUE INDEX IF NOT EXISTS one_pending_claim ON claims(device_id) WHERE state='pending';
    CREATE UNIQUE INDEX IF NOT EXISTS one_receipt ON claims(receipt_reference) WHERE receipt_reference IS NOT NULL;
    CREATE TABLE IF NOT EXISTS support(id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id), message TEXT NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'open');
    CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY, request_key TEXT UNIQUE NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, created INTEGER NOT NULL, sender TEXT NOT NULL REFERENCES admins(id));
    CREATE TABLE IF NOT EXISTS message_deliveries(id INTEGER PRIMARY KEY AUTOINCREMENT, message_id TEXT NOT NULL REFERENCES messages(id), device_id TEXT NOT NULL REFERENCES devices(id), delivered INTEGER, read_at INTEGER, UNIQUE(message_id,device_id));
    CREATE INDEX IF NOT EXISTS message_device ON message_deliveries(device_id,id);
    CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS control_requests(request_key TEXT PRIMARY KEY,payload TEXT NOT NULL,result TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS fleet_requests(request_key TEXT PRIMARY KEY, policy TEXT NOT NULL, affected INTEGER NOT NULL, created INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, subject TEXT NOT NULL, detail TEXT NOT NULL);
    ''')

app = FastAPI(title='Loop Phone Manager', docs_url='/api/docs' if DEV else None, redoc_url=None)
app.mount('/static', StaticFiles(directory=ROOT / 'static'), name='static')

def now(): return int(time.time())
def digest(value): return hashlib.sha256(value.encode()).hexdigest()
def uid(): return secrets.token_urlsafe(18)
def audit(c, actor, action, subject, detail=''):
    c.execute('INSERT INTO audit(at,actor,action,subject,detail) VALUES(?,?,?,?,?)', (now(), actor, action, subject, detail))
def fail(status, text): raise HTTPException(status, text)

@app.middleware('http')
async def security(request: Request, call_next):
    if request.method not in ('GET','HEAD','OPTIONS') and request.headers.get('origin') and request.headers['origin'] != PUBLIC_URL:
        return Response('Cross-origin writes are not permitted', status_code=403)
    response = await call_next(request)
    response.headers['Cache-Control'] = 'no-store'
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-Frame-Options'] = 'DENY'
    response.headers['Referrer-Policy'] = 'no-referrer'
    response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
    if not DEV: response.headers['Strict-Transport-Security'] = 'max-age=31536000'
    return response

def admin(request: Request):
    token = request.cookies.get('loop_session', '')
    with db() as c:
        row = c.execute('SELECT a.id,a.email,a.role FROM sessions s JOIN admins a ON a.id=s.admin_id WHERE s.token=? AND s.expires>?', (digest(token), now())).fetchone()
    if not row: fail(401, 'Sign in required')
    return dict(row)

def owner(a=Depends(admin)):
    if a['role'] != 'owner': fail(403, 'Owner permission required')
    return a

def device(request: Request):
    authorization = request.headers.get('authorization', '')
    if not authorization.startswith('Bearer '): fail(401, 'Device authentication required')
    with db() as c:
        row = c.execute('SELECT * FROM devices WHERE token=?', (digest(authorization[7:]),)).fetchone()
    if not row: fail(401, 'Invalid device credentials')
    return dict(row)

def password_hash(password, salt=None):
    salt = salt or secrets.token_hex(16)
    return salt + ':' + hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=16384, r=8, p=1).hex()

def totp(secret, step):
    key = base64.b32decode(secret + '=' * (-len(secret) % 8))
    mac = hmac.new(key, struct.pack('>Q', step), hashlib.sha1).digest()
    offset = mac[-1] & 15
    return f'{(struct.unpack(">I",mac[offset:offset+4])[0] & 0x7fffffff) % 1000000:06d}'

class Login(BaseModel):
    email: str = Field(max_length=254)
    password: str = Field(max_length=256)
    code: str = Field(pattern=r'^\d{6}$')

attempts = {}
attempt_lock = threading.Lock()
@app.post('/api/auth/login')
def login(body: Login, request: Request, response: Response):
    address = request.client.host if request.client else 'unknown'
    with attempt_lock:
        global attempts
        attempts = {k:v for k,v in attempts.items() if v[1] > now() - 600}
        count, at = attempts.get(address, (0, now()))
        if count >= 10: fail(429, 'Too many sign-in attempts; try again later')
        attempts[address] = (count + 1, now())
    with db() as c:
        a = c.execute('SELECT * FROM admins WHERE email=?', (body.email.strip().lower(),)).fetchone()
        stored = a['password'] if a else '0'*32 + ':' + '0'*128
        valid = hmac.compare_digest(stored, password_hash(body.password, stored.split(':')[0]))
        step = now() // 30
        accepted = next((s for s in (step-1, step, step+1) if a and s > a['last_step'] and hmac.compare_digest(totp(a['totp'], s), body.code)), None)
        if not a or not valid or accepted is None: fail(401, 'Email, password or authenticator code is incorrect')
        c.execute('UPDATE admins SET last_step=? WHERE id=?', (accepted, a['id']))
        token = secrets.token_urlsafe(40)
        c.execute('DELETE FROM sessions WHERE expires<?', (now(),))
        c.execute('INSERT INTO sessions VALUES(?,?,?)', (digest(token), a['id'], now()+8*3600))
        audit(c, a['id'], 'sign_in', a['id'])
    response.set_cookie('loop_session', token, httponly=True, secure=not DEV, samesite='strict', max_age=8*3600, path='/')
    return {'email': a['email'], 'role': a['role']}

@app.post('/api/auth/logout')
def logout(request: Request, response: Response):
    with db() as c: c.execute('DELETE FROM sessions WHERE token=?', (digest(request.cookies.get('loop_session','')),))
    response.delete_cookie('loop_session', path='/')
    return {'ok': True}

@app.get('/api/auth/me')
def me(a=Depends(admin)): return a

class Policy(BaseModel):
    manual_lock: bool = False
    grace_until: int = Field(default=0,ge=0)
    enforcement_enabled: bool = False
    block_factory_reset: bool = False
    block_uninstall: bool = False
    block_wifi_changes: bool = False
    block_notification_center: bool = True
    block_navigation: bool = True
    block_outgoing_calls: bool = True
    banking_packages: list[str] = Field(default_factory=list, max_length=20)
    grace_hours: int = Field(default=24, ge=0, le=168)
    update_window_start: int = Field(default=120, ge=0, le=1439)
    update_window_end: int = Field(default=240, ge=0, le=1439)
    @field_validator('banking_packages')
    @classmethod
    def packages(cls, values):
        import re
        if any(not re.fullmatch(r'[a-zA-Z][\w]*(?:\.[a-zA-Z][\w]*)+', v) for v in values): raise ValueError('Use verified Android package names')
        return list(dict.fromkeys(values))

class Enrollment(BaseModel):
    name: str = Field(min_length=3, max_length=150)
    identity: str = Field(default="", max_length=40)
    phone: str = Field(min_length=8, max_length=25)
    consent: Literal[True] = True
    identity_verified: bool = False
    verification_note: str = Field(default="Identity not collected or verified", max_length=1000)
    policy: Policy | None = None
    @field_validator('name','phone')
    @classmethod
    def nonblank(cls, value):
        if not value.strip(): raise ValueError('Must not be blank')
        return value.strip()

@app.post('/api/admin/enrollments')
def create_enrollment(body: Enrollment, a=Depends(owner)):
    cid, did, token = uid(), uid(), secrets.token_urlsafe(32)
    ref = 'LOOP-' + secrets.token_hex(5).upper()
    chosen_policy=body.policy or Policy.model_validate(json.loads((ROOT/'enrollment-defaults.json').read_text()))
    if body.identity_verified and (len(body.identity.strip())<3 or len(body.verification_note.strip())<10): fail(422,'Identity evidence is required to mark identity verified')
    if chosen_policy.enforcement_enabled and not chosen_policy.banking_packages:
        fail(422, 'Verify and configure banking app package names before enabling restrictions')
    with db() as c:
        c.execute('INSERT INTO customers VALUES(?,?,?,?,?,?,?,?,?)', (cid, body.name, cipher.encrypt(body.identity.encode()), body.phone, ref, int(body.identity_verified), body.verification_note, 1, now()))
        c.execute('INSERT INTO devices(id,customer_id,enrollment,enrollment_expires,due,policy) VALUES(?,?,?,?,?,?)', (did,cid,digest(token),now()+1800,now()+30*DAY,chosen_policy.model_dump_json()))
        audit(c,a['id'],'create_enrollment',did,'Customer agreement attested by administrator; token expires in 30 minutes. Identity verified: '+str(body.identity_verified))
    return {'device_id':did,'enrollment_token':token,'expires_in':1800,'server_url':PUBLIC_URL,'public_key':PUBLIC_KEY,'reference':ref}

class DeviceIdentifiers(BaseModel):
    imeis: list[str] = Field(default_factory=list,max_length=4)
    sim_operator: str = Field(default='',max_length=100)
    sim_country: str = Field(default='',max_length=10)
    status: Literal['available','permission_denied','unsupported'] = 'unsupported'
    @field_validator('imeis')
    @classmethod
    def imei_format(cls,values):
        if any(not (v.isdigit() and 14<=len(v)<=17) for v in values): raise ValueError('Invalid IMEI format')
        return values

class Exchange(BaseModel):
    enrollment_token: str = Field(min_length=20,max_length=100)
    model: str = Field(min_length=1,max_length=150)
    consent: Literal[True]
    identifiers: DeviceIdentifiers = Field(default_factory=DeviceIdentifiers)
    exchange_nonce: str | None = Field(default=None,min_length=16,max_length=100)
    consent_source: Literal["on_device","administrator_recorded"] = "on_device"

@app.post('/api/device/enroll')
def exchange(body: Exchange):
    retry_key=digest(body.enrollment_token+':'+body.exchange_nonce) if body.exchange_nonce else None
    token=base64.urlsafe_b64encode(hmac.new(ENROLLMENT_HMAC_KEY,retry_key.encode(),hashlib.sha256).digest()).decode().rstrip('=') if retry_key else secrets.token_urlsafe(40)
    with db() as c:
        if retry_key:
            saved=c.execute('SELECT * FROM enrollment_retries WHERE request_key=? AND expires>?',(retry_key,now())).fetchone()
            if saved and hmac.compare_digest(saved['token_hash'],digest(token)): return {'device_id':saved['device_id'],'device_token':token,'public_key':PUBLIC_KEY}
        d = c.execute('SELECT * FROM devices WHERE enrollment=? AND enrollment_expires>?', (digest(body.enrollment_token),now())).fetchone()
        if not d: fail(401,'Enrollment code expired or already used')
        c.execute('UPDATE devices SET token=?, enrollment=NULL, enrollment_expires=NULL,model=?,due=?,last_seen=?,enrollment_consent=1,enforcement_status=?,identifiers=? WHERE id=?', (digest(token),body.model,now()+30*DAY,now(),'awaiting_device_report',cipher.encrypt(body.identifiers.model_dump_json().encode()),d['id']))
        if retry_key:c.execute('INSERT INTO enrollment_retries VALUES(?,?,?,?)',(retry_key,d['id'],digest(token),d['enrollment_expires']))
        audit(c,d['id'],'enrolled',d['id'],'Consent source: '+body.consent_source)
    return {'device_id':d['id'],'device_token':token,'public_key':PUBLIC_KEY}

class Ack(BaseModel):
    revision: int = Field(ge=0)
    status: Literal['unrestricted','restricted','released','unsupported','needs_online_check','policy_error','restricted_banking_unavailable']

@app.post('/api/device/sync')
def sync(body: Ack, d=Depends(device)):
    with db() as c:
        d = dict(c.execute('SELECT * FROM devices WHERE id=?',(d['id'],)).fetchone())
        if body.revision > d['revision']: fail(409,'Acknowledgement is newer than server revision')
        if body.revision >= d['acknowledged_revision']:
            c.execute('UPDATE devices SET last_seen=?,acknowledged_revision=?,enforcement_status=? WHERE id=?',(now(),body.revision,body.status,d['id']))
        else:
            c.execute('UPDATE devices SET last_seen=? WHERE id=?',(now(),d['id']))
        customer = c.execute('SELECT name,phone,created,reference FROM customers WHERE id=?',(d['customer_id'],)).fetchone()
        claims = [dict(r) for r in c.execute('SELECT id,amount,state,submitted,reviewed,reason FROM claims WHERE device_id=? ORDER BY submitted DESC LIMIT 30',(d['id'],))]
        bank_row=c.execute("SELECT value FROM settings WHERE key='banking'").fetchone()
        bank_details=json.loads(bank_row['value']) if bank_row else {'bank':os.environ.get('LOOP_EFT_BANK',''),'account_holder':os.environ.get('LOOP_EFT_HOLDER',''),'account_number':os.environ.get('LOOP_EFT_ACCOUNT',''),'branch_code':os.environ.get('LOOP_EFT_BRANCH','')}
        inbox = [dict(r) for r in c.execute('SELECT x.id,m.title,m.body,m.created,x.delivered,x.read_at FROM message_deliveries x JOIN messages m ON m.id=x.message_id WHERE x.device_id=? ORDER BY (x.delivered IS NOT NULL),CASE WHEN x.delivered IS NULL THEN x.id ELSE -x.id END LIMIT 100',(d['id'],))]
    payload = {'device_id':d['id'],'revision':d['revision'],'server_time':now(),'due':d['due'],'released':bool(d['released']),'policy':json.loads(d['policy']),'customer_name':customer['name'],'customer_phone':customer['phone'],'account_created':customer['created'],'reference':customer['reference'],'amount':300,'currency':'ZAR','support_email':'basanisilinda@gmail.com','claims':claims,'messages':inbox,'eft':{k:bank_details.get(k,'') for k in ('bank','account_holder','account_number','branch_code')}}
    raw = json.dumps(payload,separators=(',',':'),sort_keys=True,ensure_ascii=False).encode()
    return {'payload':base64.b64encode(raw).decode(),'signature':base64.b64encode(signing_key.sign(raw)).decode()}

class Claim(BaseModel):
    request_key: str = Field(min_length=16,max_length=100)
    bank_reference: str = Field(min_length=3,max_length=100)

@app.post('/api/device/claims')
def claim(body: Claim, d=Depends(device)):
    with db() as c:
        if c.execute('SELECT released FROM devices WHERE id=?',(d['id'],)).fetchone()['released']: fail(409,'Account has been released')
        existing = c.execute('SELECT id,state FROM claims WHERE device_id=? AND (request_key=? OR state=\'pending\')',(d['id'],body.request_key)).fetchone()
        if existing: return dict(existing)
        claim_id=uid()
        c.execute('INSERT INTO claims(id,device_id,request_key,amount,bank_reference,submitted) VALUES(?,?,?,?,?,?)',(claim_id,d['id'],body.request_key,300,body.bank_reference,now()))
        audit(c,d['id'],'payment_claimed',claim_id,'Claim only; no entitlement granted')
    return {'id':claim_id,'state':'pending'}

@app.get('/api/admin/overview')
def overview(a=Depends(admin)):
    with db() as c:
        devices=[dict(r) for r in c.execute('SELECT d.id,c.name,c.phone,c.reference,c.verified,d.due,d.revision,d.released,d.model,d.last_seen,d.acknowledged_revision,d.enforcement_status,d.policy FROM devices d JOIN customers c ON c.id=d.customer_id ORDER BY c.created DESC')]
        claims=[dict(r) for r in c.execute('SELECT p.*,c.name,c.reference FROM claims p JOIN devices d ON d.id=p.device_id JOIN customers c ON c.id=d.customer_id ORDER BY p.submitted DESC LIMIT 200')]
        events=[dict(r) for r in c.execute('SELECT at,actor,action,subject,detail FROM audit ORDER BY id DESC LIMIT 100')]
    with db() as c:
        tickets=[dict(r) for r in c.execute('SELECT s.*,c.name,c.reference FROM support s JOIN devices d ON d.id=s.device_id JOIN customers c ON c.id=d.customer_id ORDER BY s.created DESC LIMIT 100')]
    with db() as c:
        messages=[dict(r) for r in c.execute('SELECT m.id,m.title,m.body,m.created,COUNT(x.id) AS recipients,SUM(x.delivered IS NOT NULL) AS delivered,SUM(x.read_at IS NOT NULL) AS read_count FROM messages m LEFT JOIN message_deliveries x ON x.message_id=m.id GROUP BY m.id ORDER BY m.created DESC LIMIT 100')]
    return {'devices':devices,'claims':claims,'audit':events,'support':tickets,'messages':messages,'server_time':now()}

class Review(BaseModel):
    decision: Literal['approve','reject']
    reason: str = Field(min_length=5,max_length=1000)
    receipt_reference: str = Field(default='',max_length=100)

@app.post('/api/admin/claims/{claim_id}/review')
def review(claim_id: str, body: Review, a=Depends(admin)):
    if a['role'] not in ('owner','reviewer'): fail(403,'Payment reviewer permission required')
    receipt=body.receipt_reference.strip()
    if body.decision=='approve' and len(receipt)<5: fail(422,'A unique settled bank transaction reference is required')
    with db() as c:
        p=c.execute('SELECT * FROM claims WHERE id=?',(claim_id,)).fetchone()
        if not p: fail(404,'Claim not found')
        if p['state']!='pending': fail(409,'This claim has already been reviewed')
        d=c.execute('SELECT * FROM devices WHERE id=?',(p['device_id'],)).fetchone()
        if d['released']: fail(409,'Account has been released')
        if body.decision=='approve':
            if c.execute('SELECT 1 FROM claims WHERE receipt_reference=?',(receipt,)).fetchone(): fail(409,'This bank transaction was already used')
            # Renew from the later of current entitlement or approval, preserving paid days.
            policy=json.loads(d['policy']);policy.update(manual_lock=False,grace_until=0)
            c.execute('UPDATE devices SET due=?,policy=?,revision=revision+1 WHERE id=?',(max(d['due'],now())+30*DAY,json.dumps(policy),d['id']))
        if body.decision=='reject': c.execute('UPDATE devices SET revision=revision+1 WHERE id=?',(d['id'],))
        c.execute('UPDATE claims SET state=?,reviewed=?,reviewer=?,reason=?,receipt_reference=? WHERE id=?',('approved' if body.decision=='approve' else 'rejected',now(),a['id'],body.reason,receipt if body.decision=='approve' else None,claim_id))
        audit(c,a['id'],'payment_'+body.decision,claim_id,body.reason)
    return {'state':'approved' if body.decision=='approve' else 'rejected','device_delivery':'pending_next_sync'}

class DeviceControl(BaseModel):
    action: Literal['lock','unlock','grace']
    request_key: str = Field(min_length=16,max_length=100)

@app.post('/api/admin/devices/{device_id}/control')
def control_device(device_id: str, body: DeviceControl, a=Depends(owner)):
    payload=json.dumps({'device_id':device_id,**body.model_dump()},sort_keys=True)
    with db() as c:
        saved=c.execute('SELECT payload,result FROM control_requests WHERE request_key=?',(body.request_key,)).fetchone()
        if saved:
            if saved['payload']!=payload: fail(409,'Request key reused for another command')
            return json.loads(saved['result'])
        d=c.execute('SELECT * FROM devices WHERE id=?',(device_id,)).fetchone()
        if not d: fail(404,'Device not found')
        if d['released']: fail(409,'Released device cannot be restricted')
        if not d['token']: fail(409,'Enroll the phone before sending a control')
        policy=json.loads(d['policy']);stamp=now()
        if body.action=='unlock' and stamp>=max(d['due']+policy.get('grace_hours',24)*3600,policy.get('grace_until',0)): fail(409,'Payment is overdue. Approve the settled EFT or give 24-hour grace.')
        policy.update(enforcement_enabled=True,manual_lock=body.action=='lock')
        if not policy.get('banking_packages'): policy['banking_packages']=json.loads((ROOT/'enrollment-defaults.json').read_text())['banking_packages']
        if body.action=='lock':policy['grace_until']=0
        elif body.action=='grace':policy['grace_until']=max(policy.get('grace_until',0),stamp+DAY)
        c.execute('UPDATE devices SET policy=?,revision=revision+1 WHERE id=?',(json.dumps(policy),device_id))
        result={'action':body.action,'delivery':'pending_next_sync','grace_until':policy.get('grace_until',0)}
        c.execute('INSERT INTO control_requests VALUES(?,?,?)',(body.request_key,payload,json.dumps(result)))
        audit(c,a['id'],'device_'+body.action,device_id)
    return result

@app.put('/api/admin/devices/{device_id}/policy')
def update_policy(device_id: str, body: Policy, a=Depends(owner)):
    if body.enforcement_enabled and not body.banking_packages: fail(422,'Banking packages must be verified before enforcement')
    with db() as c:
        d=c.execute('SELECT * FROM devices WHERE id=?',(device_id,)).fetchone()
        if not d: fail(404,'Device not found')
        if d['released']: fail(409,'Released device cannot be restricted again')
        updated=body.model_dump();prior=json.loads(d['policy']);updated.update(manual_lock=prior.get('manual_lock',False),grace_until=prior.get('grace_until',0))
        c.execute('UPDATE devices SET policy=?,revision=revision+1 WHERE id=?',(json.dumps(updated),device_id))
        audit(c,a['id'],'policy_updated',device_id,body.model_dump_json())
    return {'delivery':'pending_next_sync'}

class Release(BaseModel):
    reason: str = Field(min_length=10,max_length=1000)
    full_payoff_confirmed: Literal[True]

@app.post('/api/admin/devices/{device_id}/release')
def release(device_id: str, body: Release, a=Depends(owner)):
    with db() as c:
        d=c.execute('SELECT * FROM devices WHERE id=?',(device_id,)).fetchone()
        if not d: fail(404,'Device not found')
        if d['released']: return {'delivery':'pending_next_sync' if d['acknowledged_revision']<d['revision'] else d['enforcement_status']}
        c.execute('UPDATE devices SET released=1,revision=revision+1 WHERE id=?',(device_id,))
        c.execute("UPDATE claims SET state='rejected',reviewed=?,reviewer=?,reason='Account fully paid and released' WHERE device_id=? AND state='pending'",(now(),a['id'],device_id))
        audit(c,a['id'],'full_payoff_release',device_id,body.reason)
    return {'delivery':'pending_next_sync'}

class Provisioning(BaseModel):
    enrollment_token: str = Field(min_length=20,max_length=100)
    apk_url: str = Field(min_length=10,max_length=1000)
    apk_sha256: str = Field(pattern=r'^[A-Za-z0-9_-]{43}=?$')

@app.post('/api/admin/provisioning/qr')
def provisioning(body: Provisioning,a=Depends(owner)):
    import qrcode
    if not body.apk_url.startswith('https://') or not PUBLIC_URL.startswith('https://'): fail(422,'Provisioning requires HTTPS URLs')
    with db() as c:
        valid=c.execute('SELECT 1 FROM devices WHERE enrollment=? AND enrollment_expires>?',(digest(body.enrollment_token),now())).fetchone()
    if not valid: fail(401,'Enrollment token expired or used')
    payload={'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME':'za.co.loopmanager/.LoopAdminReceiver','android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION':body.apk_url,'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM':body.apk_sha256.rstrip('='),'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED':True,'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE':{'server_url':PUBLIC_URL,'enrollment_token':body.enrollment_token,'public_key':PUBLIC_KEY}}
    output=io.BytesIO()
    qrcode.make(json.dumps(payload)).save(output,format='PNG')
    output.seek(0)
    return StreamingResponse(output,media_type='image/png')

@app.get('/health')
def health(): return {'status':'ok'}

@app.get('/')
def index(): return FileResponse(ROOT/'static'/'index.html')

class SupportMessage(BaseModel):
    message: str = Field(min_length=5,max_length=2000)

@app.post('/api/device/support')
def support_message(body: SupportMessage, d=Depends(device)):
    with db() as c:
        if c.execute('SELECT COUNT(*) FROM support WHERE device_id=? AND created>?',(d['id'],now()-3600)).fetchone()[0]>=10: fail(429,'Too many messages. Please wait before sending another.')
        sid=uid()
        c.execute('INSERT INTO support(id,device_id,message,created) VALUES(?,?,?,?)',(sid,d['id'],body.message,now()))
        audit(c,d['id'],'support_message',sid)
    return {'id':sid,'state':'open'}

@app.post('/api/admin/support/{ticket_id}/resolve')
def resolve_support(ticket_id: str, a=Depends(admin)):
    if a['role']=='viewer': fail(403,'Staff permission required')
    with db() as c:
        if not c.execute('UPDATE support SET state=\'resolved\' WHERE id=?',(ticket_id,)).rowcount: fail(404,'Support message not found')
        audit(c,a['id'],'support_resolved',ticket_id)
    return {'state':'resolved'}

@app.get('/api/admin/devices/{device_id}/identifiers')
def identifiers(device_id: str, a=Depends(owner)):
    with db() as c:
        d=c.execute('SELECT identifiers FROM devices WHERE id=?',(device_id,)).fetchone()
        if not d: fail(404,'Device not found')
        audit(c,a['id'],'device_identifiers_viewed',device_id)
    return json.loads(cipher.decrypt(d['identifiers'])) if d['identifiers'] else {'status':'unsupported','imeis':[]}


class Broadcast(BaseModel):
    request_key: str = Field(min_length=16,max_length=100)
    title: str = Field(min_length=3,max_length=100)
    message: str = Field(min_length=3,max_length=2000)
    device_ids: list[str] = Field(default_factory=list,max_length=500)
    all_active: bool = False

@app.post('/api/admin/messages')
def broadcast(body: Broadcast, a=Depends(admin)):
    if a['role'] not in ('owner','reviewer'): fail(403,'Staff messaging permission required')
    if body.all_active and body.device_ids: fail(422,'Choose all active phones or individual recipients')
    if not body.all_active and not body.device_ids: fail(422,'Select at least one recipient')
    with db() as c:
        existing=c.execute('SELECT id,title,body FROM messages WHERE request_key=?',(body.request_key,)).fetchone()
        if existing:
            if existing['title']!=body.title or existing['body']!=body.message: fail(409,'Request key already used for a different message')
            count=c.execute('SELECT COUNT(*) FROM message_deliveries WHERE message_id=?',(existing['id'],)).fetchone()[0]
            return {'id':existing['id'],'recipients':count,'delivery':'queued'}
        devices=c.execute('SELECT id FROM devices WHERE released=0 AND token IS NOT NULL').fetchall()
        if body.all_active: recipients=[d['id'] for d in devices]
        else:
            available={d['id'] for d in devices}
            recipients=list(dict.fromkeys(body.device_ids))
            if any(d not in available for d in recipients): fail(422,'Recipients must be enrolled, active devices')
        if not recipients: fail(409,'There are no enrolled active phones to message')
        mid=uid()
        c.execute('INSERT INTO messages VALUES(?,?,?,?,?,?)',(mid,body.request_key,body.title,body.message,now(),a['id']))
        c.executemany('INSERT INTO message_deliveries(message_id,device_id) VALUES(?,?)',[(mid,d) for d in recipients])
        audit(c,a['id'],'message_queued',mid,str(len(recipients))+' recipients')
    return {'id':mid,'recipients':len(recipients),'delivery':'queued'}

class MessageReceipt(BaseModel):
    ids: list[int] = Field(min_length=1,max_length=100)
    state: Literal['delivered','read']

@app.post('/api/device/messages/receipt')
def message_receipt(body: MessageReceipt, d=Depends(device)):
    with db() as c:
        for mid in body.ids:
            # A handset can only acknowledge its own deliveries.
            if body.state=='read':
                c.execute('UPDATE message_deliveries SET delivered=COALESCE(delivered,?),read_at=COALESCE(read_at,?) WHERE id=? AND device_id=?',(now(),now(),mid,d['id']))
            else:c.execute('UPDATE message_deliveries SET delivered=COALESCE(delivered,?) WHERE id=? AND device_id=?',(now(),mid,d['id']))
    return {'ok':True}

class EventWait(BaseModel):
    cursor: str = Field(default='',max_length=100)
    wait_seconds: int = Field(default=10,ge=0,le=10)

@app.post('/api/device/events')
async def device_events(body: EventWait, request: Request, d=Depends(device)):
    deadline=time.monotonic()+body.wait_seconds
    while True:
        # Short read transaction; never hold a database connection across a wait.
        with db() as c:
            current=c.execute('SELECT revision,token,released FROM devices WHERE id=?',(d['id'],)).fetchone()
            if not current or current['token']!=d['token']: fail(401,'Device credentials revoked')
            message_state=c.execute('SELECT COALESCE(MAX(id),0),COALESCE(MIN(CASE WHEN delivered IS NULL THEN id END),0) FROM message_deliveries WHERE device_id=?',(d['id'],)).fetchone()
        cursor=str(current['revision'])+':'+str(message_state[0])+':'+str(message_state[1])
        if cursor!=body.cursor or time.monotonic()>=deadline:
            return {'cursor':cursor,'changed':cursor!=body.cursor}
        if await request.is_disconnected(): return Response(status_code=204)
        await asyncio.sleep(.5)

class FleetPolicy(BaseModel):
    request_key: str = Field(min_length=16,max_length=100)
    policy: Policy
    apply_to_all_active: Literal[True]

@app.post('/api/admin/fleet/policy')
def fleet_policy(body: FleetPolicy, a=Depends(owner)):
    if body.policy.enforcement_enabled and not body.policy.banking_packages: fail(422,'Verify banking packages before enforcement')
    policy=body.policy.model_dump_json()
    with db() as c:
        existing=c.execute('SELECT * FROM fleet_requests WHERE request_key=?',(body.request_key,)).fetchone()
        if existing:
            if existing['policy']!=policy: fail(409,'Request key already used for a different policy')
            return {'affected':existing['affected'],'delivery':'pending_device_acknowledgements'}
        devices=c.execute('SELECT id,policy FROM devices WHERE released=0').fetchall()
        count=len(devices)
        for device in devices:
            updated=json.loads(policy);prior=json.loads(device['policy']);updated.update(manual_lock=prior.get('manual_lock',False),grace_until=prior.get('grace_until',0))
            c.execute('UPDATE devices SET policy=?,revision=revision+1 WHERE id=?',(json.dumps(updated),device['id']))
        c.execute('INSERT INTO fleet_requests VALUES(?,?,?,?)',(body.request_key,policy,count,now()))
        audit(c,a['id'],'fleet_policy_updated',body.request_key,str(count)+' devices; '+policy)
    return {'affected':count,'delivery':'pending_device_acknowledgements'}

class BankingSettings(BaseModel):
    bank: str = Field(min_length=2,max_length=100)
    account_holder: str = Field(min_length=2,max_length=150)
    account_number: str = Field(pattern=r'^\d{6,20}$')
    branch_code: str = Field(pattern=r'^\d{6}$')
    details_verified: Literal[True]
    verification_note: str = Field(min_length=10,max_length=1000)

@app.get('/api/admin/banking')
def banking_get(a=Depends(owner)):
    with db() as c:
        r=c.execute("SELECT value FROM settings WHERE key='banking'").fetchone()
    return json.loads(r['value']) if r else {'bank':'','account_holder':'','account_number':'','branch_code':''}

@app.put('/api/admin/banking')
def banking_put(body: BankingSettings,a=Depends(owner)):
    with db() as c:
        c.execute("INSERT INTO settings(key,value) VALUES('banking',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",(body.model_dump_json(),))
        c.execute('UPDATE devices SET revision=revision+1 WHERE released=0')
        audit(c,a['id'],'banking_details_updated','banking',body.verification_note)
    return {'delivery':'pending_device_acknowledgements'}


APK_PATH = Path(os.environ.get('LOOP_APK_PATH', ROOT / 'downloads' / 'LoopPhoneManager.apk'))

@app.get('/downloads/client.apk')
def download_client():
    if not APK_PATH.is_file(): fail(404,'Signed client APK has not been installed on this server')
    return FileResponse(APK_PATH,media_type='application/vnd.android.package-archive',filename='LoopPhoneManager.apk')

@app.get('/api/admin/provisioning/apk')
def apk_configuration(a=Depends(owner)):
    if not APK_PATH.is_file(): return {'available':False}
    checksum=base64.urlsafe_b64encode(hashlib.sha256(APK_PATH.read_bytes()).digest()).decode().rstrip('=')
    return {'available':True,'apk_url':PUBLIC_URL+'/downloads/client.apk','apk_sha256':checksum}


@app.post('/api/admin/devices/{device_id}/enrollment')
def reissue_enrollment(device_id: str,a=Depends(owner)):
    token=secrets.token_urlsafe(32)
    policy=Policy.model_validate(json.loads((ROOT/'enrollment-defaults.json').read_text()))
    with db() as c:
        d=c.execute('SELECT * FROM devices WHERE id=?',(device_id,)).fetchone()
        if not d:fail(404,'Device not found')
        if d['token'] or d['released']:fail(409,'Phone is already enrolled; update its app instead')
        c.execute('UPDATE devices SET enrollment=?,enrollment_expires=?,policy=?,revision=revision+1 WHERE id=?',(digest(token),now()+1800,policy.model_dump_json(),device_id))
        customer=c.execute('SELECT reference FROM customers WHERE id=?',(d['customer_id'],)).fetchone()
        audit(c,a['id'],'enrollment_reissued',device_id,'Existing customer retained; new one-time QR')
    return {'device_id':device_id,'enrollment_token':token,'expires_in':1800,'server_url':PUBLIC_URL,'public_key':PUBLIC_KEY,'reference':customer['reference']}
