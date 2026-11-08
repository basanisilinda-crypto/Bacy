package za.co.loopmanager;

import android.Manifest;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.UserManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

/** Reads current platform state; never equates a requested policy with successful enforcement. */
final class DeviceHealth {
    static String describe(Context context,Entitlement entitlement){
        boolean owner=PolicyController.owns(context);
        ActivityManager activities=context.getSystemService(ActivityManager.class);
        int mode=activities.getLockTaskModeState();
        StringBuilder result=new StringBuilder();
        result.append("Device management: ").append(owner?"Device owner enrolled":"Not device owner");
        result.append("\nPhone restriction: ").append(mode==ActivityManager.LOCK_TASK_MODE_LOCKED?"Managed lock active":mode==ActivityManager.LOCK_TASK_MODE_PINNED?"Ordinary screen pinning (not managed locking)":"Managed lock not active");
        JSONObject policy=entitlement.data.optJSONObject("policy");
        result.append("\nEnforcement setting: ").append(policy!=null&&policy.optBoolean("enforcement_enabled")?"Enabled by administrator":"Disabled by administrator");
        result.append("\nDeadline verification: ").append(entitlement.clockKnown(context)?"Trusted for this boot":"Online check needed after restart");
        result.append("\nLast signed account check: ").append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(entitlement.data.optLong("server_time")*1000)));
        ConnectivityManager network=context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities=network.getNetworkCapabilities(network.getActiveNetwork());
        result.append("\nInternet connection: ").append(capabilities!=null&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Validated by Android":"Not validated");
        result.append("\nNotifications permission: ").append((android.os.Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)?"Granted":"Not granted");
        result.append("\nPhone-state permission: ").append(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED?"Granted":"Not granted");
        if(owner){
            DevicePolicyManager manager=context.getSystemService(DevicePolicyManager.class);
            try{
                Bundle restrictions=manager.getUserRestrictions(PolicyController.admin(context));
                result.append("\nSettings factory reset restriction: ").append(restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET)?"Applied":"Not applied");
                result.append("\nDeveloper options / ADB restriction: ").append(restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES)?"Applied":"Not applied");
                result.append("\nUSB debugging: ").append(android.provider.Settings.Global.getInt(context.getContentResolver(),android.provider.Settings.Global.ADB_ENABLED,0)==0?"Off":"On");
                result.append("\nSafe boot restriction: ").append(restrictions.getBoolean(UserManager.DISALLOW_SAFE_BOOT)?"Applied":"Not applied");
                result.append("\nWi-Fi configuration: ").append(restrictions.getBoolean(UserManager.DISALLOW_CONFIG_WIFI)?"Restricted":"Available");
                result.append("\nManagement app clear-data / force-stop protection: ").append(manager.getUserControlDisabledPackages(PolicyController.admin(context))!=null&&manager.getUserControlDisabledPackages(PolicyController.admin(context)).contains(context.getPackageName())?"Applied":"Not applied");
                result.append("\nManagement app uninstall restriction: ").append(manager.isUninstallBlocked(PolicyController.admin(context),context.getPackageName())?"Applied":"Not applied");
                result.append("\nOrdinary outgoing call restriction: ").append(restrictions.getBoolean(UserManager.DISALLOW_OUTGOING_CALLS)?"Applied":"Not applied");
            }catch(SecurityException unavailable){result.append("\nPolicy details: Unavailable on this phone");}
        }
        result.append("\n\nApproved banking apps with a launch screen:");
        boolean available=false;
        for(BankApps.Choice bank:BankApps.installed(context,policy)){
            result.append("\n• ").append(bank.label);available=true;
            if(owner)result.append(context.getSystemService(DevicePolicyManager.class).isLockTaskPermitted(bank.packageName)?" · allowed in lock mode":" · not allowed in lock mode");
        }
        if(!available)result.append("\nNone found. EFT can be made from another device using your reference.");
        result.append("\n\nWi-Fi and system Settings remain accessible through Loop Secure. Bank-app presence does not prove payment or in-lock compatibility. Emergency calling remains available. Physical button/recovery-mode reset blocking requires manufacturer support and is not guaranteed by standard Android device ownership.");
        return result.toString();
    }
}
