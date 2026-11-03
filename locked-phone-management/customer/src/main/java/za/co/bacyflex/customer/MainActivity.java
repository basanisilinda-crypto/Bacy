package za.co.bacyflex.customer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import za.co.bacyflex.core.Cloud;
import za.co.bacyflex.core.Screen;

public final class MainActivity extends Activity {
    private String token, userId, planId, planStatus;
    private boolean bankConfigured=false;
    private EditText email,password,reference,amount;
    private TextView status,planSummary,bankSummary,paymentHistory;
    private interface Job { String call() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=Screen.start(this,"Locked Phone","Customer payment account  |  By Basani Selinda");
        LinearLayout access=Screen.card(this,root,"Account");
        email=Screen.field(this,access,"Email address",false);
        password=Screen.field(this,access,"Password",true);
        Screen.button(this,access,"Sign in",v->signIn());
        Screen.button(this,access,"Create customer account",v->register());
        status=Screen.label(this,"Sign in to view your registered phone and payment plan.",14,false);
        access.addView(status);

        LinearLayout summary=Screen.card(this,root,"My finance plan");
        planSummary=Screen.label(this,"No account loaded.",15,false);
        summary.addView(planSummary);
        Screen.button(this,summary,"Refresh from server",v->load());

        LinearLayout history=Screen.card(this,root,"Payment history");
        paymentHistory=Screen.label(this,"Sign in to view EFT claims and their approval status.",14,false);
        history.addView(paymentHistory);
        Screen.button(this,history,"Refresh payment status",v->load());

