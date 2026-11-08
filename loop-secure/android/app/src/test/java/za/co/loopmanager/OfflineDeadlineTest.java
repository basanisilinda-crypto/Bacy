package za.co.loopmanager;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.Duration;
import java.util.TimeZone;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

/** No API or wall-clock lookup is involved in these deadline transitions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={30,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class OfflineDeadlineTest {
    private static final long NOW=2000000000L;
    private Context context(){
        Context c=RuntimeEnvironment.getApplication();
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,7);
        return c;
    }
    private JSONObject account(long due,boolean manual) throws Exception {
        return new JSONObject().put("device_id","offline-fixture").put("revision",4)
            .put("server_time",NOW).put("due",due).put("released",false)
            .put("policy",new JSONObject().put("enforcement_enabled",true)
                .put("manual_lock",manual).put("grace_hours",0));
    }
    private Entitlement current(JSONObject d){return new Entitlement(d,SystemClock.elapsedRealtime(),7);}
    private JSONObject courtesy(JSONObject d,long start,long end)throws Exception{
        d.getJSONObject("policy").put("temporary_unlock_started",start).put("temporary_unlock_until",end);
        return d;
    }

    @Test public void courtesyExpiresAfterFiveMinutesIncludingDeepSleepWithoutNetwork()throws Exception{
        Context c=context();ShadowSystemClock.setNetworkTimeAvailable(false);
        JSONObject d=courtesy(account(NOW-60,true),NOW,NOW+300);
        Entitlement e=current(d);assertEquals(300,e.temporarySecondsLeft(c));assertFalse(e.shouldRestrict(c));
        TimeZone old=TimeZone.getDefault();
        try{
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(299));
            assertEquals(1,e.temporarySecondsLeft(c));assertFalse(e.shouldRestrict(c));
            ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(1));
            assertEquals(0,e.temporarySecondsLeft(c));assertTrue(e.shouldRestrict(c));
            assertTrue("The original administrator lock stays recorded",e.manualLock());
            assertEquals("Courtesy access cannot move the paid deadline",NOW-60,d.getLong("due"));
        }finally{TimeZone.setDefault(old);}
    }

    @Test public void refreshedClockCannotRestartTheSameCourtesyWindow()throws Exception{
        Context c=context();JSONObject d=courtesy(account(NOW-60,true),NOW,NOW+300);
        Entitlement first=current(d);SystemClock.sleep(120000);assertEquals(180,first.temporarySecondsLeft(c));
        JSONObject fresh=new JSONObject(d.toString()).put("server_time",NOW+120);
        Entitlement refreshed=current(fresh);assertEquals(180,refreshed.temporarySecondsLeft(c));
        SystemClock.sleep(180000);assertTrue(refreshed.shouldRestrict(c));assertEquals(0,refreshed.temporarySecondsLeft(c));
    }

    @Test public void malformedOrFutureCourtesyWindowsCannotOpenAccess()throws Exception{
        Context c=context();
        assertTrue(current(courtesy(account(NOW-60,true),NOW,NOW+301)).shouldRestrict(c));
        assertTrue(current(courtesy(account(NOW-60,true),NOW+60,NOW+360)).shouldRestrict(c));
        assertTrue(current(courtesy(account(NOW-60,true),0,300)).shouldRestrict(c));
    }

    @Test public void rebootDoesNotCarryCourtesyOrTurnAnUnknownClockIntoPaidAccess()throws Exception{
        Context c=context();Entitlement e=current(courtesy(account(NOW-60,true),NOW,NOW+300));
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,8);
        assertFalse(e.clockKnown(c));assertEquals(0,e.temporarySecondsLeft(c));assertTrue(e.shouldRestrict(c));
        Entitlement paid=current(account(NOW+86400,false));
        assertTrue("Old boot cannot establish remaining paid time",paid.shouldRestrict(c));
        Entitlement fresh=new Entitlement(account(NOW+86400,false),SystemClock.elapsedRealtime(),8);
        assertFalse("A current verified clock restores the existing paid access",fresh.shouldRestrict(c));
    }

    @Test public void releasedAndDisabledAccountsDoNotGainARebootRestriction()throws Exception{
        Context c=context();JSONObject released=account(NOW-60,true).put("released",true);
        JSONObject disabled=account(NOW-60,true);disabled.getJSONObject("policy").put("enforcement_enabled",false);
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,8);
        assertFalse(current(released).shouldRestrict(c));assertFalse(current(disabled).shouldRestrict(c));
    }

    @Test public void dueGraceAndExplicitGraceAllExpireOnTheLocalElapsedClock()throws Exception{
        Context c=context();JSONObject d=account(NOW+10,false);
        d.getJSONObject("policy").put("grace_hours",1).put("grace_until",NOW+4000);
        Entitlement e=current(d);assertEquals(NOW+4000,e.restrictionDue());
        SystemClock.sleep(10000);assertEquals(AccountTiming.Stage.GRACE,e.stage(c));assertFalse(e.shouldRestrict(c));
        ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(3989));assertFalse(e.shouldRestrict(c));
        SystemClock.sleep(1000);assertTrue(e.shouldRestrict(c));assertEquals(AccountTiming.Stage.OVERDUE,e.stage(c));
    }

    @Test public void offlineCourtesyExpiryReentersManagedLockWithoutRemovingBankOrWifiAccess()throws Exception{
        MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).setup().visible().get();
        Settings.Global.putInt(a.getContentResolver(),Settings.Global.BOOT_COUNT,7);
        org.robolectric.Shadows.shadowOf(a.getSystemService(android.app.admin.DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(a));
        JSONObject d=courtesy(account(NOW-60,true),NOW,NOW+300);Entitlement e=current(d);
        assertEquals("unrestricted",PolicyController.apply(a,e));
        SystemClock.sleep(300000);
        String result=PolicyController.apply(a,e);
        assertTrue(result.equals("restricted")||result.equals("restricted_banking_unavailable"));
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED,a.getSystemService(android.app.ActivityManager.class).getLockTaskModeState());
        assertFalse(a.getSystemService(android.os.UserManager.class).hasUserRestriction(android.os.UserManager.DISALLOW_CONFIG_WIFI));
        java.util.List<String> allowed=java.util.Arrays.asList(a.getSystemService(android.app.admin.DevicePolicyManager.class).getLockTaskPackages(PolicyController.admin(a)));
        assertTrue(allowed.contains("capitec.acuity.mobile.prod"));
        assertTrue(allowed.contains("za.co.fnb.connect.itt"));
    }

    @Test public void aNewAdministratorLockRevokesTheTemporaryOverride()throws Exception{
        Context c=context();JSONObject d=courtesy(account(NOW+86400,true),NOW,NOW+300);
        assertFalse(current(d).shouldRestrict(c));
        JSONObject locked=new JSONObject(d.toString()).put("revision",5);
        locked.getJSONObject("policy").put("temporary_unlock_started",0).put("temporary_unlock_until",0);
        assertTrue(current(locked).shouldRestrict(c));
    }
}
