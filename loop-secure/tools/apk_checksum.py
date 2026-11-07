"""Print Android QR provisioning's URL-safe SHA-256 APK checksum."""
import base64
import hashlib
import pathlib
import sys
apk=pathlib.Path(sys.argv[1])
print(base64.urlsafe_b64encode(hashlib.sha256(apk.read_bytes()).digest()).decode().rstrip('='))
