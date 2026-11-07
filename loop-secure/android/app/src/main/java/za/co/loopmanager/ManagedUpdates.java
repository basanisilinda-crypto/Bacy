package za.co.loopmanager;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;

/** Device-owner self-updates only: signed device-bound manifest, fixed origin and existing APK signer. */
final class ManagedUpdates {
 private static final AtomicInteger VISIBLE=new AtomicInteger();
 private static final long MAX=30L*1024*1024;
 static void visible(boolean started){if(started)VISIBLE.incrementAndGet();else VISIBLE.updateAndGet(n->Math.max(0,n-1));}
 static boolean busy(){return VISIBLE.get()>0||CheckoutAccess.active();}
 static void schedule(Context c){if(!PolicyController.owns(c))return;var scheduler=c.getSystemService(JobScheduler.class);scheduler.schedule(new JobInfo.Builder(402,new ComponentName(c,UpdateJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true).setPeriodic(6*60*60*1000L).setPersisted(true).build());}
 static void checkSoon(Context c){if(!PolicyController.owns(c))return;c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(403,new ComponentName(c,UpdateJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true).setMinimumLatency(10000).setBackoffCriteria(30*60*1000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 static void request(Context c){if(!PolicyController.owns(c)){status(c,"Automatic installation requires an enrolled managed phone.");return;}var extras=new android.os.PersistableBundle();extras.putBoolean("manual",true);c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(404,new ComponentName(c,UpdateJobService.class)).setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).build());status(c,"Checking for a signed app update…");}
 static String describe(Context c){String value=c.getSharedPreferences("loop.updates",0).getString("status","");return "App version "+BuildConfig.VERSION_NAME+"\nAutomatic updates: on Wi-Fi while the phone is idle.\n"+(value.isEmpty()?"No update check recorded yet.":value);}
 static void status(Context c,String text){c.getSharedPreferences("loop.updates",0).edit().putString("status",text).putLong("at",System.currentTimeMillis()).apply();}
 static boolean trustedUrl(String value){try{Uri u=Uri.parse(value);return "https".equals(u.getScheme())&&"ourxezawbfclmhbggskv.supabase.co".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getQuery()==null&&u.getFragment()==null&&u.getPath()!=null&&u.getPath().matches("/storage/v1/object/public/loop-phone-installers/client-[0-9.]+-[a-f0-9]{64}\\.apk");}catch(Exception e){return false;}}
 static void validate(JSONObject manifest,String deviceId,long now,long currentVersion) throws Exception {
  if(!"client_update".equals(manifest.optString("kind"))||!deviceId.equals(manifest.optString("device_id")))throw new SecurityException("Update belongs to another phone");
  long issued=manifest.getLong("server_time"),expires=manifest.getLong("expires");if(now<0||issued>now+120||issued<now-3600||expires<now||expires>issued+3600)throw new SecurityException("Update authorization expired");
  if(!manifest.optBoolean("available"))return;
  if(!"za.co.loopmanager".equals(manifest.optString("package_name"))||manifest.getLong("version_code")<=currentVersion||manifest.getInt("min_sdk")>Build.VERSION.SDK_INT||manifest.getLong("bytes")<1024||manifest.getLong("bytes")>MAX||!trustedUrl(manifest.getString("apk_url"))||!manifest.getString("apk_sha256").matches("[A-Za-z0-9_-]{43}"))throw new SecurityException("Update metadata is not valid for this installation");
 }
 static JSONObject verifyManifest(Store store,JSONObject envelope,long now,long currentVersion)throws Exception {byte[] raw=Base64.decode(envelope.getString("payload"),Base64.NO_WRAP);AccountSignatures.verify(Base64.decode(store.get("public_key"),Base64.NO_WRAP),Base64.decode(envelope.getString("signature"),Base64.NO_WRAP),raw);JSONObject m=new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8));validate(m,store.get("device_id"),now,currentVersion);return m;}
 static boolean run(Context c,boolean manual)throws Exception {
  if(!PolicyController.owns(c))return false;
  if(CheckoutAccess.active()||(!manual&&(busy()||c.getSystemService(PowerManager.class).isInteractive()))){status(c,"Update check waits until the phone is idle; payment access is preserved.");return true;}
  Store store=new Store(c);if(store.get("device_token").isEmpty())return false;Entitlement account=Sync.run(c);if(account==null||account.data.optBoolean("released"))return false;
  var installer=c.getPackageManager().getPackageInstaller();var prefs=c.getSharedPreferences("loop.updates",0);int pending=prefs.getInt("session",-1);
  if(pending!=-1){var session=installer.getSessionInfo(pending);if(session!=null&&session.isSealed()){status(c,"Android is finishing the downloaded update.");return true;}if(session!=null)installer.abandonSession(pending);prefs.edit().remove("session").apply();}
  long current=c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();JSONObject m=verifyManifest(store,new Api(store).post("/api/device/app-update",new JSONObject().put("version_code",current),true),account.serverNow(c),current);
  if(!m.optBoolean("available")){status(c,"Your app is up to date.");return false;}
  File apk=new File(c.getCacheDir(),"loop-client-update.apk");
  try{
   HttpsURLConnection connection=(HttpsURLConnection)new URL(m.getString("apk_url")).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);
   MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0;
   try{if(connection.getResponseCode()!=200)throw new IOException("Update download unavailable");try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(apk)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Update deferred");size+=n;if(size>m.getLong("bytes")||size>MAX)throw new SecurityException("Unexpected update size");out.write(buffer,0,n);hash.update(buffer,0,n);}}}finally{connection.disconnect();}
   String actual=Base64.encodeToString(hash.digest(),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);if(size!=m.getLong("bytes")||!actual.equals(m.getString("apk_sha256")))throw new SecurityException("Update checksum did not match");
   PackageInfo candidate=c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);PackageInfo installed=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
   if(candidate==null||!c.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()!=m.getLong("version_code")||candidate.signingInfo==null||installed.signingInfo==null||!sameSigners(candidate,installed))throw new SecurityException("Update signing identity did not match this app");
   if(Thread.currentThread().isInterrupted()||!PolicyController.owns(c)||CheckoutAccess.active()||(!manual&&(busy()||c.getSystemService(PowerManager.class).isInteractive()))){status(c,"Verified update will retry when the phone is idle.");return true;}
   PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(c.getPackageName());params.setSize(size);if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
   int id=installer.createSession(params);prefs.edit().putInt("session",id).apply();boolean committed=false;
   try(PackageInstaller.Session session=installer.openSession(id)){
    try(FileInputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,size)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);session.fsync(out);}
    Intent callback=new Intent(c,UpdateReceiver.class).setAction("za.co.loopmanager.UPDATE_RESULT");int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
    PendingIntent result=PendingIntent.getBroadcast(c,id,callback,flags);status(c,"Installing verified update "+m.getString("version_name")+". Your customer connection is retained.");session.commit(result.getIntentSender());committed=true;
   }finally{if(!committed){installer.abandonSession(id);prefs.edit().remove("session").apply();}}
   return false;
  }finally{if(apk.exists())apk.delete();}
 }
 private static boolean sameSigners(PackageInfo a,PackageInfo b){var x=a.signingInfo.getApkContentsSigners();var y=b.signingInfo.getApkContentsSigners();if(x.length!=y.length||x.length==0)return false;for(var s:x){boolean found=false;for(var t:y)if(Arrays.equals(s.toByteArray(),t.toByteArray()))found=true;if(!found)return false;}return true;}
}
