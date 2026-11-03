package za.co.bacyflex.customer;

import androidx.activity.ComponentActivity;
import android.os.Bundle;

/**
 * Android Enterprise policy-compliance screen, not a fake Device Owner prompt.
 * The platform owns the final provisioning decision; MBMF adds explicit disclosure.
 */
public final class PolicyComplianceActivity extends ComponentActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ClientSetupUi.showConsent(this, this::agree, this::decline);
    }
    private void agree() {
        EnrollmentExtras.capture(this,getIntent());
        setResult(RESULT_OK);
        finish();
    }
    private void decline() {
        DeviceSession.clear(this,"pending_enrollment");
        setResult(RESULT_CANCELED);
        finish();
    }
    @Override public void onBackPressed() {
        decline();
    }
}
