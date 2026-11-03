package za.co.bacyflexadmin;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate native admin app. All approvals require a manager JWT and server-side role checks. */
public final class AdminActivity extends Activity {
    private static final int INK=Color.rgb(24,37,32),GREEN=Color.rgb(17,69,53);
    private static final int GREY=Color.rgb(107,117,111),SURFACE=Color.rgb(246,249,247);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String jwt;
    private JSONArray accounts,pending;
    private LinearLayout page;
    private boolean busy;
    private interface Job {void run() throws Exception;}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(SURFACE);
        getWindow().getNavigationBarColor();
        if(!CloudApi.configured()) {
            begin("Server required","This administrator app needs a configured Supabase cloud.");
            item("Cloud not configured","Set BACY_SUPABASE_URL and BACY_SUPABASE_ANON_KEY at Android build time. This app contains no sample payments.");
        } else login();
    }
    private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+.5);}
    private GradientDrawable shape(int color) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void space(int h){page.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView label(String value,int size,int color,boolean heavy) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        if(heavy)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;
    }
    private void begin(String title,String subtitle){
        LinearLayout base=col();base.setBackgroundColor(SURFACE);
        LinearLayout head=col();head.setPadding(dp(22),dp(18),dp(22),dp(16));head.setBackgroundColor(Color.WHITE);
        head.addView(label("BACY FLEX   /   ADMIN",16,GREEN,true));
        head.addView(label("Locked Phone Management by Basani Selinda",11,GREY,false));
        base.addView(head);
        ScrollView scroll=new ScrollView(this);page=col();
        page.setPadding(dp(22),dp(27),dp(22),dp(45));scroll.addView(page);
        base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(base);
        page.addView(label(title,27,INK,true));space(6);
        page.addView(label(subtitle,13,GREY,false));space(23);
    }
    private void item(String title,String detail){
        LinearLayout box=col();box.setPadding(dp(18),dp(17),dp(18),dp(17));
        box.setBackground(shape(Color.WHITE));
        box.addView(label(title,16,INK,true));
        TextView body=label(detail,13,GREY,false);body.setPadding(0,dp(5),0,0);
        box.addView(body);page.addView(box);space(12);
    }
    private void heading(String s){space(15);page.addView(label(s,19,INK,true));space(15);}
    private void button(String caption,Runnable run){
        TextView b=label(caption,15,Color.WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12),dp(15),dp(12),dp(15));b.setBackground(shape(GREEN));
        page.addView(b);space(12);b.setOnClickListener(v->{if(!busy)run.run();});
    }
    private EditText field(LinearLayout parent,String hint,String text,boolean secret){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(text);
        e.setHint(hint);e.setTextSize(15);
        if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private void notice(String title,String msg){
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();
    }
    private void work(Job job) {
        busy=true;
        io.execute(()->{
            try{job.run();}
            catch(Exception ex){
                String e=ex.getMessage()==null?"Unable to reach server":ex.getMessage();
                runOnUiThread(()->notice("Request failed",e));
            }
            finally{runOnUiThread(()->busy=false);}
        });
    }
    private void login(){
        begin("Manager sign in","Only authorised finance managers can access payment approval.");
        EditText email=field(page,"Manager email","",false);space(9);
        EditText password=field(page,"Password","",true);space(15);
        button("Sign in securely",()->{
            if(email.getText().length()==0||password.getText().length()==0){
                notice("Required","Enter your manager email and password.");return;
            }
            work(()->{
                JSONObject payload=new JSONObject().put("email",email.getText().toString().trim())
                     .put("password",password.getText().toString());
                JSONObject result=new JSONObject(CloudApi.request("POST",
                    "/auth/v1/token?grant_type=password",null,payload));
                String session=result.getString("access_token");
                JSONArray managers=new JSONArray(CloudApi.request("GET",
                    "/rest/v1/finance_managers?select=user_id&limit=1",session,null));
                if(managers.length()==0)throw new IllegalStateException("This login is not an authorised finance manager.");
                runOnUiThread(()->{jwt=session;loading();refresh();});
            });
        });
        item("Payment safety","Returning from a bank app or uploading an EFT reference never approves a payment. Check your actual bank first.");
    }
    private void loading(){begin("Your dashboard","Loading live finance accounts and pending EFT requests.");}
    private void refresh(){
        if(jwt==null)return;
        work(()->{
            JSONArray a=new JSONArray(CloudApi.request("GET",
              "/rest/v1/finance_accounts?select=id,customer_name,device_name,remaining_cents,instalment_cents,next_due_on,status&order=created_at.desc&limit=100",jwt,null));
            JSONArray p=new JSONArray(CloudApi.request("GET",
              "/rest/v1/eft_claims?select=id,account_id,reference,amount_cents,created_at,state&state=eq.pending&order=created_at.asc&limit=100",jwt,null));
            runOnUiThread(()->{accounts=a;pending=p;dashboard();});
        });
    }
    private String rand(int cents){return String.format(Locale.US,"R %,.2f",cents/100.);}
    private void dashboard(){
        begin("Finance dashboard","Live data. Manual EFT approval only.");
        item("Accounts",String.valueOf(accounts.length())+" currently loaded");
        item("Pending EFT requests",String.valueOf(pending.length())+" awaiting bank verification");
        button("Refresh online records",this::refresh);
        button("Register a finance customer",this::registerDialog);
        button("Update verified EFT destination",this::bankDialog);
        heading("Payments to verify");
        HashMap<String,JSONObject> customers=new HashMap<>();
        for(int i=0;i<accounts.length();i++){
            JSONObject a=accounts.optJSONObject(i);
            if(a!=null)customers.put(a.optString("id"),a);
        }
        if(pending.length()==0)item("All caught up","No pending EFT claims.");
        for(int i=0;i<pending.length();i++){
            JSONObject claim=pending.optJSONObject(i);
            if(claim==null)continue;
            JSONObject a=customers.get(claim.optString("account_id"));
            String customer=a==null?"Account "+claim.optString("account_id"):a.optString("customer_name");
            item(customer,rand(claim.optInt("amount_cents"))+
              "\nReference: "+claim.optString("reference")+
              "\nSubmitted: "+claim.optString("created_at")+
              "\nStatus: PENDING — not yet verified");
            String id=claim.optString("id");
            button("Check bank, then decide: "+claim.optString("reference"),()->decide(id,customer));
        }
        heading("Customer accounts");
        for(int i=0;i<accounts.length();i++){
            JSONObject a=accounts.optJSONObject(i);
            if(a==null)continue;
            item(a.optString("customer_name"),
              a.optString("device_name")+"\nRemaining "+rand(a.optInt("remaining_cents"))+
              "\nNext due "+a.optString("next_due_on","None")+
              "\n"+a.optString("status"));
        }
        space(14);
        button("Sign out",()->{jwt=null;accounts=null;pending=null;login();});
        page.addView(label("Device lock and release commands are not connected yet. Approving a claim updates the finance record; it does not yet alter Android restrictions.",12,GREY,false));
    }
    private void decide(String id,String customer){
        new AlertDialog.Builder(this)
          .setTitle("Verify EFT: "+customer)
          .setMessage("Open your banking app yourself and confirm the actual cleared deposit, amount and reference. Have you verified the funds?")
          .setNegativeButton("Not yet",null)
          .setNeutralButton("Reject claim",(d,w)->rejectDialog(id))
          .setPositiveButton("I verified — approve",(d,w)->{
              new AlertDialog.Builder(this).setTitle("Final approval")
                  .setMessage("This permanently records the EFT as approved and advances the customer's due date by 30 days. Continue only if the deposit is genuine.")
                  .setNegativeButton("Cancel",null)
                  .setPositiveButton("Approve",(dialog,which)->work(()->{
                      CloudApi.request("POST","/rest/v1/rpc/approve_eft_claim",jwt,
                        new JSONObject().put("p_claim_id",id));
                      runOnUiThread(()->{
                          notice("Payment approved","The account balance and next due date have been updated on the server.");
                          refresh();
                      });
                  })).show();
          }).show();
    }
    private void rejectDialog(String id){
        EditText reason=new EditText(this);reason.setSingleLine(true);reason.setHint("Reason (optional)");
        new AlertDialog.Builder(this).setTitle("Reject unverified claim").setView(reason)
          .setNegativeButton("Cancel",null)
          .setPositiveButton("Reject",(d,w)->work(()->{
              CloudApi.request("POST","/rest/v1/rpc/reject_eft_claim",jwt,
                new JSONObject().put("p_claim_id",id)
                  .put("p_reason",reason.getText().toString().trim()));
              runOnUiThread(()->{notice("Rejected","No balance or due date was changed.");refresh();});
          })).show();
    }
    private static int cents(String amount){
        BigDecimal n=new BigDecimal(amount.trim().replace(",",""));
        return n.movePointRight(2).intValueExact();
    }
    private void registerDialog(){
        LinearLayout box=col();box.setPadding(dp(22),dp(8),dp(22),0);
        EditText email=field(box,"Verified customer email","",false);
        EditText name=field(box,"Customer full name","",false);
        EditText device=field(box,"Device model","",false);
        EditText total=field(box,"Total finance price in Rand (e.g. 1700)","",false);
        EditText monthly=field(box,"Instalment in Rand (e.g. 300)","",false);
        CheckBox agreement=new CheckBox(this);agreement.setText("I have verified the customer's signed finance agreement");
        box.addView(agreement);
        new AlertDialog.Builder(this).setTitle("Register real finance account")
          .setView(box).setNegativeButton("Cancel",null)
          .setPositiveButton("Register",(d,w)->{
              if(!agreement.isChecked()){notice("Agreement required","Verify the customer's signed agreement before registering.");return;}
              final int totalCents,instCents;
              try{totalCents=cents(total.getText().toString());
                  instCents=cents(monthly.getText().toString());}
              catch(Exception err){notice("Invalid amount","Enter prices in Rand, such as 1700 or 300.00.");return;}
              work(()->{
                  JSONObject payload=new JSONObject()
                    .put("p_customer_email",email.getText().toString().trim())
                    .put("p_customer_name",name.getText().toString().trim())
                    .put("p_device_name",device.getText().toString().trim())
                    .put("p_total_cents",totalCents)
                    .put("p_instalment_cents",instCents)
                    .put("p_signed_agreement_confirmed",true);
                  CloudApi.request("POST","/rest/v1/rpc/create_finance_account",jwt,payload);
                  runOnUiThread(()->{notice("Registered","Customer finance account created in the cloud.");refresh();});
              });
          }).show();
    }
    private void bankDialog(){
        LinearLayout box=col();box.setPadding(dp(22),dp(8),dp(22),0);
        EditText bankName=field(box,"Bank","Capitec",false);
        EditText holder=field(box,"Account holder","",false);
        EditText accountNumber=field(box,"Account number","",false);
        EditText branch=field(box,"Branch code (if needed)","",false);
        CheckBox checked=new CheckBox(this);checked.setText("I have verified that these EFT details are correct");
        box.addView(checked);
        new AlertDialog.Builder(this).setTitle("Customer EFT destination").setView(box)
          .setNegativeButton("Cancel",null).setPositiveButton("Save bank details",(d,w)->{
              if(!checked.isChecked()){notice("Verification needed","Banking details must be verified before publishing.");return;}
              work(()->{
                  JSONObject payload=new JSONObject()
                    .put("p_bank_name",bankName.getText().toString().trim())
                    .put("p_account_holder",holder.getText().toString().trim())
                    .put("p_account_number",accountNumber.getText().toString().trim())
                    .put("p_branch_code",branch.getText().toString().trim())
                    .put("p_verified",true);
                  CloudApi.request("POST","/rest/v1/rpc/set_eft_destination",jwt,payload);
                  runOnUiThread(()->notice("Saved","Verified bank details are now available securely to signed-in customers."));
              });
          }).show();
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