        LinearLayout eft=Screen.card(this,root,"Pay by EFT");
        bankSummary=Screen.label(this,"Bank details will appear after signing in, once verified by the administrator.",15,false);
        eft.addView(bankSummary);
        reference=Screen.field(this,eft,"Your EFT payment reference",false);
        amount=Screen.field(this,eft,"Amount paid in rand (e.g. 300.00)",false);
        Screen.button(this,eft,"Copy EFT reference",v->{
            String value=reference.getText().toString().trim();
            if(value.isEmpty()){status.setText("No payment reference is available yet.");return;}
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("EFT payment reference",value));
                status.setText("Payment reference copied. Paste it into your bank's EFT reference field.");
            }
        });
        Screen.button(this,eft,"Go to home screen to open your banking app",v->{
            Intent i=new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            startActivity(i);
        });
        Screen.button(this,eft,"I paid — submit EFT reference for verification",v->submitClaim());
        eft.addView(Screen.label(this,"Submitting a reference does not unlock the device. Your administrator must verify the money in the bank and approve it.",13,false));
        
        LinearLayout device=Screen.card(this,root,"Device management");
        device.addView(Screen.label(this,"Management requires explicit consent during Android enrolment. Installing this APK by itself does not make it Device Owner.",13,false));
        android.app.admin.DevicePolicyManager manager=(android.app.admin.DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        android.content.ComponentName receiver=new android.content.ComponentName(this,FinanceAdminReceiver.class);
        TextView managementStatus=Screen.label(this,"Checking management status...",14,false);
        device.addView(managementStatus);
        Runnable refreshDevice=()->managementStatus.setText(
            "Device Owner: "+(manager!=null&&manager.isDeviceOwnerApp(getPackageName())?"Active":"Not enrolled")
            +"\\nScreen-lock test permission: "+(manager!=null&&manager.isAdminActive(receiver)?"Enabled":"Off")
            +"\\nAutomatic finance locking: Not enabled");
        refreshDevice.run();
        Screen.button(this,device,"Refresh device status",view->refreshDevice.run());
        Screen.button(this,device,"Allow manual screen-lock test",view->{
            Intent intent=new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,receiver);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "This permission is for a voluntary, manual lock test only. It does not activate finance restrictions.");
            startActivity(intent);
        });
        Screen.button(this,device,"Test lock this screen",view->{
            if(manager==null || !manager.isAdminActive(receiver)){
                status.setText("Enable screen-lock test permission before using this button.");
                return;
            }
            new android.app.AlertDialog.Builder(this)
                .setTitle("Lock this phone now?")
                .setMessage("This is a local test initiated by you. You can unlock normally using your PIN or fingerprint. Automatic missed-payment locking is not active.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Lock now",(dialog,which)->manager.lockNow())
                .show();
        });
        Screen.tabs(this,root,
            new String[]{"Account","Payments","Device"},
            new LinearLayout[][]{
                {access,summary},
                {eft,history},
                {device}
            });
    }

    @Override protected void onResume() {
        super.onResume();
        if(token!=null && planSummary!=null) load();
    }

    private void work(Job job) {
        status.setText("Connecting securely...");
        new Thread(()->{
            try {
                String result=job.call();
                runOnUiThread(()->status.setText(result));
            } catch(Exception e) {
                runOnUiThread(()->status.setText("Unable to complete: "+e.getMessage()));
            }
        }).start();
    }

    private void register() {
        String e=email.getText().toString().trim(), p=password.getText().toString();
        if(e.isEmpty()||p.length()<8) {status.setText("Enter an email and password of at least 8 characters.");return;}
        work(()->{Cloud.signUp(e,p);return "Account request sent. Confirm your email if prompted, then sign in.";});
    }

    private void signIn() {
        String e=email.getText().toString().trim(), p=password.getText().toString();
        work(()->{
            JSONObject login=Cloud.signIn(e,p);
            token=login.getString("access_token");
            userId=login.getJSONObject("user").getString("id");
            JSONArray profile=Cloud.rows("finance_customers?select=id&id=eq."+userId,token);
            if(profile.length()==0) Cloud.submit("finance_customers",new JSONObject().put("id",userId).put("display_name",e),token);
            return fetchPlan();
        });
    }

    private void load() {
        if(token==null) {status.setText("Sign in first.");return;}
        work(this::fetchPlan);
    }

    private String fetchPlan() throws Exception {
        JSONArray records=Cloud.rows("finance_plans?select=id,installment_cents,balance_cents,next_due_date,status&limit=1",token);
        JSONArray banks=Cloud.rows("finance_payment_settings?select=bank_name,account_holder,account_number,branch_code,instructions&is_active=eq.true&limit=1",token);
        bankConfigured=banks.length()>0;
        JSONArray claims=Cloud.rows("finance_payment_claims?select=eft_reference,amount_cents,status,submitted_at,decided_at&order=submitted_at.desc&limit=20",token);
        final String historyText=formatHistory(claims);
        String bankText="The administrator has not published verified EFT beneficiary details.";
        if(bankConfigured) {
            JSONObject bank=banks.getJSONObject(0);
            bankText=bank.getString("bank_name")+"\nName: "+bank.getString("account_holder")
                +"\nAccount: "+bank.getString("account_number")
                +"\nBranch: "+bank.optString("branch_code","")
                +"\n"+bank.optString("instructions","");
        }
        final String displayBank=bankText;
        if(records.length()==0) {
            planId=null;
            planStatus=null;
            runOnUiThread(()->{planSummary.setText("No finance plan is assigned to this account yet. Your account ID is: "+userId); bankSummary.setText(displayBank); paymentHistory.setText(historyText);});
            return "Signed in. Awaiting administrator enrolment.";
        }
        JSONObject plan=records.getJSONObject(0);
        planId=plan.getString("id");
        planStatus=plan.getString("status");
        long installment=plan.getLong("installment_cents"),balance=plan.getLong("balance_cents");
        String due=plan.isNull("next_due_date")?"Paid in full":plan.optString("next_due_date");
        String summary="Status: "+plan.getString("status")+"\nInstalment: R"+money(installment)
           +"\nOutstanding: R"+money(balance)+"\nNext due: "+due;
        String newReference="BCY-"+planId.substring(0,8).toUpperCase(Locale.ROOT)+"-"
            +new SimpleDateFormat("yyMMdd",Locale.US).format(new Date())+"-"
            +UUID.randomUUID().toString().substring(0,4).toUpperCase(Locale.ROOT);
        runOnUiThread(()->{
            planSummary.setText(summary);bankSummary.setText(displayBank);paymentHistory.setText(historyText);
            amount.setText(money(Math.min(installment,balance)));reference.setText(newReference);
        });
        return "Account synchronised with the live server.";
    }

    private void submitClaim() {
        if("settled".equals(planStatus)) {status.setText("Your finance plan is settled. No further payments are due.");return;}
        if(token==null||planId==null||!bankConfigured) {
            status.setText("You need an active plan and verified bank details before submitting an EFT claim.");return;
        }
        String ref=reference.getText().toString().trim(), raw=amount.getText().toString().trim();
        if(ref.length()<5) {status.setText("Enter the reference used in your banking app.");return;}
        final long cents;
        try {
            cents=new BigDecimal(raw).movePointRight(2).longValueExact();
            if(cents<=0) throw new NumberFormatException();
        } catch(Exception e) {status.setText("Enter a valid payment amount in rand.");return;}
        final String thisPlan=planId, customer=userId, auth=token;
        work(()->{
            JSONObject claim=new JSONObject().put("plan_id",thisPlan).put("customer_id",customer)
                .put("eft_reference",ref).put("amount_cents",cents);
            Cloud.submit("finance_payment_claims",claim,auth);
            fetchPlan();
            return "EFT claim received. Status: pending administrator bank verification.";
        });
    }

    private static String formatHistory(JSONArray claims) {
        if(claims.length()==0)return "No EFT submissions yet.";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<claims.length();i++) {
            JSONObject p=claims.optJSONObject(i);
            if(p==null)continue;
            if(out.length()>0)out.append("\n\n");
            String status=p.optString("status","pending");
            out.append("R ").append(money(p.optLong("amount_cents")))
               .append("  •  ").append(status.toUpperCase(Locale.ROOT))
               .append("\nReference: ").append(p.optString("eft_reference","—"));
            if(!p.isNull("decided_at"))out.append("\nReviewed: ").append(p.optString("decided_at",""));
        }
        return out.toString();
    }
    private static String money(long cents) {return BigDecimal.valueOf(cents,2).toPlainString();}
}

