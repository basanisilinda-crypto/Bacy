package za.co.bacyflex.customer;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;

/** Android 12+ fully-managed device provisioning handler. */
public final class ProvisioningModeActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ArrayList<Integer> modes = getIntent().getIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES);
        if (modes == null || !modes.contains(
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE)) {
            setResult(RESULT_CANCELED);
        } else {
            Intent response = new Intent();
            response.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
            setResult(RESULT_OK, response);
        }
        finish();
    }
}
