package za.co.loopmanager;
import android.app.ActivityManager;import android.app.admin.DevicePolicyManager;import android.content.*;import android.content.pm.*;import android.provider.Settings;import org.json.JSONObject;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class ClientAccessTest {
 @Test public void paidPhoneCanOpenWithoutStartingOrRemovingItsOwnTask()throws Exception{
  Context c=RuntimeEnvironment.getApplication();Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,1);
  long t=2000000000L;JSONObject d=new JSONObject().put("device_id","fixture").put("server_time",t).put("due",t+86400).put("released",false).put("policy",new JSONObject().put("enforcement_enabled",true).put("manual_lock",false));Entitlement e=new Entitlement(d,android.os.SystemClock.elapsedRealtime(),1);
  for(int i=0;i<3;i++){assertEquals("unrestricted",PolicyController.apply(c,e));assertEquals(ActivityManager.LOCK_TASK_MODE_NONE,c.getSystemService(ActivityManager.class).getLockTaskModeState());assertArrayEquals(new String[]{c.getPackageName()},c.getSystemService(DevicePolicyManager.class).getLockTaskPackages(PolicyController.admin(c)));}
  assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP,c.getPackageManager().getActivityInfo(new ComponentName(c,MainActivity.class),0).launchMode);
 }
 @Test public void smsAccessIsLimitedToAnActiveCheckoutAndDefaultSmsHandler()throws Exception{
  Context c=RuntimeEnvironment.getApplication();String name="com.google.android.apps.messaging";ApplicationInfo app=new ApplicationInfo();app.packageName=name;app.enabled=true;PackageInfo info=new PackageInfo();info.packageName=name;info.applicationInfo=app;Shadows.shadowOf(c.getPackageManager()).installPackage(info);ComponentName component=new ComponentName(name,name+".MainActivity");ActivityInfo activity=Shadows.shadowOf(c.getPackageManager()).addActivityIfNotPresent(component);activity.applicationInfo=app;activity.enabled=true;activity.exported=true;IntentFilter filter=new IntentFilter(Intent.ACTION_MAIN);filter.addCategory(Intent.CATEGORY_LAUNCHER);Shadows.shadowOf(c.getPackageManager()).addIntentFilterForActivity(component,filter);org.robolectric.shadows.ShadowTelephony.ShadowSms.setDefaultSmsPackage(name);
  assertTrue(CheckoutAccess.packages(c).isEmpty());CheckoutAccess.begin();try{assertEquals(java.util.Collections.singletonList(name),CheckoutAccess.packages(c));assertFalse(CheckoutAccess.packages(c).contains("com.android.chrome"));}finally{CheckoutAccess.end();}assertTrue(CheckoutAccess.packages(c).isEmpty());
 }
}
