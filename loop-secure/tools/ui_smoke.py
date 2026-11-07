"""Launch an isolated real API for browser integration checks; no production data."""
import base64
import os
import secrets
import subprocess
import tempfile
import time
from pathlib import Path
import httpx

root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as data:
    os.environ['LOOP_DATA_DIR']=data
    os.environ['LOOP_DEV']='1'
    os.environ['LOOP_PUBLIC_URL']='http://127.0.0.1:8765'
    from backend import app as m
    password=secrets.token_urlsafe(30)
    secret=base64.b32encode(secrets.token_bytes(20)).decode().rstrip('=')
    with m.db() as c:c.execute('INSERT INTO admins(id,email,password,totp,role) VALUES(?,?,?,?,?)',(m.uid(),'browser-test@example.com',m.password_hash(password),secret,'owner'))
    env=os.environ.copy()
    env.update(LOOP_TEST_EMAIL='browser-test@example.com',LOOP_TEST_PASSWORD=password,LOOP_TEST_CODE=m.totp(secret,m.now()//30),LOOP_ARTIFACTS=str(root/'artifacts'))
    server=subprocess.Popen([str(root/'.venv/bin/python'),'-m','uvicorn','backend.app:app','--host','127.0.0.1','--port','8765'],cwd=root,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):
            try:
                if httpx.get('http://127.0.0.1:8765/health').status_code==200:break
            except httpx.ConnectError:pass
            time.sleep(.1)
        subprocess.run(['node',str(root/'tools/ui_smoke.js')],cwd=root,env=env,check=True)
    finally:
        server.terminate();server.wait(timeout=10)
