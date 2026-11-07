package za.co.loopmanager;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class Api {
    private final Store store;
    Api(Store s){store=s;}
    JSONObject post(String path,JSONObject body,boolean authenticated) throws Exception {
        String base=store.get("server_url");URL url=new URL(base+path);
        if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new SecurityException("A trusted HTTPS server is required");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        if(authenticated)c.setRequestProperty("Authorization","Bearer "+store.get("device_token"));
        try{
            byte[] raw=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(raw.length);
            try(var out=c.getOutputStream()){out.write(raw);}
            int status=c.getResponseCode();InputStream input=status>=200&&status<300?c.getInputStream():c.getErrorStream();
            if(input==null)throw new IllegalStateException("Server unavailable; try again");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(input){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>2*1024*1024)throw new SecurityException("Response too large");bytes.write(buffer,0,n);}}
            JSONObject result=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            if(status<200||status>=300)throw new IllegalStateException(result.optString("detail","Request failed; contact support"));
            return result;
        }finally{c.disconnect();}
    }
}
