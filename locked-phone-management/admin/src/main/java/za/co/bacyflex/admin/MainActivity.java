package za.co.bacyflex.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Locale;
import za.co.bacyflex.core.Cloud;
import za.co.bacyflex.core.Screen;

/**
 * MBMF administrator app, backed by real Supabase contracts and EFT claims.
 * Only the administrator signs in; customers are enrolled by an admin-generated QR.
 */
public final class MainActivity extends Activity {
    private String accessToken,lastEnrollmentCode,lastContractId;
    private TextView notice,customersCount,activeCount,pendingCount,settledCount;
    private TextView qrHelp;
    private LinearLayout customersList,devicesList,pendingList;
    private EditText email,password,customerName,customerPhone,deviceModel,price,deposit,monthly,firstDue;
    private EditText bankName,bankHolder,bankAccount,bankBranch,bankInstructions;
    private interface Job { String run() throws Exception; }

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        LinearLayout root=Screen.start(this,"Administrator","Device finance · Secure management");
        notice=Screen.label(this,"Sign in to load your actual business records.",14,false);
        root.addView(notice);

        LinearLayout signIn=Screen.card(this,root,"Administrator sign in");
        email=Screen.field(this,signIn,"Administrator email",false);
        password=Screen.field(this,signIn,"Password",true);
        Screen.button(this,signIn,"Secure sign in",v->signIn());
        signIn.addView(Screen.label(this,
            "Only accounts explicitly assigned as MBMF administrators can view customer records or approve EFT payments.",12,false));

        LinearLayout overview=Screen.card(this,root,"Business overview");
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        overview.addView(top);
        customersCount=Screen.metric(this,top,"Customers");
        activeCount=Screen.metric(this,top,"Active finance");
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        overview.addView(bottom);
        pendingCount=Screen.metric(this,bottom,"EFT to verify");
        settledCount=Screen.metric(this,bottom,"Paid in full");
        Screen.button(this,overview,"Refresh live overview",v->refresh());

        LinearLayout devices=Screen.card(this,root,"Registered financed phones");
        devicesList=new LinearLayout(this);
        devicesList.setOrientation(LinearLayout.VERTICAL);
        devices.addView(devicesList);
        devicesList.addView(Screen.label(this,"Sign in to load devices.",14,false));

        LinearLayout customers=Screen.card(this,root,"Customers");
        customersList=new LinearLayout(this);
        customersList.setOrientation(LinearLayout.VERTICAL);
        customers.addView(customersList);
        customersList.addView(Screen.label(this,"Sign in to see registered customers.",14,false));
        Screen.button(this,customers,"Refresh customers",v->refresh());

        LinearLayout finance=Screen.card(this,root,"Register a customer and phone");
        finance.addView(Screen.label(this,
            "You enter the customer's information. Customers do not create accounts or passwords.",13,false));
        customerName=Screen.field(this,finance,"Customer full name",false);
        customerPhone=Screen.field(this,finance,"Customer cellphone number",false);
        deviceModel=Screen.field(this,finance,"Phone make and model (e.g. Samsung A06)",false);
        price=Screen.field(this,finance,"Total phone price in rand (e.g. 1700.00)",false);
        deposit=Screen.field(this,finance,"Deposit already paid in rand (e.g. 0.00)",false);
        monthly=Screen.field(this,finance,"Monthly instalment in rand (e.g. 300.00)",false);
        firstDue=Screen.field(this,finance,"First due date (YYYY-MM-DD)",false);
        Screen.button(this,finance,"Register finance contract",v->registerContract());
        finance.addView(Screen.label(this,
            "A one-use setup token is created by the server and expires after 24 hours. " +
            "The phone is activated only after Android's Device Owner consent flow.",12,false));

        LinearLayout enroll=Screen.card(this,root,"Android startup QR");
        qrHelp=Screen.label(this,
            "First register the customer's finance contract. Then show the personalised QR on your admin phone and scan it with the new phone during Android setup.",14,false);
        enroll.addView(qrHelp);
        Screen.button(this,enroll,"Show customer's Android setup QR",v->showQr());
        Screen.button(this,enroll,"Copy one-time activation code",v->copyCode());
        enroll.addView(Screen.label(this,
            "Test phone only for now. Back up before any reset. Device Owner controls require informed consent. The APK remains a debug-signed pilot.",12,false));

        LinearLayout payments=Screen.card(this,root,"EFT payments awaiting bank verification");
        payments.addView(Screen.label(this,
            "Check the exact EFT amount and reference in your bank account before approving. A screenshot is not proof of payment.",13,false));
        Screen.button(this,payments,"Refresh pending EFT claims",v->refresh());
        pendingList=new LinearLayout(this);
        pendingList.setOrientation(LinearLayout.VERTICAL);
        payments.addView(pendingList);
        pendingList.addView(Screen.label(this,"No payment claims loaded.",14,false));

