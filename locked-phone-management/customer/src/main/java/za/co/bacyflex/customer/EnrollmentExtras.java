package za.co.bacyflex.customer;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

/** Reads a one-use finance code delivered by Android's managed setup QR. */
public final class EnrollmentExtras {
    public static final String CODE_KEY="za.co.bacyflex.customer.ENROLLMENT_CODE";
    private EnrollmentExtras(){}
    public static void capture(Context context,Intent intent){
        if(intent==null)return;
        try{
            PersistableBundle data=intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            if(data==null)return;
            String code=data.getString(CODE_KEY);
            if(code!=null&&code.matches("[0-9a-f]{64}")){
                DeviceSession.store(context,"pending_enrollment",code);
            }
        } catch(Exception ignored) {
            // Do not claim an enrollment was completed without a valid one-time code.
        }
    }
}
