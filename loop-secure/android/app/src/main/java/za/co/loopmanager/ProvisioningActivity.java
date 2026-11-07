package za.co.loopmanager;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;

public class ProvisioningActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        try{new Store(this).importExtras((android.os.Build.VERSION.SDK_INT>=33?getIntent().getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,PersistableBundle.class):(PersistableBundle)getIntent().getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)));}catch(Exception e){setResult(RESULT_CANCELED);finish();return;}
        Intent result=new Intent();
        if(DevicePolicyManager.ACTION_GET_PROVISIONING_MODE.equals(getIntent().getAction()))result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        setResult(RESULT_OK,result);finish();
    }
}
