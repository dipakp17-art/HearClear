"""HearClear account/entitlement service. Run behind an HTTPS reverse proxy.
No payment is trusted until the operator verifies settlement and approves it locally.
"""
import argparse
import base64
import hashlib
import hmac
import io
import json
import os
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode

TRIAL_SECONDS = 48 * 3600
LEASE_SECONDS = 75
SESSION_SECONDS = 30 * 86400
INDIA = timezone(timedelta(hours=5, minutes=30))
APPROVAL_HOURS = "10:00 AM–5:00 PM India time, every day"

def approvals_open(timestamp):
    hour = datetime.fromtimestamp(timestamp, INDIA).hour
    return 10 <= hour < 17

SCHEMA = """
CREATE TABLE IF NOT EXISTS accounts (
 id TEXT PRIMARY KEY, salt TEXT NOT NULL, password TEXT NOT NULL,
 recovery TEXT NOT NULL, trial_end INTEGER NOT NULL, device TEXT NOT NULL,
 paid INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, trial_end INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (
 token TEXT PRIMARY KEY, account TEXT NOT NULL, device TEXT NOT NULL, expires INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS orders (
 id TEXT PRIMARY KEY, account TEXT NOT NULL, slot INTEGER NOT NULL,
 amount INTEGER NOT NULL, expires INTEGER NOT NULL, state TEXT NOT NULL,
 reference TEXT UNIQUE, created INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS audit (at INTEGER NOT NULL, action TEXT NOT NULL, account TEXT NOT NULL, detail TEXT NOT NULL);
"""

class ApiError(Exception):
    def __init__(self, code, message, status=400):
        self.code, self.message, self.status = code, message, status
        super().__init__(message)

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

def password_hash(password, salt):
    return hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 600_000).hex()

