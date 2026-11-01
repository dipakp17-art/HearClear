import concurrent.futures
import json
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from server import ApiError, Handler, Server, Store, digest, price

class EntitlementTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1000000
        self.store = Store(Path(self.tmp.name) / 'test.sqlite3', 'owner@example', 'Owner', lambda: self.now)
        self.data = dict(account='alice', password='correct-password-123', device='a' * 64, consent=True)
        self.created = self.store.register(self.data)
        self.token = self.created['token']
    def tearDown(self):
        self.tmp.cleanup()
    def assertCode(self, code, action):
        with self.assertRaises(ApiError) as caught:
            action()
        self.assertEqual(code, caught.exception.code)
    def quote_and_submit(self, token=None, ref='123456789012'):
        token = token or self.token
        q = self.store.quote(token)
        self.store.receipt(token, dict(order=q['id'], reference=ref))
        return q
    def test_trial_exactly_48_hours_and_lease_clipped_to_end(self):
        self.assertEqual(172800, self.store.status(self.token)['trial_remaining_seconds'])
        self.now += 172799
        self.assertEqual(1, self.store.status(self.token)['lease_seconds'])
        self.now += 1
        self.assertEqual(0, self.store.status(self.token)['lease_seconds'])
    def test_new_account_on_same_device_cannot_reset_trial(self):
        self.now += 172801
        other = self.store.register(dict(self.data, account='bob1'))
        self.assertEqual(0, self.store.status(other['token'])['lease_seconds'])
    def test_no_device_fallback_and_transfer_requires_recovery(self):
        data = dict(self.data, device='b' * 64)
        self.assertCode('transfer_required', lambda: self.store.login(data))
        self.assertCode('unauthorized', lambda: self.store.login(data, transfer=True))
        data['recovery_code'] = self.created['recovery_code']
        new = self.store.login(data, transfer=True)
        self.assertCode('session_expired', lambda: self.store.status(self.token))
        self.assertEqual(172800, self.store.status(new['token'])['trial_remaining_seconds'])
        self.assertCode('transfer_required', lambda: self.store.login(self.data))
    def test_transfer_does_not_extend_trial(self):
        self.now += 170000
        new = self.store.login(dict(self.data, device='b' * 64, recovery_code=self.created['recovery_code']), transfer=True)
        self.assertEqual(2800, self.store.status(new['token'])['trial_remaining_seconds'])
    def test_new_phone_with_expired_trial_cannot_get_another(self):
        self.store.register(dict(self.data, account='bob1', device='b' * 64))
        self.now += 172900
        result = self.store.login(dict(self.data, device='b' * 64, recovery_code=self.created['recovery_code']), transfer=True)
        self.assertEqual(0, self.store.status(result['token'])['lease_seconds'])
    def test_price_boundaries_and_cap(self):
        self.assertEqual([50,50,100,100,150,150,200,200,250,250], [price(n) for n in [1,100,101,200,201,300,301,400,401,100000]])
    def test_reference_alone_does_not_unlock_and_amount_verified(self):
        q = self.quote_and_submit()
        self.now += 172801
        self.assertFalse(self.store.status(self.token)['paid'])
        self.assertEqual(0, self.store.status(self.token)['lease_seconds'])
        with self.assertRaises(ValueError):
            self.store.approve(q['id'], 1, '123456789012')
        self.store.approve(q['id'], 50, '123456789012')
        self.store.approve(q['id'], 50, '123456789012')
        self.assertTrue(self.store.status(self.token)['paid'])
        self.assertEqual(1, self.store.report()['paid'])
    def test_paid_account_transfer_is_free(self):
        q = self.quote_and_submit()
        self.store.approve(q['id'], 50, '123456789012')
        self.now += 10000000  # trial and old session expired; lifetime purchase survives
        new = self.store.login(dict(self.data, device='b' * 64, recovery_code=self.created['recovery_code']), transfer=True)
        self.assertTrue(self.store.status(new['token'])['paid'])
        self.assertEqual(75, self.store.status(new['token'])['lease_seconds'])
        self.assertCode('already_paid', lambda: self.store.quote(new['token']))
    def test_expired_quotes_are_released_and_cannot_be_submitted(self):
        q = self.store.quote(self.token)
        self.now += 1801
        self.assertCode('quote_expired', lambda: self.store.receipt(self.token, dict(order=q['id'], reference='123456789012')))
        new = self.store.quote(self.token)
        self.assertNotEqual(q['id'], new['id'])
        self.assertEqual(1, new['slot'])
    def test_duplicate_transaction_reference_rejected(self):
        self.quote_and_submit()
        other = self.store.register(dict(self.data, account='bob1', device='b' * 64))
        q = self.store.quote(other['token'])
        self.assertCode('reference_used', lambda: self.store.receipt(other['token'], dict(order=q['id'], reference='123456789012')))
    def test_pending_quote_is_stable_until_operator_resolution(self):
        q = self.quote_and_submit()
        self.now += 3600
        self.assertEqual(q['id'], self.store.quote(self.token)['id'])
        self.store.reject(q['id'])
        self.assertNotEqual(q['id'], self.store.quote(self.token)['id'])
    def test_quote_serialization_reuses_same_checkout(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            quotes = list(pool.map(lambda _: self.store.quote(self.token), range(8)))
        self.assertEqual(1, len({q['id'] for q in quotes}))
    def test_tier_reservation_cannot_oversell_slot_100(self):
        with self.store.db() as c:
            for n in range(1,100):
                c.execute('INSERT INTO orders VALUES (?,?,?,?,?,?,?,?)', (f'fake{n}', f'paid{n}', n,50,0,'approved',f'ref{n}',0))
        first = self.store.quote(self.token)
        other = self.store.register(dict(self.data, account='bob1', device='b' * 64))
        second = self.store.quote(other['token'])
        self.assertEqual((100,50), (first['slot'],first['amount']))
        self.assertEqual((101,100), (second['slot'],second['amount']))
    def test_recovery_changes_password_and_revokes_old_session(self):
        new_pw = 'new-secure-password-123'
        result = self.store.login(dict(self.data, password=new_pw, recovery_code=self.created['recovery_code']), recover=True)
        self.assertCode('session_expired', lambda: self.store.status(self.token))
        self.assertCode('unauthorized', lambda: self.store.login(self.data))
        self.assertEqual('alice', self.store.status(result['token'])['account'])
    def test_secrets_not_stored_plaintext(self):
        with self.store.db() as c:
            self.assertIsNone(c.execute('SELECT * FROM sessions WHERE token=?', (self.token,)).fetchone())
            row = c.execute('SELECT * FROM accounts').fetchone()
            self.assertNotEqual(self.data['password'], row['password'])
            self.assertNotEqual(self.created['recovery_code'], row['recovery'])
    def test_logout_revokes(self):
        self.store.logout(self.token)
        self.assertCode('session_expired', lambda: self.store.status(self.token))
    def test_wrong_password_no_access(self):
        self.assertCode('unauthorized', lambda: self.store.login(dict(self.data, password='wrong-password-123')))
    def test_payment_not_configured_fails_closed(self):
        self.store.upi = ''
        self.assertCode('payments_unconfigured', lambda: self.store.quote(self.token))
    def test_another_account_cannot_submit_or_approve_an_order(self):
        q = self.store.quote(self.token)
        other = self.store.register(dict(self.data, account='bob1', device='b' * 64))
        self.assertCode('order_missing', lambda: self.store.receipt(other['token'], dict(order=q['id'], reference='123456789012')))
        with self.assertRaises(ValueError):
            self.store.approve(q['id'], 50, '123456789012')
    def test_concurrent_transfers_leave_exactly_one_server_session(self):
        def transfer(device):
            return self.store.login(dict(self.data, device=device * 64, recovery_code=self.created['recovery_code']), transfer=True)['token']
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            tokens = list(pool.map(transfer, ['b', 'c']))
        valid = 0
        for token in tokens:
            try:
                self.store.status(token)
                valid += 1
            except ApiError:
                pass
        self.assertEqual(1, valid)
        self.assertCode('session_expired', lambda: self.store.status(self.token))
    def test_consent_required_and_quote_remaining_time(self):
        self.assertCode('consent_required', lambda: self.store.register(dict(self.data, account='bob1', consent=False)))
        self.assertEqual(1800, self.store.quote(self.token)['remaining_seconds'])
        self.now += 300
        self.assertEqual(1500, self.store.quote(self.token)['remaining_seconds'])
    def test_http_qr_and_authorization(self):
        server = Server(('127.0.0.1', 0), Handler)
        server.store = self.store
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}/v1/'
            req = Request(url+'quote', b'{}', {'Authorization': 'Bearer '+self.token, 'Content-Type':'application/json'})
            with urlopen(req) as response:
                data = json.load(response)
            self.assertTrue(data['qr_png'].startswith('iVBOR'))
            self.assertIn('am=50.00', data['upi_uri'])
            with self.assertRaises(HTTPError) as caught:
                urlopen(Request(url+'status', b'{}'))
            self.assertEqual(401, caught.exception.code)
        finally:
            server.shutdown(); server.server_close(); thread.join()

if __name__ == '__main__':
    unittest.main()