        LinearLayout settings=Screen.card(this,root,"Customer EFT bank details");
        settings.addView(Screen.label(this,
            "Customers see these details only after secure device activation. Confirm the account details with your bank first.",13,false));
        bankName=Screen.field(this,settings,"Bank name",false);
        bankHolder=Screen.field(this,settings,"Bank account holder",false);
        bankAccount=Screen.field(this,settings,"EFT account number",false);
        bankBranch=Screen.field(this,settings,"Branch code (optional)",false);
        bankInstructions=Screen.field(this,settings,"Payment instructions",false);
        Screen.button(this,settings,"Publish verified EFT details",v->publishBank());

        Screen.tabs(this,root,new String[]{"Home","Clients","Finance","Payments","Setup"},
            new LinearLayout[][]{
                {signIn,overview,devices},
                {customers},
                {finance},
                {payments},
                {enroll,settings}
            });
    }

    @Override protected void onResume(){
        super.onResume();
        if(notice!=null && accessToken!=null)refresh();
    }

    private void work(Job job){
        notice.setText("Connecting to the secure server...");
        new Thread(()->{
            try{
                String result=job.run();
                runOnUiThread(()->notice.setText(result));
            }catch(Exception error){
                runOnUiThread(()->notice.setText("Unable to complete: "+error.getMessage()));
            }
        }).start();
    }

    private void signIn(){
        String user=email.getText().toString().trim();
        String pass=password.getText().toString();
        if(user.isEmpty()||pass.isEmpty()){notice.setText("Enter your admin email and password.");return;}
        work(()->{
            JSONObject result=Cloud.signIn(user,pass);
            String temporary=result.getString("access_token");
            String id=result.getJSONObject("user").getString("id");
            JSONArray role=Cloud.rows("finance_admins?select=user_id&user_id=eq."+id,temporary);
            if(role.length()==0)throw new SecurityException("This account does not have administrator access.");
            accessToken=temporary;
            loadDashboard();
            return "Administrator connected. The numbers below come from Supabase.";
        });
    }

    private void refresh(){
        if(accessToken==null){notice.setText("Administrator sign-in required.");return;}
        work(()->{loadDashboard();return "Customer, device, and payment records refreshed from Supabase.";});
    }

    private void loadDashboard() throws Exception{
        JSONArray contracts=Cloud.rows(
            "finance_contracts?select=id,customer_name,customer_phone,device_model,installment_cents,balance_cents,next_due_date,status,enrolled_at&order=created_at.desc&limit=250",accessToken);
        JSONArray claims=Cloud.rows(
            "finance_contract_claims?select=id,contract_id,eft_reference,amount_cents,submitted_at&status=eq.pending&order=submitted_at.asc&limit=150",accessToken);
        int active=0,settled=0;
        for(int i=0;i<contracts.length();i++){
            JSONObject c=contracts.optJSONObject(i);
            if(c!=null&&"active".equals(c.optString("status")))active++;
            if(c!=null&&"settled".equals(c.optString("status")))settled++;
        }
        final int total=contracts.length(),a=active,s=settled,p=claims.length();
        runOnUiThread(()->{
            customersCount.setText(String.valueOf(total));
            activeCount.setText(String.valueOf(a));
            settledCount.setText(String.valueOf(s));
            pendingCount.setText(String.valueOf(p));
            customersList.removeAllViews();
            devicesList.removeAllViews();
            pendingList.removeAllViews();
            if(total==0){
                customersList.addView(Screen.label(this,"No financed customers registered yet.",14,false));
                devicesList.addView(Screen.label(this,"No financed phones registered yet.",14,false));
            }
            for(int i=0;i<contracts.length();i++){
                JSONObject c=contracts.optJSONObject(i);
                if(c==null)continue;
                String due=c.isNull("next_due_date")?"Paid in full":c.optString("next_due_date");
                String name=c.optString("customer_name");
                String phone=c.optString("customer_phone");
                String model=c.optString("device_model");
                String state=c.optString("status","pending");
                customersList.addView(Screen.label(this,
                    name+"  ·  "+phone+"\\n"+model+"  ·  Due: "+due+"\\nBalance: R "+money(c.optLong("balance_cents")),14,false));
                devicesList.addView(Screen.label(this,
                    model+"  ·  "+(c.isNull("enrolled_at")?"Awaiting setup":"App activated")+
                    "\\nFinance: "+state.toUpperCase(Locale.ROOT),14,false));
            }
            if(p==0)pendingList.addView(Screen.label(this,"No pending EFT claims.",14,false));
            for(int i=0;i<claims.length();i++){
                JSONObject claim=claims.optJSONObject(i);
                if(claim==null)continue;
                final String id=claim.optString("id");
                final String reference=claim.optString("eft_reference");
                final String amount=money(claim.optLong("amount_cents"));
                pendingList.addView(Screen.label(this,
                    "Reference: "+reference+"\\nAmount claimed: R "+amount+
                    "\\nSubmitted: "+claim.optString("submitted_at"),14,false));
                Screen.button(this,pendingList,"Approve bank-confirmed EFT",v->decide(id,reference,amount,true));
                Screen.button(this,pendingList,"Reject this claim",v->decide(id,reference,amount,false));
            }
        });
    }

    private static long cents(String raw) throws Exception{
        return new BigDecimal(raw.trim()).movePointRight(2).longValueExact();
    }

    private void registerContract(){
        if(accessToken==null){notice.setText("Administrator sign-in required.");return;}
        String name=customerName.getText().toString().trim();
        String phone=customerPhone.getText().toString().trim();
        String model=deviceModel.getText().toString().trim();
        String date=firstDue.getText().toString().trim();
        String totalRaw=price.getText().toString(),depositRaw=deposit.getText().toString(),
            monthlyRaw=monthly.getText().toString();
        work(()->{
            long total=cents(totalRaw),paid=cents(depositRaw),instalment=cents(monthlyRaw);
            if(total<=0||paid<0||paid>=total||instalment<=0||!date.matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new IllegalArgumentException("Enter a valid price, deposit, instalment, and date.");
            JSONObject request=new JSONObject()
                .put("p_customer_name",name).put("p_customer_phone",phone)
                .put("p_device_model",model).put("p_price_cents",total)
                .put("p_deposit_cents",paid).put("p_installment_cents",instalment)
                .put("p_first_due_date",date);
            JSONObject contract=Cloud.rpc("finance_register_contract",request,accessToken);
            lastContractId=contract.getString("contract_id");
            lastEnrollmentCode=contract.getString("enrollment_code");
            runOnUiThread(()->{
                qrHelp.setText("Registered: "+name+" · "+model+
                    "\\nContract: "+lastContractId+
                    "\\nCode expires: "+contract.optString("expires_at")+
                    "\\nShow the QR on your admin phone and scan it from the customer's Android Welcome screen.");
            });
            loadDashboard();
            runOnUiThread(this::showQr);
            return "Finance contract registered. One-time device QR is ready.";
        });
    }

    private void showQr(){
        if(lastEnrollmentCode==null){
            notice.setText("Register a customer to generate their personal setup QR.");
            return;
        }
        final String code=lastEnrollmentCode;
        work(()->{
            Bitmap bitmap=AdminEnrollmentQr.create(code);
            runOnUiThread(()->{
                ImageView view=new ImageView(this);
                view.setImageBitmap(bitmap);
                view.setAdjustViewBounds(true);
                int width=Math.min(getResources().getDisplayMetrics().widthPixels-60,810);
                LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(18,12,18,4);
                panel.addView(view,new LinearLayout.LayoutParams(width,width));
                panel.addView(Screen.label(this,
                    "On the new Android phone's Welcome screen, open QR setup and scan this. " +
                    "Accept Android's management consent to assign it to the registered customer.",12,false));
                new AlertDialog.Builder(this).setTitle("MBMF personal setup QR")
                    .setView(panel).setPositiveButton("Close",null).show();
            });
            return "Individual Android setup QR generated from the published APK and checksum.";
        });
    }

    private void copyCode(){
        if(lastEnrollmentCode==null){notice.setText("Register a customer first.");return;}
        ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(c!=null){
            c.setPrimaryClip(ClipData.newPlainText("One-time MBMF activation",lastEnrollmentCode));
            notice.setText("One-time code copied. Do not share it publicly.");
        }
    }

    private void decide(String id,String reference,String rand,boolean approve){
        if(accessToken==null)return;
        String message=approve
            ?"Have you confirmed R "+rand+" with reference "+reference+" in your own bank account?"
            :"Reject this EFT claim for R "+rand+" with reference "+reference+"?";
        new AlertDialog.Builder(this).setTitle(approve?"Confirm actual bank receipt":"Reject EFT")
            .setMessage(message).setNegativeButton("Cancel",null)
            .setPositiveButton(approve?"Approve received EFT":"Reject",(d,w)->work(()->{
                Cloud.rpc("finance_decide_contract_claim",
                    new JSONObject().put("p_claim_id",id).put("p_approve",approve),accessToken);
                loadDashboard();
                return approve?"EFT approved in the live server.":"Claim rejected.";
            })).show();
    }

    private void publishBank(){
        if(accessToken==null){notice.setText("Administrator sign-in required.");return;}
        String bank=bankName.getText().toString().trim();
        String holder=bankHolder.getText().toString().trim();
        String number=bankAccount.getText().toString().trim();
        String branch=bankBranch.getText().toString().trim();
        String instructions=bankInstructions.getText().toString().trim();
        new AlertDialog.Builder(this).setTitle("Publish account to customers?")
            .setMessage("Only proceed after independently checking the beneficiary details in your bank.")
            .setNegativeButton("Cancel",null).setPositiveButton("Publish",(d,w)->work(()->{
                JSONObject data=new JSONObject().put("p_bank",bank).put("p_holder",holder)
                    .put("p_account",number).put("p_branch",branch)
                    .put("p_instructions",instructions).put("p_active",true);
                Cloud.rpc("finance_set_payment_details",data,accessToken);
                return "Customer EFT beneficiary details updated on Supabase.";
            })).show();
    }

    private static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
}
