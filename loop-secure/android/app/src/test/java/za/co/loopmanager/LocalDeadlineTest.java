package za.co.loopmanager;

import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={30,35})
public class LocalDeadlineTest {
    private static final long NOW=2000000000L;
    private Context owner(){
        Context c=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,7);
        ShadowAlarmManager.setAutoSchedule(false);ShadowAlarmManager.setCanScheduleExactAlarms(true);
        return c;
    }
    private JSONObject data()throws Exception{
        return new JSONObject().put("device_id","alarm-fixture").put("server_time",NOW).put("revision",3)
            .put("due",NOW+100).put("released",false).put("policy",new JSONObject()
                .put("enforcement_enabled",true).put("manual_lock",false).put("grace_hours",1));
    }
    private Entitlement current(JSONObject d){return new Entitlement(d,SystemClock.elapsedRealtime(),7);}
    private ShadowAlarmManager alarms(Context c){return Shadows.shadowOf(c.getSystemService(AlarmManager.class));}

    @Test public void localAlarmUsesElapsedWakeupAndIncludesGraceWithoutAnyNetwork()throws Exception{
        Context c=owner();Entitlement e=current(data());LocalDeadline.schedule(c,e);
        var alarm=alarms(c).peekNextScheduledAlarm();assertNotNull(alarm);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP,alarm.type);assertTrue(alarm.allowWhileIdle);
        assertEquals(e.anchorElapsed+3700000L,alarm.triggerAtTime);
        assertEquals(0,alarm.interval);
        var intent=Shadows.shadowOf(alarm.operation).getSavedIntent();
        assertEquals(LocalDeadline.ACTION,intent.getAction());
        assertEquals(new ComponentName(c,DeadlineReceiver.class),intent.getComponent());
        assertFalse(c.getPackageManager().getReceiverInfo(intent.getComponent(),0).exported);
        LocalDeadline.schedule(c,e);assertEquals("One private deadline alarm",1,alarms(c).getScheduledAlarms().size());
    }

    @Test public void courtesyAlarmReplacesTheFinancialAlarmAndReleasedAccountsCancelIt()throws Exception{
        Context c=owner();JSONObject d=data();LocalDeadline.schedule(c,current(d));
        d.getJSONObject("policy").put("manual_lock",true).put("temporary_unlock_started",NOW).put("temporary_unlock_until",NOW+300);
        Entitlement temporary=current(d);LocalDeadline.schedule(c,temporary);
        assertEquals(1,alarms(c).getScheduledAlarms().size());
        assertEquals(temporary.anchorElapsed+300000L,alarms(c).peekNextScheduledAlarm().triggerAtTime);
        d.put("released",true);LocalDeadline.schedule(c,current(d));assertTrue(alarms(c).getScheduledAlarms().isEmpty());
    }

    @Test public void disabledOrUnknownClockDoesNotScheduleAFalseFinancialDeadline()throws Exception{
        Context c=owner();JSONObject d=data();LocalDeadline.schedule(c,current(d));
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,8);
        LocalDeadline.schedule(c,current(d));assertTrue(alarms(c).getScheduledAlarms().isEmpty());
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,7);
        d.getJSONObject("policy").put("enforcement_enabled",false);
        LocalDeadline.schedule(c,current(d));assertTrue(alarms(c).getScheduledAlarms().isEmpty());
    }

    @Test @Config(sdk=35) public void exactAlarmDenialUsesAnIdleAllowedFallback()throws Exception{
        Context c=owner();ShadowAlarmManager.setCanScheduleExactAlarms(false);LocalDeadline.schedule(c,current(data()));
        var alarm=alarms(c).peekNextScheduledAlarm();assertNotNull(alarm);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP,alarm.type);assertTrue(alarm.allowWhileIdle);
        assertFalse(c.getSharedPreferences("loop.deadline",0).getBoolean("exact",true));
        assertNotEquals("Fallback must not be described as exact",0,alarm.getWindowLengthMs());
    }
}
