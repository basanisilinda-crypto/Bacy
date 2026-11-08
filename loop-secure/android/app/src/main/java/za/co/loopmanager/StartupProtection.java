package za.co.loopmanager;
import android.content.Context;import android.app.admin.DevicePolicyManager;import android.os.SystemClock;import android.os.UserManager;
/** Device-protected boot marker contains no customer data, code or credential. */
final class StartupProtection {
 static android.content.SharedPreferences prefs(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("loop.startup",0);}
 static void record(Context c,boolean active){if(prefs(c).getBoolean("financed",false)!=active)prefs(c).edit().putBoolean("financed",active).commit();}
 static void lockedBoot(Context c){if(!prefs(c).getBoolean("financed",false)||!PolicyController.owns(c))return;DevicePolicyManager pm=c.getSystemService(DevicePolicyManager.class);pm.addUserRestriction(PolicyController.admin(c),UserManager.DISALLOW_DEBUGGING_FEATURES);prefs(c).edit().putLong("early_elapsed",SystemClock.elapsedRealtime()).putString("phase","Android credential unlock pending").apply();}
 static void recovering(Context c){if(PolicyController.owns(c))prefs(c).edit().putLong("recovery_elapsed",SystemClock.elapsedRealtime()).putString("phase","Local policy recovery requested").apply();}
}
