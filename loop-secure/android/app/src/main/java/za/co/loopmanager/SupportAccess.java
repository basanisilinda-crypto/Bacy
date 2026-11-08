package za.co.loopmanager;
import android.app.*;import android.app.admin.DevicePolicyManager;import android.content.*;import android.net.Uri;import android.os.*;import android.telecom.TelecomManager;import java.util.*;import org.json.JSONObject;
/** A short, explicit support-contact session; never changes the default phone app. */
final class SupportAccess {
 static final String PHONE="+27637246209",DISPLAY="063 724 6209",WHATSAPP="https://wa.me/27637246209";
 static boolean active(Context c){var p=c.getSharedPreferences("loop.support",0);long boot=Entitlement.boot(c),now=SystemClock.elapsedRealtime();return boot>=0&&p.getLong("boot",-2)==boot&&now>=p.getLong("started",Long.MAX_VALUE)&&p.getLong("until",0)>now&&p.getLong("until",0)-p.getLong("started",0)<=3*60*1000L;}
 static boolean callsAllowed(Context c){return active(c)&&c.getSharedPreferences("loop.support",0).getBoolean("call",false);}
 static List<String> packages(Context c){if(!active(c))return Collections.emptyList();String pkg=c.getSharedPreferences("loop.support",0).getString("package","");String dialer=c.getSystemService(TelecomManager.class).getDefaultDialerPackage();return pkg.equals("com.whatsapp")||pkg.equals("com.whatsapp.w4b")||pkg.equals(dialer)?Collections.singletonList(pkg):Collections.emptyList();}
 static void end(Context c){c.getSharedPreferences("loop.support",0).edit().clear().apply();}
 static void open(Activity a,boolean whatsapp,JSONObject policy){
  boolean locked=PolicyController.owns(a)&&a.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;String pkg;Intent launch;
  if(whatsapp){pkg=a.getPackageManager().getLaunchIntentForPackage("com.whatsapp")!=null?"com.whatsapp":a.getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b")!=null?"com.whatsapp.w4b":null;if(pkg==null)throw new IllegalStateException("WhatsApp is not installed. Use Call support: "+DISPLAY);launch=new Intent(Intent.ACTION_VIEW,Uri.parse(WHATSAPP+"?text=Hello%20Loop%20Secure%20support")).setPackage(pkg);}
  else{pkg=a.getSystemService(TelecomManager.class).getDefaultDialerPackage();if(pkg==null)throw new IllegalStateException("The phone's default calling app is unavailable. Support: "+DISPLAY);launch=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+PHONE)).setPackage(pkg);}
  if(launch.resolveActivity(a.getPackageManager())==null)throw new IllegalStateException("The support contact app cannot open. Call "+DISPLAY);
  if(locked){a.getSharedPreferences("loop.support",0).edit().putLong("boot",Entitlement.boot(a)).putLong("started",SystemClock.elapsedRealtime()).putLong("until",SystemClock.elapsedRealtime()+3*60*1000).putString("package",pkg).putBoolean("call",!whatsapp).apply();BankApps.allowForLock(a,policy);if(!whatsapp)a.getSystemService(DevicePolicyManager.class).clearUserRestriction(PolicyController.admin(a),UserManager.DISALLOW_OUTGOING_CALLS);}
  try{if(locked){ActivityOptions options=ActivityOptions.makeBasic();options.setLockTaskEnabled(true);a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),options.toBundle());}else a.startActivity(launch);}catch(RuntimeException e){end(a);throw e;}
 }
}
