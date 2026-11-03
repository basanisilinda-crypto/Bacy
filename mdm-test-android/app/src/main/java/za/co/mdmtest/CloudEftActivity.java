package za.co.mdmtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Live customer finance app: requires a configured Supabase project and an authenticated account. */
public final class CloudEftActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final int GREEN=Color.rgb(18,67,53), INK=Color.rgb(26,38,34);
    private static final int GREY=Color.rgb(106,116,110), BG=Color.rgb(247,249,246);
    private String jwt;
    private JSONObject account, bank;
    private JSONArray claims;
    private LinearLayout page;
    private boolean busy;
    private interface Job { void execute() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (!CloudApi.configured()) showSetup(); else showLogin();
    }
    private int dp(int x) {return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(17));return g;
    }
    private LinearLayout col() {LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;}
    private void pad(LinearLayout c) {c.setPadding(dp(19),dp(17),dp(19),dp(17));}
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private void gap(int h) {View v=new View(this);page.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void line(String txt,int size,boolean bold) {page.addView(text(txt,size,bold?INK:GREY,bold));gap(9);}
    private void begin(String title,String subtitle) {
        LinearLayout root=col();root.setBackgroundColor(BG);
        LinearLayout header=col();pad(header);header.setBackgroundColor(Color.WHITE);
        header.addView(text("BACY FLEX",15,GREEN,true));
        header.addView(text("Locked Phone Management by Basani Selinda",11,GREY,false));
        root.addView(header);
        ScrollView scroll=new ScrollView(this);page=col();page.setPadding(dp(22),dp(28),dp(22),dp(38));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        line(title,27,true);line(subtitle,13,false);gap(15);
    }
    private void card(String heading,String body) {
        LinearLayout c=col();pad(c);c.setBackground(bg(Color.WHITE));
        c.addView(text(heading,16,INK,true));c.addView(text(body,14,GREY,false));
        page.addView(c,new LinearLayout.LayoutParams(-1,-2));gap(14);
    }
    private void button(String label,Runnable action) {
        TextView b=text(label,15,Color.WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(17),dp(10),dp(17));b.setBackground(bg(GREEN));
        page.addView(b,new LinearLayout.LayoutParams(-1,-2));gap(12);
        b.setOnClickListener(v->{ if(!busy)action.run(); });
    }
    private void toast(String title,String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("OK",null).show();
    }
    private void task(Job job) {
        busy=true;
        io.execute(()->{
            try {job.execute();}
            catch(Exception e) {
                String msg=e.getMessage()==null?"Network request failed":e.getMessage();
                runOnUiThread(()->toast("Unable to continue",msg));
            } finally {runOnUiThread(()->busy=false);}
        });
    }
    private void showSetup() {
        begin("Cloud setup needed","This is the real customer app, not a simulated account.");
        card("Server not configured",
           "A verified Supabase project URL and public client key must be included when building the APK. No payment, login or restriction will be claimed as working until the server is connected.");
    }
    private void showLogin(){
        begin("Sign in","Access your actual finance agreement and EFT submissions.");
        final EditText email=new EditText(this);email.setSingleLine(true);
        email.setHint("Email address");email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        page.addView(email);gap(10);
        final EditText password=new EditText(this);password.setSingleLine(true);
        password.setHint("Password");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(password);gap(14);
        button("Sign in securely",()->{
            String e=email.getText().toString().trim(),p=password.getText().toString();
            if(e.isEmpty()||p.isEmpty()){toast("Missing details","Enter your email and password.");return;}
            task(()->{
                JSONObject data=new JSONObject().put("email",e).put("password",p);
                JSONObject reply=new JSONObject(CloudApi.request("POST",
                  "/auth/v1/token?grant_type=password",null,data));
                String token=reply.getString("access_token");
                runOnUiThread(()->{jwt=token;showLoading();refresh();});
            });
        });
        gap(8);
        card("Private and verified","Your payment is not confirmed when you submit a reference. Only the finance manager can approve it after checking the bank. Passwords are never saved by this app.");
    }
    private void showLoading(){
        begin("Your finance account","Loading verified records from the server.");
    }
    private void refresh(){
        if(jwt==null)return;
        task(()->{
            JSONArray a=new JSONArray(CloudApi.request("GET",
              "/rest/v1/finance_accounts?select=id,customer_name,device_name,remaining_cents,instalment_cents,next_due_on,status,access_valid_until&limit=1",
              jwt,null));
            if(a.length()==0)throw new IllegalStateException("No finance account is linked to this login. Ask the manager to register it.");
            JSONObject one=a.getJSONObject(0);
            JSONArray b=new JSONArray(CloudApi.request("GET",
              "/rest/v1/eft_destinations?select=bank_name,account_holder,account_number,branch_code&verified=eq.true&limit=1",
              jwt,null));
            String id=one.getString("id");
            if(!id.matches("[a-fA-F0-9-]{36}"))throw new IllegalStateException("Invalid account record");
            JSONArray payments=new JSONArray(CloudApi.request("GET",
              "/rest/v1/eft_claims?select=reference,amount_cents,state,created_at&account_id=eq."+id+"&order=created_at.desc&limit=10",
              jwt,null));
            runOnUiThread(()->{
                account=one;bank=b.length()==0?null:b.optJSONObject(0);claims=payments;showAccount();
            });
        });
    }
    private String zar(int cents) {return String.format(Locale.US,"R %,.2f",cents/100.0);}
    private String nextReference() {
        String id=account.optString("id","");
        String due=account.optString("next_due_on","").replace("-","");
        return "BACY"+id.substring(Math.max(0,id.length()-6)).toUpperCase(Locale.US)+due;
    }
    private void showAccount(){
        begin("Your payments","Verified account data from the finance server.");
        boolean paid="paid_in_full".equals(account.optString("status"));
        int remaining=account.optInt("remaining_cents");
        int amount=Math.min(remaining,account.optInt("instalment_cents"));
        card("Account holder",account.optString("customer_name")+"\n"+account.optString("device_name"));
        card(paid?"PAID IN FULL":"Next EFT payment",paid?
          "Balance R 0.00. No further instalments are due.":
          zar(amount)+"\nDue: "+account.optString("next_due_on")+
          "\nOutstanding: "+zar(remaining));
        if(!paid) {
            if(bank==null)card("Bank details unavailable",
               "The manager has not yet verified the EFT destination. Do not pay unverified account details.");
            else {
                String details=bank.optString("bank_name")+"\n"+bank.optString("account_holder")+
                  "\nAccount: "+bank.optString("account_number")+
                  "\nBranch: "+bank.optString("branch_code","Not required")+
                  "\nEFT reference: "+nextReference();
                card("Verified EFT destination",details);
                button("Copy account number",()->copy("Account number",bank.optString("account_number")));
                button("Copy payment reference",()->copy("EFT reference",nextReference()));
                button("Open my banking app",this::openBanking);
                button("I have paid — submit reference",()->submitDialog(amount));
            }
        }
        gap(12);line("Payment history",19,true);
        if(claims==null||claims.length()==0)line("No submitted EFT claims.",13,false);
        else for(int i=0;i<claims.length();i++){
            JSONObject c=claims.optJSONObject(i);
            if(c!=null)card(c.optString("state").toUpperCase(Locale.US),
              c.optString("reference")+"   ·   "+zar(c.optInt("amount_cents")));
        }
        button("Refresh from server",this::refresh);
        button("Sign out",()->{jwt=null;account=null;bank=null;claims=null;showLogin();});
        gap(12);
        DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean owner=dpm!=null&&dpm.isDeviceOwnerApp(getPackageName());
        line("Device enrollment: "+(owner?"Device Owner active":"Not enrolled"),12,false);
        line("No payment screenshot or bank-app switch is treated as proof of payment. Emergency calling must remain available on managed phones.",12,false);
    }
    private void copy(String label,String value) {
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label,value));
        toast("Copied",label+" copied. Check the details in your banking app before sending.");
    }
    private void openBanking() {
        Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all=getPackageManager().queryIntentActivities(query,0);
        ArrayList<ResolveInfo> matches=new ArrayList<>();
        for(ResolveInfo ri:all) {
            String label=ri.loadLabel(getPackageManager()).toString().toLowerCase(Locale.US);
            if(label.contains("capitec")||label.contains("tymebank")||label.contains("gotyme"))
                matches.add(ri);
        }
        if(matches.isEmpty()){toast("Open banking app","Open your Capitec or TymeBank app manually and make an EFT using the verified details above.");return;}
        CharSequence[] labels=new CharSequence[matches.size()];
        for(int i=0;i<matches.size();i++)labels[i]=matches.get(i).loadLabel(getPackageManager());
        new AlertDialog.Builder(this).setTitle("Choose your banking app").setItems(labels,(dialog,which)->{
            Intent launch=getPackageManager().getLaunchIntentForPackage(matches.get(which).activityInfo.packageName);
            if(launch==null)toast("Unavailable","Open your banking app manually.");
            else try {startActivity(launch);}catch(Exception ex){toast("Unable to open","Launch the banking app manually.");}
        }).show();
    }
    private void submitDialog(int amount){
        EditText reference=new EditText(this);reference.setSingleLine(true);
        reference.setText(nextReference());reference.setSelectAllOnFocus(true);
        reference.setHint("Exact reference used in your EFT");
        new AlertDialog.Builder(this)
         .setTitle("Submit EFT reference")
         .setMessage("Only submit after making the EFT. Amount "+zar(amount)+
           ". The request stays pending until the manager checks the bank and approves it.")
         .setView(reference).setNegativeButton("Cancel",null)
         .setPositiveButton("Submit",(d,w)->{
            String value=reference.getText().toString().trim();
            if(value.length()<4||value.length()>80){toast("Invalid reference","Use a reference between 4 and 80 characters.");return;}
            task(()->{
                JSONObject body=new JSONObject().put("p_account_id",account.getString("id"))
                    .put("p_reference",value).put("p_amount_cents",amount);
                CloudApi.request("POST","/rest/v1/rpc/submit_eft_claim",jwt,body);
                runOnUiThread(()->{
                    toast("Submitted — pending approval","The manager must confirm the money in their banking app. Submitting cannot unlock a device.");
                    refresh();
                });
            });
        }).show();
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
