package za.co.loopmanager;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

public class LoopAdminReceiver extends DeviceAdminReceiver {
    @Override public void onProfileProvisioningComplete(Context c,Intent intent){
        try{new Store(c).importExtras((android.os.Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,PersistableBundle.class):(PersistableBundle)intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)));}catch(Exception ignored){}
        SyncJobService.schedule(c);c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
