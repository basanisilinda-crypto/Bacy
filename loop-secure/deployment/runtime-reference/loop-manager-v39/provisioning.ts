/** Android Setup Wizard payload. No login-protected dashboard URLs or invented accounts. */
export function provisioningPayload(token:string,installer:{version:string;apk_url:string;download_url?:string;apk_sha256:string},serverUrl:string,publicKey:string){
 if(!/^[A-Za-z0-9_-]{20,100}$/.test(token))throw new Error('Invalid enrollment code');
 const checksum=installer.apk_sha256.replace(/=+$/,'');if(!/^[A-Za-z0-9_-]{43}$/.test(checksum))throw new Error('Invalid APK SHA-256 checksum');
 const download=installer.download_url||installer.apk_url,url=new URL(download);if(url.protocol!=='https:'||url.username||url.password||url.hash)throw new Error('An HTTPS Android installer URL is required');
 const version=installer.version.split('.').map(Number);const compact=version[0]>0||version[1]>9||(version[1]===9&&version[2]>=2);
 const extras=compact?{enrollment_token:token}:{server_url:serverUrl,enrollment_token:token,public_key:publicKey};
 return {'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME':'za.co.loopmanager/.LoopAdminReceiver','android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION':download,'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM':checksum,'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED':true,'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE':extras};
}
