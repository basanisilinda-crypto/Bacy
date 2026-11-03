package za.co.bacyflex.customer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/** Required disclosure and affirmative consent during Android QR provisioning. */
public final class PolicyComplianceActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new AlertDialog.Builder(this)
            .setTitle("MBMF phone financing")
            .setMessage("This phone is being enrolled for a device financing agreement. " +
                "Your finance provider can manage the phone only as described in your signed agreement. " +
                "Your instalment and next payment date appear in the MBMF app. " +
                "Emergency calling must remain available. By continuing, you confirm you understand " +
                "the device management terms and agree to this enrolment.")
            .setCancelable(false)
            .setNegativeButton("Decline",(dialog,which)->{
                DeviceSession.clear(this,"pending_enrollment");
                setResult(RESULT_CANCELED);
                finish();
            })
            .setPositiveButton("Agree and continue",(dialog,which)->{
                EnrollmentExtras.capture(this,getIntent());
                setResult(RESULT_OK);
                finish();
            }).show();
    }
}
