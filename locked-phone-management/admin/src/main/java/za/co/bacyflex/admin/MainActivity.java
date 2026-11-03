package za.co.bacyflex.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import za.co.bacyflex.core.Cloud;
import za.co.bacyflex.core.Screen;

public final class MainActivity extends Activity {
    private String token,userId;
    private EditText email,password,customerId,deviceLabel,installment,balance,dueDate;
    private EditText bankName,bankHolder,bankAccount,bankBranch,bankInstructions;
    private TextView status;
    private LinearLayout pending;
    private interface Job { String call() throws Exception; }

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        LinearLayout root=Screen.start(this,"Locked Phone","Secure administration  |  By Basani Selinda");

        LinearLayout login=Screen.card(this,root,"Administrator sign in");
        email=Screen.field(this,login,"Email address",false);
        password=Screen.field(this,login,"Password",true);
        Screen.button(this,login,"Sign in",v->signIn());
        status=Screen.label(this,"Administrator access is assigned securely by the backend.",14,false);
        login.addView(status);

        LinearLayout claims=Screen.card(this,root,"EFT approvals");
        Screen.button(this,claims,"Refresh pending EFT claims",v->refreshClaims());
        pending=new LinearLayout(this);pending.setOrientation(LinearLayout.VERTICAL);claims.addView(pending);
        pending.addView(Screen.label(this,"Sign in to load pending payments.",14,false));

        LinearLayout plans=Screen.card(this,root,"Register a financed phone");
        customerId=Screen.field(this,plans,"Customer account UUID",false);
        deviceLabel=Screen.field(this,plans,"Phone model / device label",false);
        installment=Screen.field(this,plans,"Monthly instalment (rand)",false);
        balance=Screen.field(this,plans,"Total remaining balance (rand)",false);
        dueDate=Screen.field(this,plans,"First due date: YYYY-MM-DD",false);
        Screen.button(this,plans,"Create finance plan",v->createPlan());

