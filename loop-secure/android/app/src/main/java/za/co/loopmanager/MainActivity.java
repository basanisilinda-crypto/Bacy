package za.co.loopmanager;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native customer experience. Customer identity comes only from the signed enrollment. */
public class MainActivity extends AppCompatActivity {
    private static final int INK=za.co.bacy.ui.CustomerHome.INK,GREEN=za.co.bacy.ui.CustomerHome.BLUE,MUTED=za.co.bacy.ui.CustomerHome.MUTED,PAPER=za.co.bacy.ui.CustomerHome.PAPER,LIME=za.co.bacy.ui.CustomerHome.SOFT;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Store store;
    private Entitlement entitlement;
    private LinearLayout content;
    private TextView daysLabel,timerLabel,connection;
    private boolean destroyed,syncing,enrolling,bankOpened,pendingInbox;
    private int ticks;
    private boolean claiming,claimDialogOpen;
    private AccountTiming.Stage renderedStage;
    private String status="needs_online_check";
    private String accountIssue;
    private final BroadcastReceiver updates=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(!LiveConnectionService.UPDATED.equals(intent.getAction()))return;
            try{entitlement=Entitlement.load(MainActivity.this,store);if(entitlement!=null&&intent.getBooleanExtra("account_verified",false))accountIssue=null;dashboard();}catch(Exception ignored){}
        }
    };
    private final Runnable ticker=new Runnable(){
        @Override public void run(){if(destroyed)return;if(entitlement!=null&&entitlement.stage(MainActivity.this)!=renderedStage)dashboard();else updateCountdown();if(++ticks%60==0)refresh();handler.postDelayed(this,1000);}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);pendingInbox=getIntent().getBooleanExtra("open_messages",false);
        androidx.core.content.ContextCompat.registerReceiver(this,updates,new IntentFilter(LiveConnectionService.UPDATED),getPackageName()+".permission.INTERNAL",null,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        SyncJobService.schedule(this);
        try{
            entitlement=Entitlement.load(this,store);
            if(!store.get("device_token").isEmpty()){if(entitlement==null||!entitlement.data.optBoolean("released"))PolicyController.protectFinancedDevice(this);dashboard();refresh();}
            else if(!store.get("enrollment_token").isEmpty()&&PolicyController.owns(this))connectEnrollment();
            else setupScreen(null,false);
        }catch(Exception failure){setupScreen("Your administrator needs to check this phone's enrollment.",false);}
        handler.post(ticker);
    }
    @Override protected void onResume(){
        super.onResume();if(store==null)return;
        try{if(!store.get("device_token").isEmpty()){LiveConnectionService.start(this);refresh();}}
        catch(Exception ignored){}
        if(bankOpened){bankOpened=false;if(entitlement==null||entitlement.data.optBoolean("released"))return;new MaterialAlertDialogBuilder(this).setTitle("Have you made your payment?").setMessage("Send a payment claim to your administrator. Your paid time changes after they confirm the EFT funds.").setPositiveButton("I have paid",(dialog,which)->claim()).setNegativeButton("Not yet",null).show();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);pendingInbox=intent.getBooleanExtra("open_messages",false);try{if(store.get("device_token").isEmpty()&&!store.get("enrollment_token").isEmpty()&&PolicyController.owns(this)){connectEnrollment();return;}}catch(Exception ignored){}refresh();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);unregisterReceiver(updates);worker.shutdown();super.onDestroy();}

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void screen(){screen("Home");}
    private void screen(String selectedTab){
        daysLabel=null;timerLabel=null;connection=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(20),dp(22),dp(28));scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(entitlement!=null){
            String[] labels={"Home","Payments","Banking","Support"},icons={"home","payment","bank","help"};Runnable[] taps={this::dashboard,this::history,this::openBank,this::support};int selected=0;for(int i=0;i<labels.length;i++)if(labels[i].equals(selectedTab))selected=i;
            View nav=za.co.bacy.ui.SecureNavigation.create(this,labels,icons,selected,index->taps[index].run());nav.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.navigationBars());v.setPadding(dp(14)+bars.left,dp(8),dp(14)+bars.right,dp(10)+bars.bottom);return insets;});page.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        }
        setContentView(page);
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);
        label.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,bold||size>=20));label.setLineSpacing(dp(3),1);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(10);parent.addView(label,params);return label;
    }
    private void gap(int height){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout card(int background){
        MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(22));card.setCardBackgroundColor(background);card.setStrokeWidth(0);card.setStrokeColor(za.co.bacy.ui.CustomerHome.RULE);card.setCardElevation(dp(2));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(23),dp(23),dp(23),dp(17));card.addView(body);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(16);content.addView(card,params);return body;
    }
    private MaterialButton action(LinearLayout parent,String label,boolean primary,Runnable click){
        MaterialButton button=new MaterialButton(this,null,primary?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);button.setAllCaps(false);button.setTextSize(15);button.setCornerRadius(dp(15));button.setMinHeight(dp(54));button.setInsetTop(0);button.setInsetBottom(0);
        if(primary){button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));button.setTextColor(Color.WHITE);}else button.setTextColor(GREEN);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(54));params.topMargin=dp(7);params.bottomMargin=dp(7);parent.addView(button,params);button.setOnClickListener(v->click.run());return button;
    }
    private void detail(LinearLayout parent,String label,String value){text(parent,label,11,MUTED,true);text(parent,value,16,INK,false);}
    private void brand(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(new za.co.bacy.ui.CustomerHome.Logo(this),new LinearLayout.LayoutParams(dp(38),dp(38)));TextView title=new TextView(this);title.setText("Loop Secure");title.setTextSize(23);title.setTextColor(INK);title.setTypeface(za.co.bacy.ui.BrandFonts.heading(this));LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);titleParams.leftMargin=dp(10);row.addView(title,titleParams);content.addView(row);gap(22);
    }
    private void setupScreen(String error,boolean connecting){
        screen();brand();boolean owner=PolicyController.owns(this),linked=false,pending=false;
        try{linked=!store.get("device_token").isEmpty();pending=!store.get("enrollment_token").isEmpty();}catch(Exception ignored){}
        text(content,connecting?"Connecting your account":"Phone setup",27,INK,true);
        text(content,"Your business links this phone to the correct customer. You do not need a customer login.",15,MUTED,false);gap(16);
        LinearLayout statusPanel=card(Color.WHITE);detail(statusPanel,"Device management",owner?"Assigned to this phone":"Business setup needed");gap(12);detail(statusPanel,"Customer record",linked?"Saved on this phone":pending?"Enrollment ready to connect":"Waiting for enrollment");
        if(connecting){com.google.android.material.progressindicator.LinearProgressIndicator progress=new com.google.android.material.progressindicator.LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setIndicatorColor(GREEN);content.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));gap(14);text(content,"Keep Wi-Fi or mobile data connected while we verify the account.",14,MUTED,false);}
        else if(error!=null){text(content,error,14,INK,false);if(linked)action(content,"Refresh account",true,this::refresh);else if(owner&&pending)action(content,"Try connection again",true,this::connectEnrollment);}
        else text(content,"Ask your business to enroll the phone using Android setup QR or the Windows USB setup bundle.",14,MUTED,false);
        gap(10);action(content,"Open banking app",false,this::openBank);action(content,"Wi-Fi connection",false,()->openSettings(true));action(content,"Phone settings",false,()->openSettings(false));action(content,"Emergency call",false,this::emergency);gap(12);text(content,"Setup support",12,MUTED,true);text(content,"basanisilinda@gmail.com",14,GREEN,false);
    }
    private void connectEnrollment(){
        if(enrolling||destroyed)return;
        if(!PolicyController.owns(this)){setupScreen("Android has not assigned device ownership. Use your administrator's Android setup QR or Windows USB enrollment.",false);return;}
        enrolling=true;setupScreen(null,true);
        worker.execute(()->{
            try{
                String enrollmentToken=store.get("enrollment_token"),url=store.get("server_url"),key=store.get("public_key");
                if(enrollmentToken.isEmpty()||url.isEmpty()||key.isEmpty())throw new IllegalStateException("The enrollment configuration is missing. Ask your administrator to check this phone's setup.");
                ManagedPermissions.grant(this);
                String nonce=store.get("activation_nonce");if(nonce.isEmpty()){nonce=UUID.randomUUID().toString();store.put("activation_nonce",nonce);}
                JSONObject body=new JSONObject().put("enrollment_token",enrollmentToken).put("model",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" / Android "+android.os.Build.VERSION.RELEASE).put("consent",true).put("consent_source","administrator_recorded").put("exchange_nonce",nonce).put("identifiers",DeviceIdentifiers.collect(this));
                JSONObject result=new Api(store).post("/api/device/enroll",body,false);
                if(!result.getString("public_key").equals(key))throw new SecurityException("The enrollment signing key did not match. Contact your administrator.");
                store.establishDevice(result.getString("device_id"),result.getString("device_token"));store.put("enrollment_token","");PolicyController.protectFinancedDevice(this);
                runOnUiThread(()->{if(destroyed)return;enrolling=false;LiveConnectionService.start(this);dashboard();refresh();});
            }catch(Exception failure){runOnUiThread(()->{if(destroyed)return;enrolling=false;setupScreen(failure.getMessage(),false);});}
        });
    }
    private void dashboard(){
        if(destroyed)return;
        if(entitlement!=null){try{status=PolicyController.apply(this,entitlement);}catch(Exception failure){status="policy_error";}}
        screen();brand();
        if(entitlement==null){text(content,accountIssue==null?"Connecting your account":"Account not loaded",30,INK,true);text(content,"Your customer account is linked to this installation. No login is needed.",16,MUTED,false);connection=text(content,accountIssue==null?"Checking your account…":accountIssue,15,MUTED,false);action(content,"Refresh connection",true,this::refresh);action(content,"Open banking app",false,this::openBank);action(content,"Wi-Fi connection",false,()->openSettings(true));action(content,"Phone settings",false,()->openSettings(false));action(content,"Emergency call",false,this::emergency);return;}
        JSONObject data=entitlement.data;renderedStage=entitlement.stage(this);boolean released=data.optBoolean("released"),overdue=entitlement.expired(this),pending=hasPendingClaim(),grace=renderedStage==AccountTiming.Stage.GRACE;
        za.co.bacy.ui.CustomerHome.Model model=new za.co.bacy.ui.CustomerHome.Model();
        model.paidOff=released;model.monthlyPayment="R"+data.optLong("amount",300);JSONObject progress=data.optJSONObject("payment_summary");if(progress!=null){model.paidTotal="R"+progress.optLong("approved_total");model.approvedPayments=progress.optInt("approved_count",-1);if(!progress.isNull("contract_total")&&!progress.isNull("progress_percent")){model.totalPrice="R"+progress.optLong("contract_total");model.progressPercent=(float)progress.optDouble("progress_percent",-1);}}model.name=data.optString("customer_name");model.device=android.os.Build.MODEL;model.reference=data.optString("reference");
        model.status=released?"Paid off":status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Restricted":grace?"Grace period":overdue?"Overdue":"Account connected";
        model.due=date(data.optLong("due"));long seconds=entitlement.secondsLeft(this);model.remaining=seconds<0?"—":String.valueOf((seconds+86399)/86400);model.fraction=seconds<0?0:Math.min(1f,seconds/(30f*86400));if(released){model.remaining="✓";model.ringCaption="paid off";model.fraction=1;}else if(grace){model.remaining=String.valueOf(Math.max(0,(entitlement.restrictionDue()-entitlement.serverNow(this)+3599)/3600));model.ringCaption="grace hours";}
        model.foot=accountIssue==null?statusText():accountIssue+" Showing your last verified account.";model.banner=pending?"Your payment claim is awaiting administrator review.":"EFT reference: "+data.optString("reference");
        setContentView(za.co.bacy.ui.CustomerHome.create(this,model,index->{switch(index){case 0 -> new MaterialAlertDialogBuilder(this).setTitle("Your account").setMessage("R300 per 30 days\nDue: "+dateTime(data.optLong("due"))+"\nGrace ends: "+dateTime(entitlement.restrictionDue())+"\nEFT reference: "+data.optString("reference")).setPositiveButton("Close",null).show();case 1 -> history();case 2,5 -> managementStatus();case 3 -> support();case 4 -> showInbox();case 6 -> paymentMethods();case 8 -> emergency();case 9 -> openBank();case 10 -> openSettings(true);case 11 -> openSettings(false);default -> refresh();}}));
        daysLabel=null;timerLabel=null;connection=null;applySystemBars();
    }
    private boolean hasPendingClaim(){if(entitlement==null)return false;JSONArray claims=entitlement.data.optJSONArray("claims");if(claims!=null)for(int i=0;i<claims.length();i++)if(claims.optJSONObject(i).optString("state").equals("pending"))return true;return false;}
    private String date(long epoch){return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(epoch*1000));}
    private String dateTime(long epoch){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(epoch*1000));}
    private String statusText(){return switch(status){case "unsupported"->"Management needs administrator attention.";case "needs_online_check"->"Connect to verify your account after restart.";case "policy_error"->"Your administrator needs to check the phone's policy.";case "restricted_banking_unavailable"->"No banking app is installed. Pay by EFT from another device or contact support.";case "released"->"Fully paid · finance management released.";case "restricted"->"Payment required · banking, Wi-Fi, settings and emergency access available.";default->"Account connected. Tap to refresh.";};}
    private void updateCountdown(){
        if(entitlement==null||daysLabel==null)return;
        if(entitlement.data.optBoolean("released")){daysLabel.setText(R.string.paid_off);timerLabel.setText(status.equals("released")?"Finance management released":"Device release awaiting confirmation");return;}
        if(entitlement.manualLock()){daysLabel.setText("Admin lock");timerLabel.setText(status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Phone restricted · support and payment available":"Check management status for actual enforcement");return;}
        if(entitlement.stage(this)==AccountTiming.Stage.GRACE){
            long graceLeft=Math.max(0,entitlement.restrictionDue()-entitlement.serverNow(this));
            daysLabel.setText("Grace period");timerLabel.setText(String.format(Locale.getDefault(),"%02dh %02dm %02ds remaining · ends %s",graceLeft/3600,(graceLeft%3600)/60,graceLeft%60,dateTime(entitlement.restrictionDue())));return;
        }
        if(entitlement.expired(this)){daysLabel.setText("Payment overdue");timerLabel.setText(status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Phone restricted · EFT payment available":"Check phone management status below");return;}
        long remaining=entitlement.secondsLeft(this);
        if(remaining<0){daysLabel.setText(R.string.sync_needed);timerLabel.setText("Connect to verify your deadline");return;}
        long days=(remaining+86399)/86400;
        daysLabel.setText(days==0?getString(R.string.due_now):getResources().getQuantityString(R.plurals.days_left,(int)Math.min(Integer.MAX_VALUE,days),days));
        timerLabel.setText(String.format(Locale.getDefault(),"%02dh %02dm %02ds · due %s",(remaining%86400)/3600,(remaining%3600)/60,remaining%60,date(entitlement.data.optLong("due"))));
    }
    private void applySystemBars(){
        if(entitlement==null)return;
        ActivityManager activity=getSystemService(ActivityManager.class);WindowInsetsController bars=getWindow().getInsetsController();
        if(bars==null)return;
        if(activity.getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED&&!entitlement.data.optBoolean("released")){
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            JSONObject p=entitlement.data.optJSONObject("policy");
            int hide=0;if(p.optBoolean("block_navigation",true))hide|=WindowInsets.Type.navigationBars();if(p.optBoolean("block_notification_center",true))hide|=WindowInsets.Type.statusBars();
            if(hide!=0)bars.hide(hide);
        }else bars.show(WindowInsets.Type.systemBars());
    }
    private void refresh(){
        if(syncing||enrolling||destroyed)return;
        try{if(store.get("device_token").isEmpty())return;}catch(Exception ignored){return;}
        syncing=true;if(connection!=null)connection.setText(R.string.checking);
        worker.execute(()->{
            Entitlement loaded=null;String issue=null;
            try{ManagedPermissions.grant(this);loaded=Sync.run(this);}catch(Exception failure){issue=AccountConnectionError.describe(failure);android.util.Log.w("BacyAccountSync","Account check failed: "+failure.getClass().getSimpleName());try{loaded=Entitlement.load(this,store);}catch(Exception ignored){}}
            final Entitlement result=loaded;final String failureMessage=issue;
            runOnUiThread(()->{if(destroyed)return;syncing=false;accountIssue=failureMessage;entitlement=result;dashboard();
                if(pendingInbox&&entitlement!=null){pendingInbox=false;showInbox();}
                String applied=status;worker.execute(()->{try{Sync.report(this,result,applied);}catch(Exception ignored){}});
            });
        });
    }
    private void copyReference(){if(entitlement==null)return;getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("EFT reference",entitlement.data.optString("reference")));toast("EFT reference copied");}
    private boolean onlinePaymentBusy;
    private void paymentMethods(){if(entitlement==null||entitlement.data.optBoolean("released"))return;new MaterialAlertDialogBuilder(this).setTitle("Make a payment").setItems(new String[]{"Pay online with iKhokha","Pay by bank EFT","Check online payment"},(dialog,index)->{if(index==0)onlinePayment(false);else if(index==1)paymentDetails();else onlinePayment(true);}).setNegativeButton("Close",null).show();}
    private void onlinePayment(boolean checkOnly){if(onlinePaymentBusy)return;onlinePaymentBusy=true;toast(checkOnly?"Checking payment confirmation…":"Preparing your secure payment link…");worker.execute(()->{try{JSONObject result=new Api(store).post(checkOnly?"/api/device/payments/ikhokha/check":"/api/device/payments/ikhokha",new JSONObject(),true);runOnUiThread(()->{onlinePaymentBusy=false;if(destroyed)return;if(checkOnly){toast(result.optString("state").equals("paid")?"Payment verified. Refreshing your account.":"Payment is not yet confirmed. Keep the phone online.");refresh();return;}String url=result.optString("checkout_url");if(!PaymentActivity.initialUrl(url)){problem("iKhokha did not return a supported checkout address. Bank EFT remains available.");return;}startActivity(new Intent(this,PaymentActivity.class).putExtra("payment_url",url));});}catch(Exception failure){runOnUiThread(()->{onlinePaymentBusy=false;problem("Online payment could not be prepared. "+failure.getMessage()+" Bank EFT remains available.");});}});}
    private void paymentDetails(){
        if(entitlement==null)return;JSONObject data=entitlement.data,bank=data.optJSONObject("eft");
        String details="Amount: R300\nReference: "+data.optString("reference");
        if(bank!=null&&!bank.optString("account_number").isEmpty())details+="\n\n"+bank.optString("bank")+"\n"+bank.optString("account_holder")+"\nAccount: "+bank.optString("account_number")+"\nBranch: "+bank.optString("branch_code");
        else details+="\n\nYour administrator must supply verified EFT destination details before you pay.";
        new MaterialAlertDialogBuilder(this).setTitle("Your EFT payment").setMessage(details).setPositiveButton("Open banking app",(d,w)->openBank()).setNeutralButton("Copy reference",(d,w)->copyReference()).setNegativeButton("Close",null).show();
    }
    private void openBank(){
        JSONObject policy=entitlement==null?null:entitlement.data.optJSONObject("policy");
        java.util.List<BankApps.Choice> banks=BankApps.installed(this,policy);
        if(banks.isEmpty()){problem("No supported banking app with a launch screen is installed. Install the bank's genuine app before handing over the phone, or pay by EFT from another device using your reference.");return;}
        String[] labels=new String[banks.size()];for(int i=0;i<banks.size();i++)labels[i]=banks.get(i).label;
        new MaterialAlertDialogBuilder(this).setTitle("Open banking app").setItems(labels,(d,which)->{
            try{BankApps.open(this,banks.get(which),policy);bankOpened=true;}
            catch(Exception unavailable){problem("The bank could not be opened on this phone. Contact support or pay by EFT from another device. Your account has not been unlocked or marked paid.");}
        }).setNegativeButton("Cancel",null).show();
    }
    private void reviewStatus(){
        new MaterialAlertDialogBuilder(this).setTitle("Payment under review").setMessage("Your claim is already recorded. Your administrator must confirm the EFT funds before extending your paid time. Sending another claim does not speed up approval.").setPositiveButton("Check for approval",(d,w)->refresh()).setNegativeButton("Close",null).show();
    }
    private void openSettings(boolean wifi){try{DeviceSettings.open(this,wifi,entitlement==null?null:entitlement.data.optJSONObject("policy"));}catch(Exception unavailable){problem("The phone could not open its system settings. Contact your administrator.");}}
    private void managementStatus(){
        if(entitlement==null)return;
        try{new MaterialAlertDialogBuilder(this).setTitle("Phone management status").setMessage(DeviceHealth.describe(this,entitlement)).setPositiveButton("Refresh account",(d,w)->refresh()).setNegativeButton("Close",null).show();}
        catch(Exception unavailable){problem("The phone could not read its management status. Contact your administrator.");}
    }
    private void claim(){
        if(entitlement==null||entitlement.data.optBoolean("released"))return;
        if(hasPendingClaim()){reviewStatus();return;}
        if(claiming){toast("Your payment claim is being sent. Please wait.");return;}
        if(claimDialogOpen)return;
        claimDialogOpen=true;
        androidx.appcompat.app.AlertDialog claimDialog=new MaterialAlertDialogBuilder(this).setTitle("Send your payment claim?").setMessage("Confirm your R300 EFT uses reference "+entitlement.data.optString("reference")+". Your administrator will check that the funds arrived before extending your paid time.").setPositiveButton("Send for review",(d,w)->{claiming=true;worker.execute(()->{
            try{String id=store.get("claim_request");if(id.isEmpty()){id=UUID.randomUUID().toString();store.put("claim_request",id);}
                new Api(store).post("/api/device/claims",new JSONObject().put("request_key",id).put("bank_reference",entitlement.data.optString("reference")),true);store.put("claim_request","");
                runOnUiThread(()->{claiming=false;toast("Payment claim sent for review");refresh();});
            }catch(Exception failure){runOnUiThread(()->{claiming=false;problem("The claim has not been confirmed as sent. Reconnect and try again; your paid time has not changed.");});}
        });}).setNegativeButton("Not yet",null).create();
        claimDialog.setOnDismissListener(d->claimDialogOpen=false);claimDialog.show();
    }
    private LinearLayout dialogBody(ScrollView scroll){LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(12),dp(24),dp(20));scroll.addView(body);return body;}
    private void history(){
        if(entitlement==null){problem("Your payment history is still loading. Banking and emergency access remain available.");return;}
        screen("Payments");brand();text(content,"Payments",27,INK,true);JSONObject data=entitlement.data,summary=data.optJSONObject("payment_summary");
        LinearLayout totals=card(Color.WHITE);detail(totals,"Approved payments",summary==null?"Not available yet":"R"+summary.optLong("approved_total")+" across "+summary.optLong("approved_count")+" payments");
        if(!data.optBoolean("released")){action(content,"EFT account and reference",true,this::paymentDetails);action(content,hasPendingClaim()?"Payment awaiting review":"I have made an EFT",false,hasPendingClaim()?this::reviewStatus:this::claim);}else text(content,"Your finance plan has been released as fully paid.",15,INK,false);
        action(content,"Open banking app",false,this::openBank);gap(18);text(content,"Recent payment activity",19,INK,true);JSONArray claims=data.optJSONArray("claims");
        if(claims==null||claims.length()==0)text(content,"Your payment claims will appear here after submission. Only approved payments extend your paid time.",14,MUTED,false);
        else for(int i=0;i<claims.length();i++){JSONObject item=claims.optJSONObject(i);LinearLayout row=card(Color.WHITE);String state=item.optString("state"),label=state.equals("approved")?"Approved":state.equals("pending")?"Awaiting review":state.equals("rejected")?"Not approved":"Recorded";text(row,"R"+item.optInt("amount")+" · "+label,17,INK,true);if(item.optLong("submitted")>0)text(row,date(item.optLong("submitted")),13,MUTED,false);if(!item.optString("reason","").isEmpty())text(row,item.optString("reason"),14,MUTED,false);}
    }
    private void showInbox(){
        if(entitlement==null)return;JSONArray messages;try{String cached=store.get("inbox");messages=cached.isEmpty()?new JSONArray():new JSONArray(cached);}catch(Exception ignored){messages=entitlement.data.optJSONArray("messages");}
        ScrollView scroll=new ScrollView(this);LinearLayout body=dialogBody(scroll);JSONArray ids=new JSONArray();
        if(messages==null||messages.length()==0)text(body,"No messages yet.",15,MUTED,false);
        else for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);ids.put(message.optLong("id"));text(body,message.optString("title"),18,INK,true);text(body,message.optString("body"),15,INK,false);text(body,date(message.optLong("created")),12,MUTED,false);}
        new MaterialAlertDialogBuilder(this).setTitle("Your messages").setView(scroll).setPositiveButton("Mark as read",(d,w)->{if(ids.length()==0)return;worker.execute(()->{try{new Api(store).post("/api/device/messages/receipt",new JSONObject().put("ids",ids).put("state","read"),true);runOnUiThread(this::refresh);}catch(Exception failure){runOnUiThread(()->problem("Read receipt not sent. Reconnect and open your inbox again."));}});}).setNegativeButton("Close",null).show();
    }
    private void support(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(8),dp(24),0);EditText message=new EditText(this);message.setHint("How can we help?");message.setMinLines(3);body.addView(message);
        new MaterialAlertDialogBuilder(this).setTitle("Contact your administrator").setMessage("For payment or phone help. Support: basanisilinda@gmail.com").setView(body).setPositiveButton("Send message",(d,w)->{
            String value=message.getText().toString().trim();if(value.length()<5){problem("Please describe what you need help with.");return;}
            worker.execute(()->{try{new Api(store).post("/api/device/support",new JSONObject().put("message",value),true);runOnUiThread(()->toast("Support message sent"));}catch(Exception failure){runOnUiThread(()->problem("Message not sent. Reconnect and try again. Email support at basanisilinda@gmail.com."));}});
        }).setNegativeButton("Close",null).show();
    }
    private void emergency(){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:112")));}catch(Exception unavailable){problem("Use the phone's system emergency calling option.");}}
    private void toast(String value){if(!destroyed)android.widget.Toast.makeText(this,value,android.widget.Toast.LENGTH_LONG).show();}
    private void problem(String value){if(!destroyed)new MaterialAlertDialogBuilder(this).setTitle("Your phone account").setMessage(value==null?"Contact your administrator for help.":value).setPositiveButton("OK",null).show();}
}
