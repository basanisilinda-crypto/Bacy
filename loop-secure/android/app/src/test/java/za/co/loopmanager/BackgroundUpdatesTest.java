package za.co.loopmanager;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class BackgroundUpdatesTest {
 private Context owner(){Context c=RuntimeEnvironment.getApplication();Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));return c;}
 @Test public void persistedBackstopAcceptsMobileInternetWithoutOpeningAnActivity(){Context c=owner();ManagedUpdates.schedule(c);JobInfo job=c.getSystemService(JobScheduler.class).getPendingJob(402);assertNotNull(job);assertTrue(job.isPersisted());assertEquals(JobInfo.NETWORK_TYPE_ANY,job.getNetworkType());assertEquals(15*60*1000L,job.getIntervalMillis());assertFalse(job.isRequireBatteryNotLow());assertFalse(job.isRequireDeviceIdle());}
 @Test public void automaticInstallRequiresDeviceOwnershipSignedPermissionAndNoPayment()throws Exception {Context c=RuntimeEnvironment.getApplication();JSONObject m=new JSONObject().put("install_automatically",true).put("requires_approval",false);assertFalse(ManagedUpdates.automaticInstallAllowed(c,m));Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));assertTrue(ManagedUpdates.automaticInstallAllowed(c,m));assertFalse(ManagedUpdates.automaticInstallAllowed(c,new JSONObject(m.toString()).put("requires_approval",true)));assertFalse(ManagedUpdates.automaticInstallAllowed(c,new JSONObject()));CheckoutAccess.begin();try{assertFalse(ManagedUpdates.automaticInstallAllowed(c,m));android.os.SystemClock.sleep(11*60000);assertFalse(CheckoutAccess.active());assertFalse(ManagedUpdates.automaticInstallAllowed(c,m));}finally{CheckoutAccess.end();}assertTrue(ManagedUpdates.automaticInstallAllowed(c,m));}
 @Test public void unsolicitedInstallCallbacksCannotChangeTheExistingSession(){Context c=owner();var p=c.getSharedPreferences("loop.updates",0);p.edit().putInt("session",99).putString("stage","installing").apply();new UpdateReceiver().onReceive(c,new Intent("za.co.loopmanager.UPDATE_RESULT").putExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID,100).putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,android.content.pm.PackageInstaller.STATUS_SUCCESS));assertEquals(99,p.getInt("session",-1));assertEquals("installing",p.getString("stage",""));assertFalse(UpdateApprovalActivity.trustedConfirmation(c,new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.invalid"))));}
 @Test public void approvalTargetsMustBeSystemInstallConfirmationActivities(){Context c=owner();String pkg="com.android.packageinstaller";android.content.pm.ApplicationInfo app=new android.content.pm.ApplicationInfo();app.packageName=pkg;app.enabled=true;app.flags=android.content.pm.ApplicationInfo.FLAG_SYSTEM;android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName=pkg;info.applicationInfo=app;Shadows.shadowOf(c.getPackageManager()).installPackage(info);ComponentName component=new ComponentName(pkg,pkg+".Confirm");android.content.pm.ActivityInfo a=Shadows.shadowOf(c.getPackageManager()).addActivityIfNotPresent(component);a.applicationInfo=app;a.enabled=true;a.exported=true;IntentFilter filter=new IntentFilter("android.content.pm.action.CONFIRM_INSTALL");filter.addCategory(Intent.CATEGORY_DEFAULT);Shadows.shadowOf(c.getPackageManager()).addIntentFilterForActivity(component,filter);Intent i=new Intent("android.content.pm.action.CONFIRM_INSTALL").setComponent(component);assertTrue(UpdateApprovalActivity.trustedConfirmation(c,i));assertFalse(UpdateApprovalActivity.trustedConfirmation(c,new Intent(i).setAction(Intent.ACTION_VIEW)));a.applicationInfo.flags=0;assertFalse(UpdateApprovalActivity.trustedConfirmation(c,i));}
}
