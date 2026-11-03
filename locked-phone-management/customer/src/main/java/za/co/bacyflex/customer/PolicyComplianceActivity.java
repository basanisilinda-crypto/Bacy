package za.co.bacyflex.customer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/** Displays consent information before Android management setup completes. */
public final class PolicyComplianceActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new AlertDialog.Builder(this)
            .setTitle("Managed phone enrolment")
            .setMessage("Your phone is being enrolled in Locked Phone Management for a financing agreement. The finance provider can apply the device management restrictions explained in your signed agreement. Emergency calling must stay available. Only continue if this is your agreed financed device and you consent to management.")
            .setCancelable(false)
            .setNegativeButton("Cancel", (dialog, which) -> {
                setResult(RESULT_CANCELED);
                finish();
            })
            .setPositiveButton("I agree to enrol", (dialog, which) -> {
                setResult(RESULT_OK);
                finish();
            }).show();
    }
}
