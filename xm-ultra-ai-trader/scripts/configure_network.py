"""Explicit opt-in to authenticated HTTPS on a private interface; no firewall changes."""
from __future__ import annotations
import argparse
import ipaddress
import json
from pathlib import Path
import sys
from runtime_config import DEFAULT_CONFIG, load_config
from setup_local import private_write

PRIVATE_NETWORKS = tuple(ipaddress.ip_network(network) for network in (
    "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--local-only", action="store_true")
    parser.add_argument("--address", help="Windows private IPv4 address or private VPN IPv4 address")
    parser.add_argument("--cert", type=Path)
    parser.add_argument("--key", type=Path)
    parser.add_argument("--confirm-private-network", action="store_true")
    args = parser.parse_args()
    config = load_config(args.config)
    if args.local_only:
        config.update(host="127.0.0.1", allowed_hosts=["localhost", "127.0.0.1", "::1"], allowed_origins=[],
                      network_access_confirmed=False, tls_cert=None, tls_key=None)
    else:
        if config.get("local_passwordless", True):
            raise ValueError("Enable password mode locally before configuring phone/network access.")
        if not args.confirm_private_network or not args.address or not args.cert or not args.key:
            raise ValueError("Network mode requires an explicit private address, certificate, key, and confirmation.")
        address = ipaddress.ip_address(args.address)
        if address.version != 4 or not any(address in network for network in PRIVATE_NETWORKS):
            raise ValueError("Only a specific private LAN/VPN IPv4 address is supported by this setup helper.")
        if not args.cert.is_file() or not args.key.is_file():
            raise ValueError("TLS certificate or private key is missing.")
        # The TLS library verifies that the certificate/key pair is usable. It
        # cannot establish Android trust; that must be verified on the device.
        import ssl
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(str(args.cert), str(args.key))
        port = config.get("port", 8765)
        config.update(host=str(address), allowed_hosts=[str(address), "localhost", "127.0.0.1", "::1"],
                      allowed_origins=[f"https://{address}:{port}", f"https://localhost:{port}"],
                      network_access_confirmed=True, tls_cert=str(args.cert.resolve()), tls_key=str(args.key.resolve()))
    private_write(args.config, json.dumps(config, indent=2) + "\n")
    print("Configuration saved. Restart the dashboard to apply it; trading remains paused after restart.")
    print("This script did not expose public ports, add firewall rules, or establish remote internet connectivity.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        print(f"Network configuration stopped: {exc}", file=sys.stderr)
        raise SystemExit(1)
