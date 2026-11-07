package za.co.loopmanager;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONObject;

/** Grants only declared, supported managed permissions after QR device-owner enrollment. */
final class ManagedPermissions {
    static void grant(Context context) throws Exception {
        if(!PolicyController.owns(context))return;
        DevicePolicyManager manager=context.getSystemService(DevicePolicyManager.class);
        JSONObject states=new JSONObject();
        for(String permission:new String[]{Manifest.permission.READ_PHONE_STATE,Manifest.permission.POST_NOTIFICATIONS}){
            if(android.os.Build.VERSION.SDK_INT<33&&permission.equals(Manifest.permission.POST_NOTIFICATIONS))continue;
            try{manager.setPermissionGrantState(PolicyController.admin(context),context.getPackageName(),permission,DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);}
            catch(SecurityException|IllegalArgumentException unsupported){/* Platform restrictions are authoritative. */}
            states.put(permission,context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED?"granted":"not_granted");
        }
        new Store(context).put("managed_permissions",states.toString());
    }
}
