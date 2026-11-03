package za.co.bacyflex.admin;

import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates a customer-specific Android Setup Wizard QR.
 * Copies the validated APK URL/checksum from an actual GitHub release, then adds
 * a random single-use customer code. The code is never in the public repository.
 */
public final class AdminEnrollmentQr{
    private static final String RELEASES=
      "https://api.github.com/repos/basanisilinda-crypto/Bacy/releases?per_page=12";
    private static final String EXTRA="za.co.bacyflex.customer.ENROLLMENT_CODE";
    private AdminEnrollmentQr(){}

    private static String fetch(String where) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(where).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestProperty("Accept","application/vnd.github+json");
        c.setRequestProperty("User-Agent","MBMF-Android-Pilot/1.0");
        try{
            if(c.getResponseCode()!=200)throw new IllegalStateException("Could not fetch setup release");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){
                byte[] data=new byte[4096];int n;
                while((n=in.read(data))!=-1){
                    b.write(data,0,n);
                    if(b.size()>300000)throw new IllegalStateException("Unexpected setup file size");
                }
                return b.toString("UTF-8");
            }
        }finally{c.disconnect();}
    }

    public static Bitmap create(String code) throws Exception {
        if(code==null||!code.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid activation code");
        JSONArray releases=new JSONArray(fetch(RELEASES));
        String jsonUrl=null;
        for(int i=0;i<releases.length();i++){
            JSONObject release=releases.optJSONObject(i);
            if(release==null||!release.optString("tag_name").startsWith("phone-enrolment-pilot-"))continue;
            JSONArray assets=release.optJSONArray("assets");
            if(assets==null)continue;
            for(int j=0;j<assets.length();j++){
                JSONObject asset=assets.optJSONObject(j);
                if(asset!=null&&"enrollment-qr.json".equals(asset.optString("name"))){
                    jsonUrl=asset.optString("browser_download_url");
                    break;
                }
            }
            if(jsonUrl!=null)break;
        }
        if(jsonUrl==null)throw new IllegalStateException("No published phone setup QR found");
        JSONObject payload=new JSONObject(fetch(jsonUrl));
        String location=payload.optString("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION");
        String hash=payload.optString("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM");
        if(!location.startsWith("https://github.com/basanisilinda-crypto/Bacy/releases/download/")
           || !hash.matches("[A-Za-z0-9_-]{43}"))throw new IllegalStateException("Setup release is invalid");
        JSONObject extras=new JSONObject().put(EXTRA,code);
        payload.put("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE",extras);

        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN,3);
        BitMatrix matrix=new MultiFormatWriter().encode(payload.toString(),
            BarcodeFormat.QR_CODE,770,770,hints);
        Bitmap bmp=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.ARGB_8888);
        for(int y=0;y<matrix.getHeight();y++)
            for(int x=0;x<matrix.getWidth();x++)
                bmp.setPixel(x,y,matrix.get(x,y)?0xff15283f:0xffffffff);
        return bmp;
    }
}