        LinearLayout bank=Screen.card(this,root,"EFT beneficiary configuration");
        bank.addView(Screen.label(this,"Only save account details after confirming them with your own bank. Publishing makes them visible to signed-in customers.",13,false));
        bankName=Screen.field(this,bank,"Bank name",false);
        bankHolder=Screen.field(this,bank,"Account holder",false);
        bankAccount=Screen.field(this,bank,"Account number",false);
        bankBranch=Screen.field(this,bank,"Branch code",false);
        bankInstructions=Screen.field(this,bank,"Payment instructions (optional)",false);
        Screen.button(this,bank,"Publish verified EFT details",v->saveBank());
    }

    private void work(Job job) {
        status.setText("Connecting securely...");
        new Thread(()->{
            try {String message=job.call();runOnUiThread(()->status.setText(message));}
            catch(Exception e) {runOnUiThread(()->status.setText("Unable to complete: "+e.getMessage()));}
        }).start();
    }

    private void signIn() {
        final String e=email.getText().toString().trim(),p=password.getText().toString();
        work(()->{
            JSONObject result=Cloud.signIn(e,p);
            String temporaryToken=result.getString("access_token");
            String temporaryUser=result.getJSONObject("user").getString("id");
            JSONArray assigned=Cloud.rows("finance_admins?select=user_id&user_id=eq."+temporaryUser,temporaryToken);
            if(assigned.length()==0) throw new SecurityException("Account has not been assigned an administrator role.");
            token=temporaryToken;userId=temporaryUser;
            loadClaims();
            return "Administrator authentication successful.";
        });
    }

    private void refreshClaims() {
        if(token==null) {status.setText("Administrator sign-in required.");return;}
        work(()->{loadClaims();return "Pending claims synchronised.";});
    }

    private void loadClaims() throws Exception {
        JSONArray claims=Cloud.rows("finance_payment_claims?select=id,eft_reference,amount_cents,submitted_at&status=eq.pending&order=submitted_at.asc&limit=100",token);
        runOnUiThread(()->{
            pending.removeAllViews();
            if(claims.length()==0) {
                pending.addView(Screen.label(this,"No pending EFT claims.",14,false));return;
            }
            for(int i=0;i<claims.length();i++) {
                JSONObject claim=claims.optJSONObject(i);
                if(claim==null) continue;
                final String claimId=claim.optString("id");
                final String claimRef=claim.optString("eft_reference");
                final String claimAmount=BigDecimal.valueOf(claim.optLong("amount_cents"),2).toPlainString();
                String label="Reference: "+claim.optString("eft_reference")+"\nAmount claimed: R"+
                    BigDecimal.valueOf(claim.optLong("amount_cents"),2).toPlainString()+
                    "\nSubmitted: "+claim.optString("submitted_at");
                pending.addView(Screen.label(this,label,14,false));
                Screen.button(this,pending,"Approve only after checking bank",v->confirm(claimId,claimRef,claimAmount,true));
                Screen.button(this,pending,"Reject claim",v->confirm(claimId,claimRef,claimAmount,false));
            }
        });
    }

    private void confirm(String id,String eftRef,String amountRand,boolean approve) {
        if(token==null)return;
        String prompt=approve
            ?"Have you confirmed a received EFT of R "+amountRand+" with the exact reference '"+eftRef+"' inside your bank account? Do not rely on screenshots."
            :"Reject EFT claim for R "+amountRand+" with reference '"+eftRef+"'?";
        new AlertDialog.Builder(this).setTitle(approve?"Verify real bank receipt":"Reject EFT claim")
            .setMessage(prompt).setNegativeButton("Cancel",null)
            .setPositiveButton(approve?"Confirmed: approve":"Reject",(dialog,which)->{
                work(()->{
                    Cloud.rpc("finance_decide_payment",new JSONObject().put("p_claim_id",id).put("p_approve",approve),token);
                    loadClaims();
                    return approve?"Payment approved on live server.":"Payment claim rejected.";
                });
            }).show();
    }

    private void createPlan() {
        if(token==null){status.setText("Administrator sign-in required.");return;}
        String c=customerId.getText().toString().trim(),label=deviceLabel.getText().toString().trim();
        String rawInstallment=installment.getText().toString(),rawBalance=balance.getText().toString();
        String due=dueDate.getText().toString().trim();
        work(()->{
            long i=new BigDecimal(rawInstallment).movePointRight(2).longValueExact();
            long b=new BigDecimal(rawBalance).movePointRight(2).longValueExact();
            JSONObject payload=new JSONObject().put("p_customer_id",c).put("p_device_label",label)
                .put("p_installment_cents",i).put("p_balance_cents",b).put("p_first_due_date",due);
            Cloud.submit("rpc/finance_create_plan",payload,token);
            return "Finance plan created in the database. Device enrolment must still be performed with consent.";
        });
    }

    private void saveBank() {
        if(token==null){status.setText("Administrator sign-in required.");return;}
        String n=bankName.getText().toString(),h=bankHolder.getText().toString(),a=bankAccount.getText().toString(),
            b=bankBranch.getText().toString(),ins=bankInstructions.getText().toString();
        new AlertDialog.Builder(this).setTitle("Publish bank account to customers?")
            .setMessage("Confirm these details came directly from your bank. An incorrect account number can misdirect real payments.")
            .setNegativeButton("Cancel",null).setPositiveButton("Publish",(dialog,which)->{
                work(()->{
                    JSONObject payload=new JSONObject().put("p_bank",n).put("p_holder",h)
                        .put("p_account",a).put("p_branch",b).put("p_instructions",ins).put("p_active",true);
                    Cloud.submit("rpc/finance_set_payment_details",payload,token);
                    return "Verified EFT instructions saved on the live server.";
                });
            }).show();
    }
}