def price(slot):
    if slot < 1:
        raise ValueError('slot must be positive')
    return min(250, 50 * ((slot - 1) // 100 + 1))

class Store:
    def __init__(self, path, upi='', payee='', clock=time.time):
        self.path, self.upi, self.payee, self.clock = str(path), upi, payee, clock
        with self.db() as c:
            c.executescript(SCHEMA)

    @contextmanager
    def db(self):
        c = sqlite3.connect(self.path, timeout=15, isolation_level=None)
        c.row_factory = sqlite3.Row
        try:
            c.execute('BEGIN IMMEDIATE')
            yield c
            c.commit()
        except Exception:
            c.rollback()
            raise
        finally:
            c.close()

    def now(self):
        return int(self.clock())

    def audit(self, c, action, account, detail=''):
        c.execute('INSERT INTO audit VALUES (?,?,?,?)', (self.now(), action, account, detail))

    def identity(self, data):
        name = str(data.get('account', '')).strip().lower()
        pw, device = str(data.get('password', '')), str(data.get('device', ''))
        if not re.fullmatch(r'[a-z0-9][a-z0-9_.-]{3,39}', name):
            raise ApiError('invalid_account', 'Use an account ID of 4–40 letters, numbers, dots, underscores or hyphens.')
        if not 12 <= len(pw) <= 128 or not re.fullmatch('[0-9a-f]{64}', device):
            raise ApiError('invalid_credentials', 'Use a password of 12–128 characters and a valid device.')
        return name, pw, device

    def make_session(self, c, name, device):
        token = secrets.token_urlsafe(32)
        c.execute('DELETE FROM sessions WHERE account=?', (name,))
        c.execute('INSERT INTO sessions VALUES (?,?,?,?)', (digest(token), name, device, self.now() + SESSION_SECONDS))
        return token

    def register(self, data):
        name, pw, device = self.identity(data)
        if data.get('consent') is not True:
            raise ApiError('consent_required', 'Please read and accept the account data notice.')
        salt, recovery = secrets.token_hex(16), secrets.token_urlsafe(24)
        hashed = password_hash(pw, salt)
        with self.db() as c:
            if c.execute('SELECT 1 FROM accounts WHERE id=?', (name,)).fetchone():
                raise ApiError('unavailable', 'Account ID unavailable. Choose another, or sign in.', 409)
            old = c.execute('SELECT trial_end FROM devices WHERE id=?', (device,)).fetchone()
            end = old['trial_end'] if old else self.now() + TRIAL_SECONDS
            c.execute('INSERT OR IGNORE INTO devices VALUES (?,?)', (device, end))
            c.execute('INSERT INTO accounts VALUES (?,?,?,?,?,?,0,?)',
                      (name, salt, hashed, digest(recovery), end, device, self.now()))
            token = self.make_session(c, name, device)
            self.audit(c, 'register', name)
        return dict(token=token, recovery_code=recovery)

    def login(self, data, transfer=False, recover=False):
        name, pw, device = self.identity(data)
        # Hash outside the writer transaction. Unknown users do comparable work.
        with self.db() as c:
            row = c.execute('SELECT * FROM accounts WHERE id=?', (name,)).fetchone()
        valid = hmac.compare_digest(password_hash(pw, row['salt'] if row else '00' * 16), row['password'] if row else '00' * 32)
        code = str(data.get('recovery_code', ''))
        recovery_ok = row is not None and hmac.compare_digest(digest(code), row['recovery'])
        if not row or (not recover and not valid) or ((transfer or recover) and not recovery_ok):
            raise ApiError('unauthorized', 'Account details or recovery code are incorrect.', 401)
        with self.db() as c:
            # Re-read after acquiring lock to serialize concurrent transfers/logins.
            row = c.execute('SELECT * FROM accounts WHERE id=?', (name,)).fetchone()
            if (not recover and row['password'] != password_hash(pw, row['salt'])) or ((transfer or recover) and not hmac.compare_digest(digest(code), row['recovery'])):
                raise ApiError('unauthorized', 'Account details changed. Sign in again.', 401)
            if row['device'] != device and not (transfer or recover):
                raise ApiError('transfer_required', 'This account is on another phone. Use Transfer with your recovery code.', 409)
            if recover:
                salt = secrets.token_hex(16)
                c.execute('UPDATE accounts SET salt=?,password=? WHERE id=?', (salt, password_hash(pw, salt), name))
            if row['device'] != device:
                old_trial = c.execute('SELECT trial_end FROM devices WHERE id=?', (device,)).fetchone()
                end = min(row['trial_end'], old_trial['trial_end']) if old_trial else row['trial_end']
                c.execute('INSERT OR IGNORE INTO devices VALUES (?,?)', (device, end))
                c.execute('UPDATE devices SET trial_end=MIN(trial_end,?) WHERE id=?', (end, device))
                c.execute('UPDATE accounts SET device=?,trial_end=? WHERE id=?', (device, end, name))
                self.audit(c, 'transfer', name)
            token = self.make_session(c, name, device)
            self.audit(c, 'recover' if recover else 'login', name)
        return dict(token=token)

    def auth(self, c, token):
        row = c.execute('SELECT a.* FROM accounts a JOIN sessions s ON a.id=s.account '
                        'WHERE s.token=? AND s.expires>? AND a.device=s.device', (digest(token), self.now())).fetchone()
        if row is None:
            raise ApiError('session_expired', 'Sign in again. This phone may have been replaced.', 401)
        return row

    def status(self, token):
        with self.db() as c:
            row = self.auth(c, token)
            remaining = max(0, row['trial_end'] - self.now())
            return dict(account=row['id'], paid=bool(row['paid']), trial_remaining_seconds=remaining,
                        lease_seconds=LEASE_SECONDS if row['paid'] else min(LEASE_SECONDS, remaining),
                        updates_free=True, approval_hours=APPROVAL_HOURS)

    def quote(self, token):
        if not self.upi or not self.payee:
            raise ApiError('payments_unconfigured', 'Payments are not available yet. Contact the app owner.', 503)
        with self.db() as c:
            user = self.auth(c, token)
            if user['paid']:
                raise ApiError('already_paid', 'This account already has lifetime access.', 409)
            c.execute("UPDATE orders SET state='expired' WHERE state='quoted' AND expires<=?", (self.now(),))
            order = c.execute("SELECT * FROM orders WHERE account=? AND state IN ('quoted','pending')", (user['id'],)).fetchone()
            if not order:
                used = {r[0] for r in c.execute("SELECT slot FROM orders WHERE state IN ('quoted','pending','approved')")}
                slot = 1
                while slot in used:
                    slot += 1
                oid = secrets.token_hex(12)
                c.execute('INSERT INTO orders VALUES (?,?,?,?,?,?,NULL,?)',
                          (oid, user['id'], slot, price(slot), self.now() + 1800, 'quoted', self.now()))
                order = c.execute('SELECT * FROM orders WHERE id=?', (oid,)).fetchone()
            result = dict(order)
            result['upi_uri'] = 'upi://pay?' + urlencode(dict(pa=self.upi, pn=self.payee, am=f"{order['amount']}.00", cu='INR', tn='HearClear ' + order['id']))
            result['remaining_seconds'] = max(0, order['expires'] - self.now())
            result['approval_hours'] = APPROVAL_HOURS
            result['approvals_open'] = approvals_open(self.now())
            result['payee'] = self.payee
            result['upi_id'] = self.upi
            return result

    def receipt(self, token, data):
        reference = str(data.get('reference', '')).strip().upper()
        if not re.fullmatch('[A-Z0-9]{8,40}', reference):
            raise ApiError('invalid_reference', 'Enter the transaction reference from your payment app (8–40 letters/numbers).')
        with self.db() as c:
            user = self.auth(c, token)
            order = c.execute('SELECT * FROM orders WHERE id=? AND account=?', (str(data.get('order', '')), user['id'])).fetchone()
            if not order:
                raise ApiError('order_missing', 'Open payment details first.', 404)
            if order['state'] == 'pending' and order['reference'] == reference:
                return dict(state='pending')
            if order['state'] != 'quoted' or order['expires'] <= self.now():
                raise ApiError('quote_expired', 'Payment window ended or reference already submitted. Contact the owner if you paid.', 409)
            try:
                c.execute("UPDATE orders SET state='pending',reference=? WHERE id=?", (reference, order['id']))
            except sqlite3.IntegrityError:
                raise ApiError('reference_used', 'That transaction reference is already submitted.', 409)
            self.audit(c, 'payment_submitted', user['id'], order['id'])
        return dict(state='pending', approval_hours=APPROVAL_HOURS)

    def approve(self, oid, amount, reference):
        with self.db() as c:
            order = c.execute('SELECT * FROM orders WHERE id=?', (oid,)).fetchone()
            if not order or order['state'] not in ('pending', 'approved'):
                raise ValueError('Order must have a submitted reference. Inspect it first.')
            if order['amount'] != amount or order['reference'] != reference.strip().upper():
                raise ValueError('Settled amount/reference does not match the order.')
            if order['state'] == 'approved':
                return  # idempotent; retrying an already-approved purchase changes nothing
            if not approvals_open(self.now()):
                raise ValueError('Payment approvals are open only ' + APPROVAL_HOURS + '. Submitted payments remain pending until the next window.')
            if c.execute('SELECT paid FROM accounts WHERE id=?', (order['account'],)).fetchone()[0]:
                raise ValueError('Account is already paid; resolve duplicate payment manually.')
            c.execute("UPDATE orders SET state='approved' WHERE id=?", (oid,))
            c.execute('UPDATE accounts SET paid=1 WHERE id=?', (order['account'],))
            self.audit(c, 'payment_approved', order['account'], oid)

    def reject(self, oid):
        with self.db() as c:
            order = c.execute("SELECT * FROM orders WHERE id=? AND state='pending'", (oid,)).fetchone()
            if not order:
                raise ValueError('Pending order not found')
            c.execute("UPDATE orders SET state='rejected' WHERE id=?", (oid,))
            self.audit(c, 'payment_rejected', order['account'], oid)

    def logout(self, token):
        with self.db() as c:
            c.execute('DELETE FROM sessions WHERE token=?', (digest(token),))
        return dict(ok=True)

    def report(self):
        with self.db() as c:
            return dict(approval_hours=APPROVAL_HOURS, approvals_open=approvals_open(self.now()), accounts=c.execute('SELECT COUNT(*) FROM accounts').fetchone()[0],
                        paid=c.execute('SELECT COUNT(*) FROM accounts WHERE paid=1').fetchone()[0],
                        pending=[dict(r) for r in c.execute("SELECT id,account,amount,reference FROM orders WHERE state='pending' ORDER BY created")])

class Limiter:
    def __init__(self):
        self.lock, self.hits = threading.Lock(), {}
    def check(self, key, limit):
        now = time.monotonic()
        with self.lock:
            # bounded map; counts reset every minute, independent of wall-clock changes
            bucket = int(now // 60)
            self.hits = {k: v for k, v in self.hits.items() if v[0] == bucket}
            old = self.hits.get(key, (bucket, 0))
            if old[1] >= limit:
                raise ApiError('rate_limited', 'Too many attempts. Wait one minute.', 429)
            self.hits[key] = (bucket, old[1] + 1)

class Handler(BaseHTTPRequestHandler):
    limiter = Limiter()
    def log_message(self, fmt, *args):
        pass  # never log credentials, tokens or references
    def send_json(self, status, data):
        raw = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)
    def do_POST(self):
        self.connection.settimeout(10)
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 8192:
                raise ApiError('bad_request', 'Invalid request size.')
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ApiError('bad_request', 'Incomplete request.')
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise ApiError('bad_request', 'Expected a JSON object.')
            # Listener is loopback-only. The trusted reverse proxy must OVERWRITE X-Real-IP.
            client_ip = self.headers.get('X-Real-IP', self.client_address[0])
            self.limiter.check(('ip', client_ip), 600)
            auth_paths = ('/v1/register', '/v1/login', '/v1/transfer', '/v1/recover')
            if self.path in auth_paths:
                self.limiter.check(('auth', str(data.get('account', '')).lower()), 5)
                self.limiter.check(('auth-ip', client_ip), 20)
            token = self.headers.get('Authorization', '').removeprefix('Bearer ')
            store = self.server.store
            if self.path == '/v1/register':
                result = store.register(data)
            elif self.path in ('/v1/login', '/v1/transfer', '/v1/recover'):
                result = store.login(data, transfer=self.path == '/v1/transfer', recover=self.path == '/v1/recover')
            elif self.path == '/v1/status':
                result = store.status(token)
            elif self.path == '/v1/quote':
                result = store.quote(token)
                # Only payment details go into the QR. No credentials or account token.
                import qrcode
                stream = io.BytesIO()
                qrcode.make(result['upi_uri']).save(stream, format='PNG')
                result['qr_png'] = base64.b64encode(stream.getvalue()).decode()
            elif self.path == '/v1/receipt':
                result = store.receipt(token, data)
            elif self.path == '/v1/logout':
                result = store.logout(token)
            else:
                raise ApiError('not_found', 'Endpoint not found.', 404)
            self.send_json(200, result)
        except ApiError as e:
            self.send_json(e.status, dict(error=e.code, message=e.message))
        except (ValueError, TypeError):
            self.send_json(400, dict(error='bad_request', message='Invalid request.'))
        except Exception:
            self.send_json(503, dict(error='unavailable', message='Service unavailable. Please try again.'))

class Server(ThreadingHTTPServer):
    daemon_threads = True

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['serve', 'report', 'approve', 'reject'])
    parser.add_argument('--db', default=os.environ.get('HEARCLEAR_DB', 'hearclear.sqlite3'))
    parser.add_argument('--port', type=int, default=8787)
    parser.add_argument('--order')
    parser.add_argument('--amount', type=int)
    parser.add_argument('--reference')
    parser.add_argument('--settlement-checked', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    store = Store(args.db, os.environ.get('HEARCLEAR_UPI_ID', ''), os.environ.get('HEARCLEAR_PAYEE', ''))
    if args.command == 'serve':
        server = Server(('127.0.0.1', args.port), Handler)
        server.store = store
        print('HearClear API on localhost; use an HTTPS reverse proxy. No public admin endpoint.', flush=True)
        server.serve_forever()
    elif args.command == 'report':
        print(json.dumps(store.report(), indent=2))
    elif args.command == 'approve':
        if not (args.settlement_checked and args.order and args.amount and args.reference):
            parser.error('Check bank settlement first; then supply --order --amount --reference --settlement-checked')
        try:
            store.approve(args.order, args.amount, args.reference)
        except ValueError as e:
            parser.exit(2, str(e) + '\n')
        print('Lifetime access approved.')
    else:
        if not args.order:
            parser.error('--order required')
        store.reject(args.order)
        print('Order rejected; pricing slot released. No money is refunded by this command.')

if __name__ == '__main__':
    main()
