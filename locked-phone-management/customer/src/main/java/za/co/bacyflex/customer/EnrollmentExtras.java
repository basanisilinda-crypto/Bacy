package za.co.bacyflex.customer;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

/** A valid one-time QR provisioning code is required before a client can link a finance plan. */
public final class EnrollmentExtras {
    public static final String CODE_KEY="za.co.bacyflex.customer.ENROLLMENT_CODE";
    private EnrollmentExtras(){}
    public static boolean capture(Context context,Intent intent){
        if(intent==null)return false;
        try {
            PersistableBundle b=intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            if(b==null)return false;
            String code=b.getString(CODE_KEY);
            if(code==null||!code.matches("[0-9a-f]{64}"))return false;
            DeviceSession.store(context,"pending_enrollment",code);
            return true;
        }catch(Exception ignored){return false;}
    }
}
