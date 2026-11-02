# Android connection and security

The Windows loopback dashboard works on that Windows computer only. Opening localhost on Android addresses the phone itself. The phone does not run the trading engine.

Default operation is local-only. Never forward a router port, use an unauthenticated tunnel, or disable certificate validation to access the controls.

For a trusted private network, obtain a free local certificate using a tool such as mkcert from its official source. The certificate must cover the actual Windows private IP or hostname. Install only the public local root certificate on your own Android device using Android's certificate settings, verify its fingerprint using a trusted local channel, and keep its private signing key on the Windows machine. Device/managed-browser policy can prevent installation. A self-signed leaf accepted through a warning is not a reliably supported PWA secure context.

After local setup, stop the backend and run the private-network configuration helper using the project virtual-environment Python:

```
python scripts/configure_network.py --address YOUR_PRIVATE_WINDOWS_IP --cert PATH_TO_CERTIFICATE --key PATH_TO_PRIVATE_KEY --confirm-private-network
```

Replace placeholders with your actual private address and certificate files. The helper accepts specific private/VPN IPv4 addresses, verifies the certificate/key load, sets secure cookies and exact allowed origins, and never changes router/firewall rules. Restart the backend and open its HTTPS address from your phone on the same trusted network. If Windows Firewall prompts, allow only the intended private network and user-approved source devices. Verify the certificate without bypassing warnings, log in, and check the connection indicator before using controls. Install through the supported browser's install menu when offered.

Optional private VPN software such as Tailscale may provide access away from home under an eligible free personal plan. It is disabled by default, is not included or provisioned here, and its current eligibility, account requirements, device policies and terms must be checked separately. It does not supply a trading runtime. Use device access restrictions, the same HTTPS/authentication requirements, and no public sharing. If secure connectivity cannot be established, keep local-only mode.

Restore loopback operation using `python scripts/configure_network.py --local-only` while stopped, then restart. See the root SECURITY.md for the threat boundary. Do not share bridge files, password hashes, private keys or database backups. Browser voice input is opt-in and may use a browser provider's network processing; typed chat is always available without that provider.
