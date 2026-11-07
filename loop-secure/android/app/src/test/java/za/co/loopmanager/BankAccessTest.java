package za.co.loopmanager;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class BankAccessTest {
 private static final String BANK="capitec.acuity.mobile.prod";
 private void installBank(Context context){
  ApplicationInfo app=new ApplicationInfo();app.packageName=BANK;app.name="Capitec";app.enabled=true;
  PackageInfo info=new PackageInfo();info.packageName=BANK;info.applicationInfo=app;
  Shadows.shadowOf(context.getPackageManager()).installPackage(info);
  ComponentName component=new ComponentName(BANK,BANK+".BankActivity");
  ActivityInfo activity=Shadows.shadowOf(context.getPackageManager()).addActivityIfNotPresent(component);activity.applicationInfo=app;activity.enabled=true;activity.exported=true;
  IntentFilter filter=new IntentFilter(Intent.ACTION_MAIN);filter.addCategory(Intent.CATEGORY_LAUNCHER);
  Shadows.shadowOf(context.getPackageManager()).addIntentFilterForActivity(component,filter);
 }
 private void own(Context c){Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));}
 @Test public void installedBanksRemainAvailableWithoutAccountAndEmptyPolicy()throws Exception{
  Context c=RuntimeEnvironment.getApplication();installBank(c);
  assertEquals(1,BankApps.installed(c,null).size());
  assertEquals(BANK,BankApps.installed(c,new JSONObject("{\"banking_packages\":[]}")).get(0).packageName);
  assertFalse(BankApps.approved(null).contains("com.android.chrome"));
 }
 @Test public void lockedAccountLoadingStillAllowsBanks()throws Exception{
  Context c=RuntimeEnvironment.getApplication();installBank(c);own(c);
  Shadows.shadowOf(c.getSystemService(ActivityManager.class)).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED);
  assertEquals("needs_online_check",PolicyController.apply(c,null));
  assertTrue(Arrays.asList(c.getSystemService(DevicePolicyManager.class).getLockTaskPackages(PolicyController.admin(c))).contains(BANK));
 }
 @Test public void lockedBankLaunchUsesManagedTaskWithoutChangingHome()throws Exception{
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();installBank(activity);own(activity);
  Shadows.shadowOf(activity.getSystemService(ActivityManager.class)).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED);
  BankApps.open(activity,BankApps.installed(activity,null).get(0),null);
  org.robolectric.shadows.ShadowActivity.IntentForResult launched=Shadows.shadowOf(activity).getNextStartedActivityForResult();
  assertEquals(BANK,launched.intent.getComponent().getPackageName());
  ActivityOptions expected=ActivityOptions.makeBasic();expected.setLockTaskEnabled(true);String lockKey=null;
  for(String key:expected.toBundle().keySet())if(key.toLowerCase(java.util.Locale.ROOT).contains("locktask"))lockKey=key;
  assertNotNull("Framework lock-task option missing",lockKey);assertTrue(launched.options.getBoolean(lockKey));
  assertTrue(activity.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0).stream().noneMatch(r->activity.getPackageName().equals(r.activityInfo.packageName)));
 }
 @Test public void lastOpenedBankMovesToTheFirstShortcut()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();installBank(a);String name="za.co.gotyme";ApplicationInfo app=new ApplicationInfo();app.packageName=name;app.enabled=true;PackageInfo info=new PackageInfo();info.packageName=name;info.applicationInfo=app;Shadows.shadowOf(a.getPackageManager()).installPackage(info);ComponentName component=new ComponentName(name,name+".BankActivity");ActivityInfo activity=Shadows.shadowOf(a.getPackageManager()).addActivityIfNotPresent(component);activity.applicationInfo=app;activity.enabled=true;activity.exported=true;IntentFilter filter=new IntentFilter(Intent.ACTION_MAIN);filter.addCategory(Intent.CATEGORY_LAUNCHER);Shadows.shadowOf(a.getPackageManager()).addIntentFilterForActivity(component,filter);BankApps.Choice chosen=BankApps.installed(a,null).stream().filter(b->b.packageName.equals(name)).findFirst().orElseThrow();BankApps.open(a,chosen,null);assertEquals(name,BankApps.installed(a,null).get(0).packageName);
 }
 @Test public void mismatchedLabelsDoNotGrantBankAccess()throws Exception{
  Context c=RuntimeEnvironment.getApplication();ApplicationInfo app=new ApplicationInfo();app.packageName="not.a.bank";app.nonLocalizedLabel="Capitec";app.enabled=true;
  PackageInfo info=new PackageInfo();info.packageName=app.packageName;info.applicationInfo=app;Shadows.shadowOf(c.getPackageManager()).installPackage(info);
  assertTrue(BankApps.installed(c,null).isEmpty());
 }
}
