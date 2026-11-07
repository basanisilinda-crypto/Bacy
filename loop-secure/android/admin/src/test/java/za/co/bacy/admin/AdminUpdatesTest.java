package za.co.bacy.admin;
import android.content.Context;import android.app.ActivityManager;import android.app.admin.DevicePolicyManager;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class AdminUpdatesTest {
 @Test public void updaterSchedulesWithoutOwnerPrivilegesOrPhoneRestriction(){Context c=RuntimeEnvironment.getApplication();AdminUpdates.schedule(c);AdminUpdates.status(c,"ready","Verified update ready",100);assertFalse(c.getSystemService(DevicePolicyManager.class).isDeviceOwnerApp(c.getPackageName()));assertEquals(ActivityManager.LOCK_TASK_MODE_NONE,c.getSystemService(ActivityManager.class).getLockTaskModeState());assertEquals("ready",c.getSharedPreferences("admin.updates",0).getString("stage",""));assertFalse(AdminUpdates.describe(c).isEmpty());}
}
