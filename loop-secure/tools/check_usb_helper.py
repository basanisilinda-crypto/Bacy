"""Exercise the helper's actual PowerShell parsers without a phone or customer records.

Run with a PowerShell executable: python tools/check_usb_helper.py /path/to/pwsh
This checks parser behavior, not Windows CMD, USB drivers or physical provisioning.
"""
import concurrent.futures
import os
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'deployment/usb/Enroll-Windows.cmd').read_text()
commands = [line.split('-Command "', 1)[1][:-1] for line in source.splitlines()
            if line.startswith('powershell.exe ') and '-Command "' in line]
owner_parser = next(s for s in commands if 'LoopSecureOwnerCheck:v1:' in s)
device_parser = next(s for s in commands if 'BACY_SERIAL_RESULT' in s)
pwsh = sys.argv[1] if len(sys.argv) > 1 else 'pwsh'
nonce = '123456789'


def probe_line(owner='true', active='true', linked='false', reply_nonce=nonce):
    return (f'Broadcast completed: result=1, data="LoopSecureOwnerCheck:v1:{reply_nonce}:'
            f'owner={owner};active={active};linked={linked}"')


cases = [
    ('confirmed owner', probe_line(), 0),
    ('dpm refused with zero remote exit', probe_line('false', 'false'), 10),
    ('active administrator is not device owner', probe_line('false', 'true'), 10),
    ('inactive administrator', probe_line('true', 'false'), 10),
    ('preserve linked owner', probe_line(linked='true'), 20),
    ('preserve linked account without owner', probe_line('false', 'false', 'true'), 21),
    ('unreadable account', probe_line(linked='unknown'), 30),
    ('unavailable management API', probe_line(owner='unknown'), 30),
    ('stale nonce', probe_line(reply_nonce='99999'), 30),
    ('missing response', 'Broadcast completed: result=0', 30),
    ('duplicate response', probe_line() + '\n' + probe_line(), 30),
    ('marker in exception text', 'Error: ' + probe_line(), 30),
    ('suffix garbage', probe_line() + '; ignored=true', 30),
    ('ambiguous response', 'Broadcast completed: result=0\n' + probe_line(), 30),
]


def owner_case(case):
    label, output, expected = case
    with tempfile.TemporaryDirectory() as folder:
        result = Path(folder) / 'result.txt'
        result.write_text('Broadcasting: Intent { component probe }\n' + output + '\n')
        env = dict(os.environ, XDG_CACHE_HOME=folder, XDG_CONFIG_HOME=folder, XDG_DATA_HOME=folder, BACY_OWNER_NONCE=nonce, BACY_OWNER_RESULT=str(result))
        run = subprocess.run([pwsh, '-NoProfile', '-Command', owner_parser], env=env,
                             capture_output=True, text=True, timeout=30)
        assert run.returncode == expected, (label, run.returncode, run.stderr)
    return label


device_cases = [
    ('one authorized device', 'SAFE123\tdevice', 0, 'SAFE123'),
    ('not authorized', 'SAFE123\tunauthorized', 2, None),
    ('offline', 'SAFE123\toffline', 1, None),
    ('multiple phones', 'SAFE123\tdevice\nOTHER456\tdevice', 1, None),
    ('no phone', '', 1, None),
    ('unsafe serial', 'bad&command\tdevice', 1, None),
]


def device_case(case):
    label, devices, expected, expected_serial = case
    with tempfile.TemporaryDirectory() as folder:
        listing, serial = Path(folder) / 'devices.txt', Path(folder) / 'serial.txt'
        listing.write_text('List of devices attached\n' + devices + '\n\n')
        env = dict(os.environ, XDG_CACHE_HOME=folder, XDG_CONFIG_HOME=folder, XDG_DATA_HOME=folder, BACY_DEVICE_LIST=str(listing), BACY_SERIAL_RESULT=str(serial))
        run = subprocess.run([pwsh, '-NoProfile', '-Command', device_parser], env=env,
                             capture_output=True, text=True, timeout=30)
        assert run.returncode == expected, (label, run.returncode, run.stderr)
        if expected_serial is not None:
            assert serial.read_text() == expected_serial
        else:
            assert not serial.exists()
    return label


with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    outcomes = list(pool.map(owner_case, cases)) + list(pool.map(device_case, device_cases))
print(f'{len(outcomes)} actual PowerShell ownership/device parser cases passed.')
