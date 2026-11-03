package za.co.bacyflex.customer;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Management receiver for informed Android setup-time Device Owner enrolment. */
public final class FinanceAdminReceiver extends DeviceAdminReceiver {
    @Override public void onProfileProvisioningComplete(Context context,Intent intent) {
        EnrollmentExtras.capture(context,intent);
        super.onProfileProvisioningComplete(context,intent);
    }
}
