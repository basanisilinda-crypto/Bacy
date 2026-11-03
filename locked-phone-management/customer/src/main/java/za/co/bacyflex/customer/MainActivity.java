package za.co.bacyflex.customer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Locale;
import za.co.bacyflex.core.Screen;

/**
 * No-customer-signup MBMF app.
 * A customer is registered by the finance administrator and paired once through
 * the consented Android setup QR, not through an end-user email/password.
 */
public final class MainActivity extends Activity {
    private TextView notice, deviceSummary, planSummary, bankSummary, history, ownerSummary, support, balanceHero, dueHero;
    private LinearLayout mainRoot;
    private EditText eftReference, eftAmount;
    private JSONObject currentAccount;
    private boolean bankReady=false;
    private interface Job { String run() throws Exception; }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=Screen.start(this,"Your phone","A simpler way to follow your instalments");
        mainRoot=root;

        LinearLayout home=Screen.card(this,root,"BALANCE REMAINING");
        balanceHero=Screen.label(this,"—",46,true);
        balanceHero.setTextColor(Screen.WINE);
        balanceHero.setPadding(0,0,0,5);
        home.addView(balanceHero);
        dueHero=Screen.label(this,"Next payment date appears here",16,false);
        dueHero.setTextColor(Screen.CLAY);
        home.addView(dueHero);
        deviceSummary=Screen.label(this,Build.MANUFACTURER+" "+Build.MODEL,15,true);
        home.addView(deviceSummary);
        notice=Screen.label(this,"Loading your phone agreement...",13,false);
        home.addView(notice);

        LinearLayout plan=Screen.card(this,root,"YOUR AGREEMENT");
        planSummary=Screen.label(this,"Your phone and payment details appear here after setup.",15,false);
        plan.addView(planSummary);
        Screen.button(this,plan,"Refresh account",v->refresh());

        LinearLayout actions=Screen.card(this,root,"WHAT WOULD YOU LIKE TO DO?");
        Screen.action(this,actions,"Pay an instalment","Bank transfer details and payment confirmation",v->Screen.showTab(mainRoot,1));
        Screen.action(this,actions,"Read your agreement","See instalments, next date and remaining balance",v->
            new AlertDialog.Builder(this).setTitle("Your agreement").setMessage(planSummary.getText()).setPositiveButton("Close",null).show());
        Screen.action(this,actions,"About this phone","View your enrolled device information",v->Screen.showTab(mainRoot,2));
        Screen.action(this,actions,"Speak to support","Help with a payment or a finance question",v->Screen.showTab(mainRoot,3));

