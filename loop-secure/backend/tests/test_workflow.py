import base64
import concurrent.futures
import importlib
import json
import os
import tempfile
import unittest
from unittest.mock import patch

TEMP=tempfile.TemporaryDirectory()
os.environ['LOOP_DATA_DIR']=TEMP.name
os.environ['LOOP_PUBLIC_URL']='https://loop.example'
from backend import app as m
from fastapi.testclient import TestClient

class Workflow(unittest.TestCase):
    def setUp(self):
        with m.db() as c:
            for table in ['control_requests','enrollment_retries','settings','message_deliveries','messages','fleet_requests','support','audit','claims','devices','customers','sessions','admins']: c.execute('DELETE FROM '+table)
            self.admin_id=m.uid()
            self.secret=base64.b32encode(b'unit-test-totp-key-20').decode().rstrip('=')
            c.execute('INSERT INTO admins(id,email,password,totp,role) VALUES(?,?,?,?,?)',(self.admin_id,'owner@example.com',m.password_hash('long-test-password'),self.secret,'owner'))
        m.attempts.clear()
        self.client=TestClient(m.app,base_url='https://loop.example')
        r=self.client.post('/api/auth/login',json={'email':'owner@example.com','password':'long-test-password','code':m.totp(self.secret,m.now()//30)})
        self.assertEqual(r.status_code,200,r.text)

    def enroll(self):
        r=self.client.post('/api/admin/enrollments',json={'name':'Test Customer','identity':'TEST-ID-12345','phone':'+27630000000','consent':True,'identity_verified':True,'verification_note':'Original document checked in person','policy':{'enforcement_enabled':False,'grace_hours':24}})
        self.assertEqual(r.status_code,200,r.text)
        enrollment=r.json()
        d=self.client.post('/api/device/enroll',json={'enrollment_token':enrollment['enrollment_token'],'model':'Target handset pending verification','consent':True,'identifiers':{'imeis':['123456789012345'],'sim_operator':'Test carrier','sim_country':'za','status':'available'}})
        self.assertEqual(d.status_code,200,d.text)
        headers={'Authorization':'Bearer '+d.json()['device_token']}
        return enrollment,headers

    def sync(self,headers,revision=0,status='needs_online_check'):
        r=self.client.post('/api/device/sync',headers=headers,json={'revision':revision,'status':status})
        self.assertEqual(r.status_code,200,r.text)
        raw=base64.b64decode(r.json()['payload'])
        m.signing_key.public_key().verify(base64.b64decode(r.json()['signature']),raw)
        return json.loads(raw)

    def claim(self,headers,key='unique-request-123456'):
        r=self.client.post('/api/device/claims',headers=headers,json={'request_key':key,'bank_reference':'Customer EFT reference'})
        self.assertEqual(r.status_code,200,r.text)
        return r.json()['id']

    def test_complete_payment_claim_review_and_ack(self):
        e,h=self.enroll()
        initial=self.sync(h)
        cid=self.claim(h)
        self.assertEqual(self.claim(h,'another-request-1234'),cid)
        self.assertEqual(self.sync(h)['due'],initial['due'])
        r=self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Confirmed bank funds settled','receipt_reference':'BANK-TXN-001'})
        self.assertEqual(r.status_code,200,r.text)
        paid=self.sync(h,1,'unrestricted')
        self.assertEqual(paid['due'],initial['due']+30*m.DAY)
        self.assertEqual(paid['revision'],2)
        overview=self.client.get('/api/admin/overview').json()['devices'][0]
        self.assertEqual(overview['acknowledged_revision'],1)
        self.sync(h,2,'unrestricted')
        overview=self.client.get('/api/admin/overview').json()['devices'][0]
        self.assertEqual(overview['acknowledged_revision'],2)
        repeat=self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Attempt duplicate review','receipt_reference':'BANK-TXN-001'})
        self.assertEqual(repeat.status_code,409)
        self.assertEqual(self.sync(h)['due'],paid['due'])

    def test_enrollment_one_time_and_expiry(self):
        e,h=self.enroll()
        self.assertEqual(self.client.post('/api/device/enroll',json={'enrollment_token':e['enrollment_token'],'model':'Other','consent':True}).status_code,401)
        with m.db() as c:c.execute('UPDATE devices SET token=NULL,enrollment=?,enrollment_expires=?',(m.digest('expired-enrollment-token'),m.now()-1))
        self.assertEqual(self.client.post('/api/device/enroll',json={'enrollment_token':'expired-enrollment-token','model':'Other','consent':True}).status_code,401)

    def test_auth_permissions_csrf_and_totp_replay(self):
        anonymous=TestClient(m.app,base_url='https://loop.example')
        self.assertEqual(anonymous.get('/api/admin/overview').status_code,401)
        self.assertEqual(self.client.post('/api/auth/logout',headers={'Origin':'https://attacker.example'}).status_code,403)
        r=anonymous.post('/api/auth/login',json={'email':'owner@example.com','password':'long-test-password','code':m.totp(self.secret,m.now()//30)})
        self.assertEqual(r.status_code,401)
        cookie=next(c for c in self.client.cookies.jar if c.name=='loop_session')
        self.assertTrue(cookie.secure)
        self.assertIn('HttpOnly',cookie._rest)
        self.assertEqual(cookie._rest.get('SameSite'),'strict')
        with m.db() as c:c.execute("UPDATE admins SET role='viewer' WHERE id=?",(self.admin_id,))
        self.assertEqual(self.client.post('/api/admin/enrollments',json={'name':'Test Customer','identity':'TEST-ID','phone':'0123456789','consent':True,'identity_verified':True,'verification_note':'Checked original document','policy':{}}).status_code,403)

    def test_encrypted_identity_and_hashed_device_credentials(self):
        e,h=self.enroll()
        with m.db() as c:
            person=c.execute('SELECT * FROM customers').fetchone()
            device=c.execute('SELECT * FROM devices').fetchone()
        self.assertNotIn(b'TEST-ID',person['identity'])
        self.assertEqual(m.cipher.decrypt(person['identity']),b'TEST-ID-12345')
        self.assertNotEqual(device['token'],h['Authorization'][7:])
        self.assertEqual(device['token'],m.digest(h['Authorization'][7:]))

    def test_approval_requires_unique_bank_receipt(self):
        e,h=self.enroll();cid=self.claim(h)
        r=self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Missing settlement evidence'})
        self.assertEqual(r.status_code,422)
        self.assertEqual(self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Bank receipt checked','receipt_reference':'BANK-UNIQUE'}).status_code,200)
        second=self.claim(h,'second-claim-1234567')
        self.assertEqual(self.client.post('/api/admin/claims/'+second+'/review',json={'decision':'approve','reason':'Reuse receipt blocked','receipt_reference':'BANK-UNIQUE'}).status_code,409)

    def test_release_is_permanent_and_cancels_pending(self):
        e,h=self.enroll();cid=self.claim(h)
        r=self.client.post('/api/admin/devices/'+e['device_id']+'/release',json={'reason':'Full balance confirmed as paid','full_payoff_confirmed':True})
        self.assertEqual(r.status_code,200)
        self.assertTrue(self.sync(h)['released'])
        self.assertEqual(self.client.put('/api/admin/devices/'+e['device_id']+'/policy',json={}).status_code,409)
        self.assertEqual(self.client.post('/api/device/claims',headers=h,json={'request_key':'released-claim-12345','bank_reference':'Test reference'}).status_code,409)
        self.assertEqual(self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Release already applied','receipt_reference':'BANK-FINAL'}).status_code,409)

    def test_device_identifiers_encrypted_and_owner_only(self):
        e,h=self.enroll()
        with m.db() as c:
            raw=c.execute('SELECT identifiers FROM devices').fetchone()['identifiers']
        self.assertNotIn(b'123456789012345',raw)
        r=self.client.get('/api/admin/devices/'+e['device_id']+'/identifiers')
        self.assertEqual(r.status_code,200)
        self.assertEqual(r.json()['imeis'],['123456789012345'])
        with m.db() as c:c.execute("UPDATE admins SET role='reviewer' WHERE id=?",(self.admin_id,))
        self.assertEqual(self.client.get('/api/admin/devices/'+e['device_id']+'/identifiers').status_code,403)

    def test_old_ack_does_not_replace_newer_status(self):
        e,h=self.enroll()
        self.client.put('/api/admin/devices/'+e['device_id']+'/policy',json={})
        self.sync(h,2,'unrestricted')
        self.sync(h,1,'restricted')
        d=self.client.get('/api/admin/overview').json()['devices'][0]
        self.assertEqual(d['acknowledged_revision'],2)
        self.assertEqual(d['enforcement_status'],'unrestricted')

    def test_rejection_does_not_extend_entitlement(self):
        e,h=self.enroll();initial=self.sync(h);cid=self.claim(h)
        self.assertEqual(self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'reject','reason':'Funds not received in bank'}).status_code,200)
        self.assertEqual(self.sync(h)['due'],initial['due'])

    def test_concurrent_approvals_grant_one_period(self):
        e,h=self.enroll();initial=self.sync(h);cid=self.claim(h)
        def approve(i):return self.client.post('/api/admin/claims/'+cid+'/review',json={'decision':'approve','reason':'Funds verified in bank','receipt_reference':'CONCURRENT-'+str(i)}).status_code
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: codes=list(pool.map(approve,[1,2]))
        self.assertEqual(sorted(codes),[200,409])
        self.assertEqual(self.sync(h)['due'],initial['due']+30*m.DAY)

    def test_support_message_reaches_admin(self):
        e,h=self.enroll()
        r=self.client.post('/api/device/support',headers=h,json={'message':'Please help verify my payment'})
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(self.client.get('/api/admin/overview').json()['support'][0]['message'],'Please help verify my payment')
        self.assertEqual(self.client.post('/api/admin/support/'+r.json()['id']+'/resolve').status_code,200)

    def test_acknowledgement_cannot_jump_ahead(self):
        e,h=self.enroll()
        self.assertEqual(self.client.post('/api/device/sync',headers=h,json={'revision':99999,'status':'unrestricted'}).status_code,409)

    def test_overdue_approval_starts_from_approval(self):
        e,h=self.enroll()
        with m.db() as c:c.execute('UPDATE devices SET due=?',(m.now()-10*m.DAY,))
        cid=self.claim(h)
        with patch.object(m,'now',return_value=2000000000):
            # Session would be expired at this future time; review directly exercises the transaction.
            m.review(cid,m.Review(decision='approve',reason='Late receipt checked',receipt_reference='LATE-BANK'),{'id':self.admin_id,'role':'owner'})
        self.assertEqual(self.sync(h)['due'],2000000000+30*m.DAY)

if __name__=='__main__':unittest.main()
