package za.co.loopmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Store {
    private static final String ALIAS="loop.local.v1";
    private final SharedPreferences prefs;
    Store(Context c){prefs=c.getSharedPreferences("loop.secure",Context.MODE_PRIVATE);}
    private synchronized SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    synchronized void put(String name,String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String encrypted=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs.edit().putString(name,encrypted).commit())throw new IllegalStateException("Device storage unavailable");
    }
    synchronized String get(String name) throws Exception {
        if((name.equals("device_id")||name.equals("device_token"))&&prefs.contains("device_session")){
            return new org.json.JSONObject(get("device_session")).getString(name);
        }
        String text=prefs.getString(name,null);if(text==null)return "";
        String[] parts=text.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    void establishDevice(String deviceId,String deviceToken) throws Exception {
        put("device_session",new org.json.JSONObject().put("device_id",deviceId).put("device_token",deviceToken).toString());
    }
    void importExtras(PersistableBundle b) throws Exception {
        if(b==null||!get("device_token").isEmpty()||!get("server_url").isEmpty())return;
        String url=b.getString("server_url",EnrollmentConfig.SERVER),pub=b.getString("public_key",EnrollmentConfig.PUBLIC_KEY),token=b.getString("enrollment_token","");
        if(!url.startsWith("https://")||pub.isEmpty()||token.isEmpty())throw new IllegalArgumentException("Incomplete provisioning configuration");
        put("server_url",url);put("public_key",pub);put("enrollment_token",token);
    }
}
