package za.co.loopmanager;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** DUMP-protected bootstrap for an already device-owned handset. Does not grant ownership itself. */
public class AdbEnrollmentActivity extends AppCompatActivity {
    private static final String SERVER="https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            if(!PolicyController.owns(this))throw new IllegalStateException("Android has not assigned this app device ownership. Finish the Windows ADB ownership step first.");
            Store store=new Store(this);
            if(!store.get("device_token").isEmpty()){
                startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return;
            }
            String token=getIntent().getStringExtra("enrollment_token");
            if(token==null||!token.matches("[A-Za-z0-9_-]{20,100}"))throw new IllegalArgumentException("Invalid or missing one-time enrollment code.");
            if(getIntent().getBooleanExtra("administrator_confirmed",false)){
                android.widget.LinearLayout progress=new android.widget.LinearLayout(this);progress.setOrientation(android.widget.LinearLayout.VERTICAL);progress.setGravity(android.view.Gravity.CENTER);progress.setPadding(32,32,32,32);progress.setBackgroundColor(za.co.bacy.ui.CustomerHome.PAPER);progress.addView(new za.co.bacy.ui.CustomerHome.Logo(this),new android.widget.LinearLayout.LayoutParams(96,96));android.widget.TextView message=new android.widget.TextView(this);message.setText("Connecting customer account…\nKeep this phone connected to the internet.");message.setTextSize(17);message.setTextColor(za.co.bacy.ui.CustomerHome.INK);message.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,false));message.setGravity(android.view.Gravity.CENTER);message.setPadding(0,32,0,32);progress.addView(message);progress.addView(new android.widget.ProgressBar(this));setContentView(progress);connect(token);return;
            }
            new MaterialAlertDialogBuilder(this).setTitle("Connect this managed phone?")
                .setMessage("Your administrator supplied a one-time customer enrollment code over USB. Continue only for the intended customer. This connects the phone to Loop Secure and applies the agreed finance-management policy.")
                .setPositiveButton("Connect customer account",(dialog,which)->connect(token))
                .setNegativeButton("Cancel",(dialog,which)->finish()).setOnCancelListener(dialog->finish()).show();
        }catch(Exception error){fail(error);}
    }
    private void connect(String token){
        new Thread(()->{
            try{
                HttpsURLConnection connection=(HttpsURLConnection)new URL(SERVER+"/health").openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
                JSONObject health;
                try{
                    if(connection.getResponseCode()!=200)throw new IllegalStateException("Could not verify the Loop Secure server. Connect the phone to the internet and retry.");
                    try(var stream=connection.getInputStream()){
                        byte[] bytes=za.co.bacy.ui.BoundedInput.read(stream,65536);if(bytes.length>65536)throw new IllegalStateException("Unexpected server response.");
                        health=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                    }
                }finally{connection.disconnect();}
                String key=health.getString("public_key");if(key.isEmpty())throw new IllegalStateException("Server signing key unavailable.");
                Store store=new Store(this);
                if(!PolicyController.owns(this)||!store.get("device_token").isEmpty())throw new IllegalStateException("Device enrollment state changed. Contact your administrator.");
                boolean sameCode=token.equals(store.get("enrollment_token"));
                store.put("server_url",SERVER);store.put("public_key",key);store.put("enrollment_token",token);if(!sameCode)store.put("activation_nonce","");
                runOnUiThread(()->{if(isFinishing())return;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
            }catch(Exception error){runOnUiThread(()->fail(error));}
        },"BacyUsbEnrollment").start();
    }
    private void fail(Exception error){if(!isFinishing())new MaterialAlertDialogBuilder(this).setTitle("USB enrollment").setMessage(error.getMessage()).setPositiveButton("Close",(dialog,which)->finish()).show();}
}
