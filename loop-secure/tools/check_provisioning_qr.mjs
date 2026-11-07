import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {provisioningPayload} from '../cloud-api/provisioning.ts';
const require=createRequire(new URL('./qr-check/package.json',import.meta.url));
const QRCode=require('qrcode'),{PNG}=require('pngjs');
const {RGBLuminanceSource,HybridBinarizer,BinaryBitmap,QRCodeReader,DecodeHintType}=require('@zxing/library');
const token='test-only-code-not-a-live-customer-0123456789';
const installer={version:'0.14.0',apk_url:'https://ourxezawbfclmhbggskv.supabase.co/storage/client.apk',download_url:'https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager/a/0.14.0-123456789012.apk',apk_sha256:'A'.repeat(43)};
for(const version of ['0.14.0','1.0.0','0.9.2','0.8.0']){
 const payload=provisioningPayload(token,{...installer,version},'https://project.example/functions/v1/loop-manager','test-public-key');
 assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'],'za.co.loopmanager/.LoopAdminReceiver');
 assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'],installer.download_url);
 assert.equal(payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'],installer.apk_sha256);
 assert.equal(payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'].enrollment_token,token);
 assert.equal(payload['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'].server_url,version==='0.8.0'?'https://project.example/functions/v1/loop-manager':undefined);
 const json=JSON.stringify(payload),url=await QRCode.toDataURL(json,{type:'image/png',errorCorrectionLevel:'M',margin:6,scale:10});
 const png=PNG.sync.read(Buffer.from(url.split(',')[1],'base64'));const gray=new Uint8ClampedArray(png.width*png.height);
 for(let i=0;i<gray.length;i++)gray[i]=(png.data[i*4]+2*png.data[i*4+1]+png.data[i*4+2])/4;
 const text=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(gray,png.width,png.height))),new Map([[DecodeHintType.TRY_HARDER,true],[DecodeHintType.PURE_BARCODE,true]])).getText();
 assert.deepEqual(JSON.parse(text),payload);
}
for(const change of [{apk_sha256:'wrong'},{download_url:'http://project.example/app.apk'},{download_url:'https://username:password@project.example/app.apk'}])assert.throws(()=>provisioningPayload(token,{...installer,...change},'https://project.example',''));
assert.throws(()=>provisioningPayload('bad code',installer,'https://project.example',''));
console.log('Full Android provisioning PNGs decoded exactly for four client versions; component, public HTTPS installer, checksum, compact/legacy extras and invalid-input checks passed.');
