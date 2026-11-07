package za.co.loopmanager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import org.json.JSONArray;
import org.json.JSONObject;

final class DeviceIdentifiers {
    static JSONObject collect(Context c) throws Exception {
        JSONObject out=new JSONObject().put("status","unsupported").put("imeis",new JSONArray());
        if(!PolicyController.owns(c))return out;
        if(!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))return out;
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return out.put("status","permission_denied");
        TelephonyManager phone=c.getSystemService(TelephonyManager.class);
        JSONArray imeis=new JSONArray();
        try{
            for(int slot=0;slot<Math.min(4,phone.getActiveModemCount());slot++){
                String imei=readOwnerImei(phone,slot);
                if(imei!=null&&imei.matches("[0-9]{14,17}"))imeis.put(imei);
            }
            return out.put("imeis",imeis).put("sim_operator",phone.getSimOperatorName()).put("sim_country",phone.getSimCountryIso()).put("status",imeis.length()>0?"available":"unsupported");
        }catch(SecurityException denied){return out.put("status","permission_denied");}
        catch(UnsupportedOperationException unsupported){return out;}
    }
    // TelephonyManager#getImei documents fully-managed device ownership as an
    // alternative to the system-only READ_PRIVILEGED_PHONE_STATE permission.
    // collect() checks ownership and READ_PHONE_STATE, and catches SecurityException.
    // The SDK permission annotation cannot express that owner-based alternative.
    @android.annotation.SuppressLint("MissingPermission")
    private static String readOwnerImei(TelephonyManager phone,int slot){return phone.getImei(slot);}
}
