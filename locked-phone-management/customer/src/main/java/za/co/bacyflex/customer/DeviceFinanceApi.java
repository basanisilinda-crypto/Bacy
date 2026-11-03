package za.co.bacyflex.customer;

import java.security.SecureRandom;
import org.json.JSONObject;
import za.co.bacyflex.core.Cloud;

/** HTTPS calls to finance-device; this APK contains no server secret key. */
public final class DeviceFinanceApi{
    private DeviceFinanceApi(){}
    public static String newSecret(){
        byte[] data=new byte[32];
        new SecureRandom().nextBytes(data);
        StringBuilder s=new StringBuilder(64);
        for(byte b:data)s.append(String.format(java.util.Locale.US,"%02x",b&0xff));
        return s.toString();
    }
    private static JSONObject call(JSONObject request) throws Exception{
        String body=Cloud.request("POST","functions/v1/finance-device",request,null);
        return new JSONObject(body);
    }
    public static JSONObject activate(String code,String deviceSecret) throws Exception{
        return call(new JSONObject().put("action","activate")
            .put("enrollment_code",code).put("device_secret",deviceSecret));
    }
    public static JSONObject snapshot(String deviceSecret) throws Exception{
        return call(new JSONObject().put("action","snapshot").put("device_secret",deviceSecret));
    }
    public static JSONObject claim(String deviceSecret,String reference,long cents) throws Exception{
        return call(new JSONObject().put("action","claim").put("device_secret",deviceSecret)
            .put("reference",reference).put("amount_cents",cents));
    }
}
