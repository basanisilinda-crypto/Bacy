package za.co.loopmanager;
import android.content.Context;import android.os.SystemClock;
/** Bounded maintenance restriction; it never changes the financial account or blocks recovery access. */
final class UpdateGate {
 static void begin(Context c,long version){if(!PolicyController.owns(c))return;var p=c.getSharedPreferences("loop.updates",0);if(p.getLong("required_version",0)==version&&p.getLong("gate_boot",-2)==Entitlement.boot(c))return;c.getSharedPreferences("loop.updates",0).edit().putLong("required_version",version).putLong("gate_boot",Entitlement.boot(c)).putLong("gate_until",SystemClock.elapsedRealtime()+10*60*1000).apply();}
 static void clear(Context c){c.getSharedPreferences("loop.updates",0).edit().remove("gate_until").remove("required_version").apply();}
 static boolean active(Context c){try{var p=c.getSharedPreferences("loop.updates",0);return PolicyController.owns(c)&&!CheckoutAccess.active()&&p.getLong("required_version",0)>c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()&&p.getLong("gate_boot",-2)==Entitlement.boot(c)&&p.getLong("gate_until",0)>SystemClock.elapsedRealtime();}catch(Exception e){return false;}}
}