        LinearLayout payment=Screen.card(this,root,"BANK TRANSFER");
        bankSummary=Screen.label(this,"Payment details are available after phone setup.",14,false);
        payment.addView(bankSummary);
        eftReference=Screen.field(this,payment,"Bank payment reference",false);
        eftAmount=Screen.field(this,payment,"Amount paid (R)",false);
        Screen.button(this,payment,"Copy EFT reference",v->copyReference());
        Screen.button(this,payment,"Open phone home to use your banking app",v->{
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);
        });
        Screen.button(this,payment,"I paid · Submit reference for review",v->submit());
        payment.addView(Screen.label(this,
            "Submitting a reference does not prove an EFT was received. Your finance provider verifies the money and approves it.",12,false));

        LinearLayout payments=Screen.card(this,root,"PAYMENT RECORD");
        history=Screen.label(this,"No payments recorded.",14,false);
        payments.addView(history);
        Screen.button(this,payments,"Check payment status",v->refresh());

        LinearLayout device=Screen.card(this,root,"PHONE DETAILS");
        ownerSummary=Screen.label(this,"Checking Android management status...",14,false);
        device.addView(ownerSummary);
        Screen.button(this,device,"Refresh device information",v->refreshOwner());
        Screen.action(this,device,"Review Android permissions",
            "See Device Owner status and the permission explanation",v->{
                Intent setup = new Intent(this, WelcomeSetupActivity.class);
                setup.putExtra("from_main", true);
                setup.putExtra("review_setup", true);
                startActivity(setup);
            });

        LinearLayout help=Screen.card(this,root,"HERE TO HELP");
        support=Screen.label(this,
            "For payment questions, contact your finance provider using the phone number or email on your agreement. " +
            "This app does not restrict emergency calling.",14,false);
        help.addView(support);
        Screen.button(this,help,"Check my plan",v->refresh());

        Screen.tabs(this,root,
            new String[]{"Balance","Pay","Phone","Help"},
            new LinearLayout[][]{
                {home,plan,actions},
                {payment,payments},
                {device},
                {help}
            });
        refreshOwner();
    }

    @Override protected void onResume(){
        super.onResume();
        if(notice!=null) refresh();
    }

    private boolean isOwner(){
        DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        return dpm!=null && dpm.isDeviceOwnerApp(getPackageName());
    }

    private void refreshOwner(){
        if(ownerSummary==null)return;
        String message="Device: "+Build.MANUFACTURER+" "+Build.MODEL+
            "\nAndroid: "+Build.VERSION.RELEASE+
            "\nDevice Owner: "+(isOwner()?"Enrolled":"Not enrolled")+
            "\nFinance restriction: Not active";
        ownerSummary.setText(message);
    }

    private void work(Job job){
        if(notice!=null)notice.setText("Connecting securely...");
        new Thread(()->{
            try{
                String result=job.run();
                runOnUiThread(()->notice.setText(result));
            }catch(Exception e){
                runOnUiThread(()->notice.setText("Unable to complete: "+e.getMessage()));
            }
        }).start();
    }

    private void refresh(){
        if(notice==null)return;
        String secret=DeviceSession.read(this,"device_secret");
        String code=DeviceSession.read(this,"pending_enrollment");
        if(secret==null && code!=null && isOwner()){
            final String activation=code;
            work(()->{
                String newSecret=DeviceFinanceApi.newSecret();
                DeviceFinanceApi.activate(activation,newSecret);
                DeviceSession.store(this,"device_secret",newSecret);
                DeviceSession.clear(this,"pending_enrollment");
                return loadSnapshot(newSecret);
            });
        } else if(secret!=null){
            final String activeSecret=secret;
            work(()->loadSnapshot(activeSecret));
        } else {
            currentAccount=null;
            bankReady=false;
            notice.setText(isOwner()
                ?"Device Owner is active. Waiting for the administrator's one-time activation code."
                :"Not yet enrolled. The administrator must scan the MBMF QR during Android setup.");
            balanceHero.setText("—");
            dueHero.setText("No payment date assigned");
            planSummary.setText("No agreement assigned to this phone.");
            bankSummary.setText("EFT instructions are available after secure activation.");
            history.setText("No account history available.");
            refreshOwner();
        }
    }

    private String loadSnapshot(String secret) throws Exception{
        try{
            JSONObject snapshot=DeviceFinanceApi.snapshot(secret);
            DeviceSession.store(this,"latest_snapshot",snapshot.toString());
            runOnUiThread(()->display(snapshot,false));
            return "Your finance account is up to date.";
        } catch(Exception error){
            String cached=DeviceSession.read(this,"latest_snapshot");
            if(cached!=null){
                JSONObject snapshot=new JSONObject(cached);
                runOnUiThread(()->display(snapshot,true));
                return "Offline: showing your last saved account information. Payments need internet.";
            }
            throw error;
        }
    }

    private void display(JSONObject response,boolean cached){
        JSONObject account=response.optJSONObject("account");
        if(account==null){
            notice.setText("The backend did not return an assigned finance plan.");
            return;
        }
        currentAccount=account;
        deviceSummary.setText(account.optString("device_model",Build.MODEL));
        String state=account.optString("status","pending");
        long instalment=account.optLong("installment_cents",0);
        long balance=account.optLong("balance_cents",0);
        String due=account.isNull("next_due_date")?"Paid in full":account.optString("next_due_date","Not assigned");
        balanceHero.setText("R "+money(balance));
        dueHero.setText("Next payment  •  "+due);
        planSummary.setText("Customer: "+account.optString("customer_name","")+
            "\nStatus: "+state.toUpperCase(Locale.ROOT)+
            "\nMonthly instalment: R "+money(instalment)+
            "\nOutstanding balance: R "+money(balance)+
            "\nNext payment: "+due+
            (cached?"\nLast saved information (offline)":""));
        if(!cached && "active".equals(state)){
            eftAmount.setText(money(Math.min(instalment,balance)));
            if(eftReference.getText().length()==0){
                eftReference.setText("MBMF-"+account.optString("contract_id","").substring(0,8).toUpperCase(Locale.ROOT));
            }
        }
        JSONObject bank=response.optJSONObject("bank");
        bankReady=bank!=null;
        bankSummary.setText(bankReady
            ?bank.optString("bank_name")+"\nAccount holder: "+bank.optString("account_holder")+
                "\nAccount number: "+bank.optString("account_number")+
                "\nBranch: "+bank.optString("branch_code","")+
                "\n"+bank.optString("instructions","")
            :"The administrator has not yet published verified EFT banking details.");
        JSONArray claims=account.optJSONArray("claims");
        if(claims==null || claims.length()==0){
            history.setText("No EFT claims have been submitted.");
        }else{
            StringBuilder lines=new StringBuilder();
            for(int i=0;i<Math.min(claims.length(),20);i++){
                JSONObject claim=claims.optJSONObject(i);
                if(claim==null)continue;
                if(lines.length()>0)lines.append("\n\n");
                lines.append("R ").append(money(claim.optLong("amount_cents")))
                  .append("  ·  ").append(claim.optString("status","pending").toUpperCase(Locale.ROOT))
                  .append("\nReference: ").append(claim.optString("reference",""));
            }
            history.setText(lines.toString());
        }
        refreshOwner();
    }

    private void showPaymentHelp(){Screen.showTab(mainRoot,1);}

    private void copyReference(){
        String value=eftReference.getText().toString().trim();
        if(value.isEmpty()){notice.setText("No EFT reference to copy.");return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){
            clipboard.setPrimaryClip(ClipData.newPlainText("MBMF EFT reference",value));
            notice.setText("Reference copied. Use this exact reference in your bank transfer.");
        }
    }

    private void submit(){
        String deviceSecret=DeviceSession.read(this,"device_secret");
        if(deviceSecret==null || currentAccount==null || !bankReady){
            notice.setText("Your device must be activated and verified bank details must be available.");
            return;
        }
        if(!"active".equals(currentAccount.optString("status"))){
            notice.setText("No payment is currently due for this finance status.");
            return;
        }
        String reference=eftReference.getText().toString().trim();
        String raw=eftAmount.getText().toString().trim();
        long cents;
        try{
            if(reference.length()<4 || reference.length()>80 || !reference.matches("[a-zA-Z0-9 _./-]+"))
                throw new IllegalArgumentException("Invalid reference");
            cents=new BigDecimal(raw).movePointRight(2).longValueExact();
            if(cents<=0 || cents>100000000L)throw new IllegalArgumentException("Invalid amount");
        }catch(Exception e){
            notice.setText("Enter the correct EFT reference and payment amount.");
            return;
        }
        final long amount=cents;
        work(()->{
            DeviceFinanceApi.claim(deviceSecret,reference,amount);
            String result=loadSnapshot(deviceSecret);
            return "EFT claim received. Awaiting verification in the administrator's banking app.";
        });
    }

    private static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
}
