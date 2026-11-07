package za.co.loopmanager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import java.util.LinkedHashSet;
import java.util.Set;

/** Only the phone's trusted system Settings handlers may join the managed task. */
final class DeviceSettings {
    private DeviceSettings() {}
    static Intent target(Context context,boolean wifi){
        Intent intent=new Intent(wifi?Settings.ACTION_WIFI_SETTINGS:Settings.ACTION_SETTINGS);
        ResolveInfo info=context.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY);
        if(info==null||info.activityInfo==null||!info.activityInfo.exported||!info.activityInfo.enabled||"android".equals(info.activityInfo.packageName))return null;
        ApplicationInfo app=info.activityInfo.applicationInfo;
        if(app==null||(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)return null;
        return intent.setClassName(info.activityInfo.packageName,info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static Set<String> packages(Context context){Set<String> result=new LinkedHashSet<>();for(boolean wifi:new boolean[]{false,true}){Intent intent=target(context,wifi);if(intent!=null)result.add(intent.getComponent().getPackageName());}return result;}
    static void open(Activity activity,boolean wifi,org.json.JSONObject policy){
        Intent intent=target(activity,wifi);if(intent==null)throw new IllegalStateException("This phone has no supported system settings screen.");
        BankApps.allowForLock(activity,policy);
        boolean locked=PolicyController.owns(activity)&&activity.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
        if(locked){ActivityOptions options=ActivityOptions.makeBasic();options.setLockTaskEnabled(true);activity.startActivity(intent,options.toBundle());}else activity.startActivity(intent);
    }
}
