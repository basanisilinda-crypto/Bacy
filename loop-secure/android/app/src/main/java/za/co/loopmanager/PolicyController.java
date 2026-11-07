package za.co.loopmanager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.telecom.TelecomManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

final class PolicyController {
    static ComponentName admin(Context c){return new ComponentName(c,LoopAdminReceiver.class);}
    static boolean owns(Context c){return ((DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE)).isDeviceOwnerApp(c.getPackageName());}
    static void protectFinancedDevice(Context c){
        if(owns(c))financeProtection(c.getSystemService(DevicePolicyManager.class),admin(c),true,c.getPackageName());
    }
    private static void financeProtection(DevicePolicyManager pm,ComponentName admin,boolean protectedDevice,String packageName){
        restriction(pm,admin,UserManager.DISALLOW_FACTORY_RESET,protectedDevice);
        restriction(pm,admin,UserManager.DISALLOW_SAFE_BOOT,protectedDevice);
        restriction(pm,admin,UserManager.DISALLOW_ADD_USER,protectedDevice);
        restriction(pm,admin,UserManager.DISALLOW_USER_SWITCH,protectedDevice);
        pm.setUninstallBlocked(admin,packageName,protectedDevice);
        java.util.List<String> existingProtection=pm.getUserControlDisabledPackages(admin);
        java.util.ArrayList<String> protectedPackages=new java.util.ArrayList<>(existingProtection==null?java.util.Collections.emptyList():existingProtection);
        if(protectedDevice&&!protectedPackages.contains(packageName))protectedPackages.add(packageName);
        if(!protectedDevice)protectedPackages.remove(packageName);
        pm.setUserControlDisabledPackages(admin,protectedPackages);
    }
    static synchronized String apply(Context c,Entitlement entitlement) throws Exception {
        Entitlement latest=Entitlement.load(c,new Store(c));
        if(latest!=null&&(entitlement==null||latest.data.optLong("revision")>entitlement.data.optLong("revision")||latest.data.optLong("server_time")>entitlement.data.optLong("server_time")))entitlement=latest;
        if(entitlement==null){
            if(owns(c)&&c.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED)BankApps.allowForLock(c,null);
            return "needs_online_check";
        }
        JSONObject d=entitlement.data,policy=d.getJSONObject("policy");
        if(!owns(c))return d.optBoolean("released")?"released":"unsupported";
        DevicePolicyManager pm=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);ComponentName admin=admin(c);
        if(d.optBoolean("released")){
            leave(c,pm,admin);pm.setLockTaskPackages(admin,new String[0]);financeProtection(pm,admin,false,c.getPackageName());restriction(pm,admin,UserManager.DISALLOW_CONFIG_WIFI,false);pm.setSystemUpdatePolicy(admin,null);
            pm.clearDeviceOwnerApp(c.getPackageName());
            return "released";
        }
        boolean enabled=policy.optBoolean("enforcement_enabled");
        financeProtection(pm,admin,true,c.getPackageName());
        // Connectivity must remain available for payment verification and server recovery.
        restriction(pm,admin,UserManager.DISALLOW_CONFIG_WIFI,false);
        if(!enabled){pm.setSystemUpdatePolicy(admin,null);leave(c,pm,admin);return "unrestricted";}
        int updateStart=policy.optInt("update_window_start",120),updateEnd=policy.optInt("update_window_end",240);
        pm.setSystemUpdatePolicy(admin,updateStart==updateEnd?SystemUpdatePolicy.createAutomaticInstallPolicy():SystemUpdatePolicy.createWindowedInstallPolicy(updateStart,updateEnd));
        if(!entitlement.clockKnown(c)&&!entitlement.manualLock()){
            if(c.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED)BankApps.allowForLock(c,policy);
            // No editable wall-clock fallback. Do not newly lock a rebooted phone on an uncertain deadline.
            return "needs_online_check";
        }
        if(!entitlement.shouldRestrict(c)){leave(c,pm,admin);return "unrestricted";}
        BankApps.allowForLock(c,policy);
        boolean found=!BankApps.installed(c,policy).isEmpty();
        int features=DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD|DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
        if(!policy.optBoolean("block_navigation",true))features|=DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
        if(!policy.optBoolean("block_notification_center",true))features|=DevicePolicyManager.LOCK_TASK_FEATURE_HOME|DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS|DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
        pm.setLockTaskFeatures(admin,features);
        pm.setStatusBarDisabled(admin,policy.optBoolean("block_notification_center",true));
        // Android explicitly preserves emergency calls under this restriction.
        restriction(pm,admin,UserManager.DISALLOW_OUTGOING_CALLS,policy.optBoolean("block_outgoing_calls",true));
        ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        if(am.getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_LOCKED){
            if(c instanceof Activity){((Activity)c).startLockTask();}
            else c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        return am.getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED?(found?"restricted":"restricted_banking_unavailable"):"policy_error";
    }
    private static void restriction(DevicePolicyManager p,ComponentName a,String name,boolean enabled){if(enabled)p.addUserRestriction(a,name);else p.clearUserRestriction(a,name);}
    private static void leave(Context c,DevicePolicyManager p,ComponentName a){
        if(c instanceof Activity){ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);if(am.getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_NONE)((Activity)c).stopLockTask();}
        // Removing our own package finishes its task on Android. Keep it permitted;
        // normal manifest launch mode ensures permission alone never starts kiosk mode.
        p.setLockTaskPackages(a,new String[]{c.getPackageName()});p.clearPackagePersistentPreferredActivities(a,c.getPackageName());c.getPackageManager().clearPackagePreferredActivities(c.getPackageName());
        p.setStatusBarDisabled(a,false);restriction(p,a,UserManager.DISALLOW_OUTGOING_CALLS,false);
    }
}
