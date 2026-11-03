package za.co.bacyflex.customer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Protects the device's unique non-login finance credential with Android Keystore. */
public final class DeviceSession {
    private static final String KEY_ALIAS="mbmf.finance.device.v1";
    private static final String PREF="mbmf_private_device";
    private DeviceSession(){}
    private static SecretKey key() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing=store.getKey(KEY_ALIAS,null);
        if(existing instanceof SecretKey)return (SecretKey)existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build();
        generator.init(spec);
        return generator.generateKey();
    }
    private static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    public static void store(Context context,String name,String value) throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String encrypted=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        prefs(context).edit().putString(name+"_iv",iv).putString(name+"_data",encrypted).apply();
    }
    public static String read(Context context,String name){
        try{
            String iv=prefs(context).getString(name+"_iv",null);
            String encrypted=prefs(context).getString(name+"_data",null);
            if(iv==null||encrypted==null)return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(Exception e){return null;}
    }
    public static void clear(Context context,String name){
        prefs(context).edit().remove(name+"_iv").remove(name+"_data").apply();
    }
}
