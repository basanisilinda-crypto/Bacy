import base64
import json
import unittest
from unittest.mock import patch
import test_workflow as base
from backend import app as m

class Messaging(unittest.TestCase):
    setUp=base.Workflow.setUp
    enroll=base.Workflow.enroll
    sync=base.Workflow.sync
    claim=base.Workflow.claim
    def test_broadcast_queued_delivered_read_and_idempotent(self):
        e,h=self.enroll()
        other,other_h=self.enroll()
        body={'request_key':'broadcast-test-123456','title':'Payment support','message':'Contact support if your payment is awaiting review.','all_active':True}
        sent=self.client.post('/api/admin/messages',json=body)
        self.assertEqual(sent.status_code,200,sent.text)
        self.assertEqual(sent.json()['recipients'],2)
        repeat=self.client.post('/api/admin/messages',json=body)
        self.assertEqual(repeat.json()['id'],sent.json()['id'])
        messages=self.sync(h)['messages']
        self.assertEqual(len(messages),1)
        self.assertIsNone(messages[0]['delivered'])
        self.assertEqual(self.client.post('/api/device/messages/receipt',headers=h,json={'ids':[messages[0]['id']],'state':'delivered'}).status_code,200)
        dashboard=self.client.get('/api/admin/overview').json()['messages'][0]
        self.assertEqual(dashboard['delivered'],1)
        self.assertEqual(dashboard['read_count'],0)
        self.client.post('/api/device/messages/receipt',headers=h,json={'ids':[messages[0]['id']],'state':'read'})
        dashboard=self.client.get('/api/admin/overview').json()['messages'][0]
        self.assertEqual(dashboard['read_count'],1)
        self.assertEqual(dashboard['recipients'],2)

    def test_targeted_message_isolation(self):
        e,h=self.enroll();other,oh=self.enroll()
        r=self.client.post('/api/admin/messages',json={'request_key':'private-message-123456','title':'Your account','message':'We are reviewing your EFT claim.','device_ids':[e['device_id']]})
        self.assertEqual(r.status_code,200,r.text)
        message=self.sync(h)['messages'][0]
        self.assertEqual(self.sync(oh)['messages'],[])
        self.client.post('/api/device/messages/receipt',headers=oh,json={'ids':[message['id']],'state':'read'})
        dashboard=self.client.get('/api/admin/overview').json()['messages'][0]
        self.assertEqual(dashboard['read_count'],0)
        self.assertEqual(dashboard['delivered'],0)

    def test_released_and_unenrolled_devices_excluded(self):
        e,h=self.enroll()
        self.client.post('/api/admin/devices/'+e['device_id']+'/release',json={'reason':'Fully paid account release','full_payoff_confirmed':True})
        r=self.client.post('/api/admin/messages',json={'request_key':'released-audience-12345','title':'Test audience','message':'No active recipients','all_active':True})
        self.assertEqual(r.status_code,409)
        r=self.client.post('/api/admin/messages',json={'request_key':'explicit-released-12345','title':'Test audience','message':'Must reject this recipient','device_ids':[e['device_id']]})
        self.assertEqual(r.status_code,422)

    def test_fleet_policy_excludes_release_and_retries_once(self):
        e,h=self.enroll();other,oh=self.enroll()
        self.client.post('/api/admin/devices/'+other['device_id']+'/release',json={'reason':'Fully paid account release','full_payoff_confirmed':True})
        before=self.sync(h)['revision'];released=self.sync(oh)['revision']
        body={'request_key':'fleet-policy-request-1234','policy':{'grace_hours':48},'apply_to_all_active':True}
        r=self.client.post('/api/admin/fleet/policy',json=body)
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['affected'],1)
        self.client.post('/api/admin/fleet/policy',json=body)
        self.assertEqual(self.sync(h)['revision'],before+1)
        self.assertEqual(self.sync(h)['policy']['grace_hours'],48)
        self.assertEqual(self.sync(oh)['revision'],released)
        body['policy']['grace_hours']=72
        self.assertEqual(self.client.post('/api/admin/fleet/policy',json=body).status_code,409)

    def test_event_cursor_changes_for_policy_and_message(self):
        e,h=self.enroll()
        first=self.client.post('/api/device/events',headers=h,json={'cursor':'','wait_seconds':0}).json()
        self.assertTrue(first['changed'])
        same=self.client.post('/api/device/events',headers=h,json={'cursor':first['cursor'],'wait_seconds':0}).json()
        self.assertFalse(same['changed'])
        self.client.post('/api/admin/messages',json={'request_key':'event-message-123456','title':'New message','message':'An online delivery test','device_ids':[e['device_id']]})
        changed=self.client.post('/api/device/events',headers=h,json={'cursor':first['cursor'],'wait_seconds':0}).json()
        self.assertTrue(changed['changed'])
        self.client.put('/api/admin/devices/'+e['device_id']+'/policy',json={'grace_hours':36})
        policy=self.client.post('/api/device/events',headers=h,json={'cursor':changed['cursor'],'wait_seconds':0}).json()
        self.assertTrue(policy['changed'])

    def test_viewer_cannot_message_or_change_fleet(self):
        e,h=self.enroll()
        with m.db() as c:c.execute("UPDATE admins SET role='viewer' WHERE id=?",(self.admin_id,))
        self.assertEqual(self.client.post('/api/admin/messages',json={'request_key':'viewer-message-12345','title':'Should fail','message':'No permission to send','all_active':True}).status_code,403)
        self.assertEqual(self.client.post('/api/admin/fleet/policy',json={'request_key':'viewer-fleet-123456','policy':{},'apply_to_all_active':True}).status_code,403)

    def test_verified_bank_change_updates_signed_devices(self):
        e,h=self.enroll();initial=self.sync(h)
        body={'bank':'Test business bank','account_holder':'Test Business','account_number':'1234567890','branch_code':'123456','details_verified':True,'verification_note':'Checked test account records'}
        r=self.client.put('/api/admin/banking',json=body)
        self.assertEqual(r.status_code,200,r.text)
        after=self.sync(h)
        self.assertEqual(after['revision'],initial['revision']+1)
        self.assertEqual(after['eft']['account_number'],'1234567890')
        self.assertNotIn('verification_note',after['eft'])
        with m.db() as c:c.execute("UPDATE admins SET role='viewer' WHERE id=?",(self.admin_id,))
        self.assertEqual(self.client.put('/api/admin/banking',json=body).status_code,403)

    def test_unicode_message_backlog_fits_handset_response_limit(self):
        e,h=self.enroll()
        with m.db() as c:
            for i in range(100):
                mid=m.uid()
                c.execute('INSERT INTO messages VALUES(?,?,?,?,?,?)',(mid,m.uid(),'Unicode account message','\U0001f4ec'*2000,m.now(),self.admin_id))
                c.execute('INSERT INTO message_deliveries(message_id,device_id) VALUES(?,?)',(mid,e['device_id']))
        response=self.client.post('/api/device/sync',headers=h,json={'revision':0,'status':'needs_online_check'})
        self.assertEqual(response.status_code,200)
        self.assertLess(len(response.content),2*1024*1024)
        payload=self.sync(h)
        self.assertEqual(len(payload['messages']),100)
        self.assertEqual(payload['messages'][0]['body'],'\U0001f4ec'*2000)

    def test_name_phone_only_uses_automatic_defaults_without_verified_identity(self):
        response=self.client.post('/api/admin/enrollments',json={'name':'Two Field Customer','phone':'+27630000000'})
        self.assertEqual(response.status_code,200,response.text)
        with m.db() as c:
            customer=c.execute('SELECT * FROM customers').fetchone()
            device=c.execute('SELECT * FROM devices').fetchone()
        self.assertEqual(customer['verified'],0)
        self.assertEqual(m.cipher.decrypt(customer['identity']),b'')
        policy=json.loads(device['policy'])
        self.assertTrue(policy['enforcement_enabled'])
        self.assertTrue(policy['block_factory_reset'])
        self.assertTrue(policy['block_uninstall'])
        self.assertTrue(policy['block_notification_center'])
        self.assertTrue(policy['block_navigation'])
        self.assertTrue(policy['block_outgoing_calls'])
        self.assertEqual(policy['grace_hours'],24)
        self.assertIn('capitec.acuity.mobile.prod',policy['banking_packages'])

    def test_activation_retry_returns_same_credentials_without_extending_deadline(self):
        response=self.client.post('/api/admin/enrollments',json={'name':'Retry Enrollment Customer','phone':'+27630000000'})
        enrollment=response.json()
        body={'enrollment_token':enrollment['enrollment_token'],'model':'Transaction fixture','consent':True,'exchange_nonce':'stable-enrollment-retry-nonce','consent_source':'administrator_recorded'}
        first=self.client.post('/api/device/enroll',json=body)
        self.assertEqual(first.status_code,200,first.text)
        h={'Authorization':'Bearer '+first.json()['device_token']}
        deadline=self.sync(h)['due']
        again=self.client.post('/api/device/enroll',json=body)
        self.assertEqual(again.status_code,200,again.text)
        self.assertEqual(first.json(),again.json())
        self.assertEqual(self.sync(h)['due'],deadline)
        body['exchange_nonce']='different-enrollment-retry-nonce'
        self.assertEqual(self.client.post('/api/device/enroll',json=body).status_code,401)

    def test_pending_qr_refresh_keeps_customer_and_invalidates_old_code(self):
        first=self.client.post('/api/admin/enrollments',json={'name':'Pending QR Customer','phone':'+27630000000'}).json()
        fresh=self.client.post('/api/admin/devices/'+first['device_id']+'/enrollment').json()
        self.assertEqual(first['reference'],fresh['reference'])
        self.assertNotEqual(first['enrollment_token'],fresh['enrollment_token'])
        with m.db() as c:self.assertEqual(c.execute('SELECT COUNT(*) FROM customers').fetchone()[0],1)
        self.assertEqual(self.client.post('/api/device/enroll',json={'enrollment_token':first['enrollment_token'],'model':'Fixture','consent':True}).status_code,401)
        self.assertEqual(self.client.post('/api/device/enroll',json={'enrollment_token':fresh['enrollment_token'],'model':'Fixture','consent':True}).status_code,200)
        self.assertEqual(self.client.post('/api/admin/devices/'+first['device_id']+'/enrollment').status_code,409)


    def control(self,device,action,key):
        return self.client.post('/api/admin/devices/'+device+'/control',json={'action':action,'request_key':key})

    def test_manual_lock_claim_and_payment_approval(self):
        e,h=self.enroll();before=self.sync(h)
        result=self.control(e['device_id'],'lock','manual-lock-12345678');self.assertEqual(result.status_code,200,result.text)
        locked=self.sync(h);self.assertTrue(locked['policy']['manual_lock']);self.assertTrue(locked['policy']['enforcement_enabled']);self.assertEqual(locked['due'],before['due'])
        self.control(e['device_id'],'lock','manual-lock-12345678');self.assertEqual(self.sync(h)['revision'],locked['revision'])
        claim=self.claim(h);self.assertTrue(self.sync(h)['policy']['manual_lock'])
        review=self.client.post('/api/admin/claims/'+claim+'/review',json={'decision':'approve','reason':'Settled EFT confirmed','receipt_reference':'CONTROL-TXN-001'})
        self.assertEqual(review.status_code,200,review.text);approved=self.sync(h)
        self.assertFalse(approved['policy']['manual_lock']);self.assertEqual(approved['policy']['grace_until'],0);self.assertEqual(approved['due'],before['due']+30*m.DAY)

    def test_unlock_requires_paid_time_or_explicit_grace(self):
        e,h=self.enroll();stamp=m.now()
        with m.db() as c:c.execute('UPDATE devices SET due=? WHERE id=?',(stamp-2*m.DAY,e['device_id']))
        self.assertEqual(self.control(e['device_id'],'unlock','unlock-overdue-12345').status_code,409)
        with patch.object(m,'now',return_value=stamp):
            granted=self.control(e['device_id'],'grace','grace-idempotent-1234');self.assertEqual(granted.status_code,200,granted.text)
        with patch.object(m,'now',return_value=stamp+200):
            retried=self.control(e['device_id'],'grace','grace-idempotent-1234');self.assertEqual(retried.json(),granted.json())
        state=self.sync(h);self.assertEqual(state['due'],stamp-2*m.DAY);self.assertEqual(state['policy']['grace_until'],stamp+m.DAY)
        self.assertEqual(self.control(e['device_id'],'unlock','unlock-in-grace-1234').status_code,200)
        self.assertEqual(self.control(e['device_id'],'lock','lock-after-grace-123').status_code,200)
        locked=self.sync(h);self.assertTrue(locked['policy']['manual_lock']);self.assertEqual(locked['policy']['grace_until'],0)
        self.assertEqual(self.control(e['device_id'],'unlock','unlock-denied-123456').status_code,409)

    def test_control_cannot_restrict_released_or_unenrolled_devices(self):
        e,h=self.enroll()
        self.client.post('/api/admin/devices/'+e['device_id']+'/release',json={'reason':'Paid off and ownership released','full_payoff_confirmed':True})
        self.assertEqual(self.control(e['device_id'],'lock','released-control-123').status_code,409)
        pending=self.client.post('/api/admin/enrollments',json={'name':'Pending Customer','phone':'0630000000'}).json()
        self.assertEqual(self.control(pending['device_id'],'lock','unenrolled-lock-1234').status_code,409)

    def test_advanced_policy_preserves_manual_lock(self):
        e,h=self.enroll();self.control(e['device_id'],'lock','preserved-lock-12345')
        response=self.client.put('/api/admin/devices/'+e['device_id']+'/policy',json={'enforcement_enabled':True,'banking_packages':['capitec.acuity.mobile.prod']})
        self.assertEqual(response.status_code,200,response.text);self.assertTrue(self.sync(h)['policy']['manual_lock'])
        response=self.client.post('/api/admin/fleet/policy',json={'request_key':'preserved-fleet-1234','apply_to_all_active':True,'policy':{'enforcement_enabled':True,'banking_packages':['capitec.acuity.mobile.prod']}})
        self.assertEqual(response.status_code,200,response.text);self.assertTrue(self.sync(h)['policy']['manual_lock'])

    def test_controls_are_owner_only(self):
        e,h=self.enroll()
        with m.db() as c:c.execute("UPDATE admins SET role='viewer' WHERE id=?",(self.admin_id,))
        self.assertEqual(self.control(e['device_id'],'lock','viewer-lock-1234567').status_code,403)

if __name__=='__main__':unittest.main()
