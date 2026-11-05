"""Open a configured local dashboard only after a verified health response."""
from __future__ import annotations
import argparse
import json
import sys
import time
import urllib.request
import webbrowser
from runtime_config import load_config


def dashboard_url(config):
    host = config.get('host', '127.0.0.1')
    if host == '::1':
        host = '[::1]'
    return f"{'https' if config.get('tls_cert') else 'http'}://{host}:{config.get('port',8765)}"


def process_running(pid):
    if not pid or sys.platform != 'win32':
        return True
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x1000, False, pid)
    if not handle:
        return False
    try:
        code = wintypes.DWORD()
        return bool(kernel.GetExitCodeProcess(handle, ctypes.byref(code))) and code.value == 259
    finally:
        kernel.CloseHandle(handle)


def wait_until_ready(url, *, timeout=45, process_id=0):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not process_running(process_id):
            raise RuntimeError('The dashboard process exited. Check its error window; another instance may already be running.')
        try:
            with opener.open(url + '/api/health', timeout=2) as response:
                data = json.loads(response.read(4096))
                if response.status == 200 and data.get('status') == 'ok' and isinstance(data.get('version'), str):
                    return
        except (OSError, ValueError):
            pass
        time.sleep(.5)
    raise RuntimeError('Dashboard startup timed out. Inspect the service window. No trading command was sent.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--process-id', type=int, default=0)
    args = parser.parse_args()
    url = dashboard_url(load_config())
    wait_until_ready(url, process_id=args.process_id)
    if not webbrowser.open(url):
        print('Open this dashboard address in your browser: ' + url)
    print('Dashboard ready. Local mode opens automatically. Trading remains paused.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
