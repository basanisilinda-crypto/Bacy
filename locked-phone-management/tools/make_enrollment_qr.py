#!/usr/bin/env python3
"""Create an immutable Android setup-time enrollment QR for a *specific* built APK.

The URL must point to a publicly accessible, unchanged HTTPS APK. This script
hashes the actual APK bytes and never uses a made-up checksum.
This is a pilot/test-device workflow only; debug signing is not suitable for
production app updates.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", type=Path, required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    if not args.apk.is_file():
        ap.error("APK does not exist")
    parsed = urlparse(args.url)
    if parsed.scheme != "https" or not parsed.netloc:
        ap.error("APK URL must be HTTPS")
    args.output.mkdir(parents=True, exist_ok=True)

    digest = hashlib.sha256(args.apk.read_bytes()).digest()
    encoded = base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")
    payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
            "za.co.bacyflex.customer/.FinanceAdminReceiver",
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.url,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": encoded,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
    }
    qr_text = json.dumps(payload, separators=(",", ":"), ensure_ascii=True)
    (args.output / "enrollment-qr.json").write_text(
        json.dumps(payload, indent=2) + "\n", encoding="utf-8"
    )
    (args.output / "locked-phone-customer.sha256").write_text(
        digest.hex() + "  locked-phone-customer.apk\n", encoding="utf-8"
    )
    import qrcode
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,
                       box_size=9, border=4)
    qr.add_data(qr_text)
    qr.make(fit=True)
    qr.make_image(fill_color="#17202A", back_color="#FFFFFF").save(
        args.output / "enrollment-qr.png"
    )
    print("Generated QR for exact APK URL:", args.url)
    print("SHA-256:", digest.hex())
    print("Payload checksum:", encoded)


if __name__ == "__main__":
    main()
