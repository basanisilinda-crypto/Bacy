package za.co.loopmanager;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Read-only ownership probe for shell/privileged callers, protected by DUMP in the manifest. */
public final class OwnershipCheckReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String nonce = intent == null ? null : intent.getStringExtra("nonce");
        if (nonce == null || !nonce.matches("[0-9]{2,20}")) {
            setResultCode(0);
            setResultData(null);
            return;
        }
        setResultCode(1);
        setResultData("LoopSecureOwnerCheck:v1:" + nonce + ":" + inspect(context));
    }

    static String inspect(Context context) {
        String owner = "unknown", active = "unknown", linked = "unknown";
        try {
            DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
            if (policy != null) {
                owner = Boolean.toString(policy.isDeviceOwnerApp(context.getPackageName()));
                active = Boolean.toString(policy.isAdminActive(PolicyController.admin(context)));
            }
        } catch (RuntimeException unavailable) { /* Unverified ownership must not permit setup. */ }
        try {
            linked = Boolean.toString(!new Store(context).get("device_token").isEmpty());
        } catch (Exception unavailable) { /* Never treat an unreadable account as unlinked. */ }
        return "owner=" + owner + ";active=" + active + ";linked=" + linked;
    }
}
