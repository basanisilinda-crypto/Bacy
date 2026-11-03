package za.co.mdmtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * BACY FLEX — visual-first, offline finance-account demo.
 * No payment gateway, network, remote commands or automated restrictions are
 * implemented in this version. A separate manual Device Admin test is available.
 */
public class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 37, 32);
    private static final int FOREST = Color.rgb(17, 69, 53);
    private static final int CREAM = Color.rgb(248, 249, 246);
    private static final int MUTED = Color.rgb(108, 120, 112);
    private static final int PALE = Color.rgb(229, 240, 233);
    private static final int LINE = Color.rgb(224, 229, 222);
    private static final int LIME = Color.rgb(211, 234, 157);
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK);
    private static final int PRICE = 1700;
    private static final int MONTHLY = 300;
    private SharedPreferences store;
    private DevicePolicyManager policy;
    private ComponentName admin;
    private int page = 0;
    private LocalDate chosenDate;
    private LinearLayout content;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(CREAM);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = getSharedPreferences("bacy_flex_v1", MODE_PRIVATE);
        policy = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        chosenDate = activationDate();
        showPage();
    }
    private LocalDate today() { return LocalDate.now(ZONE); }
    private LocalDate activationDate() {
        try { return LocalDate.parse(store.getString("date", today().toString())); }
        catch (Exception e) { return today(); }
    }
    private boolean registered() { return store.getBoolean("registered", false); }
    private String name() { return store.getString("name", "Guest customer"); }
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n + .5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable outlined(int color, int stroke, int radius) {
        GradientDrawable d = bg(color, radius);
        d.setStroke(dp(1), stroke); return d;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }
    private void gap(LinearLayout parent, int height) {
        View v = new View(this); parent.addView(v, lp(1, height));
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }
    private TextView kicker(String text, int color) {
        TextView t = label(text.toUpperCase(Locale.UK), 10, color, true);
        t.setLetterSpacing(.17f); return t;
    }
    private void add(LinearLayout parent, View view) { parent.addView(view, lp(-1, -2)); }
    private void title(LinearLayout parent, String heading, String sub) {
        add(parent, label(heading, 27, INK, true));
        gap(parent, 7); add(parent, label(sub, 13, MUTED, false)); gap(parent, 22);
    }
    private LinearLayout card(int color, int pad, int radius) {
        LinearLayout box = vertical(); box.setBackground(bg(color, radius));
        box.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        return box;
    }
    private void section(LinearLayout parent, String title) {
        gap(parent, 25); add(parent, label(title, 17, INK, true)); gap(parent, 13);
    }
    private void button(LinearLayout parent, String text, boolean primary, Runnable click) {
        TextView b = label(text, 15, primary ? Color.WHITE : FOREST, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(primary ? bg(FOREST, 16) : outlined(Color.WHITE, LINE, 16));
        b.setMinHeight(dp(53)); b.setPadding(dp(14), dp(15), dp(14), dp(15));
        add(parent, b);
        b.setOnClickListener(v -> click.run());
    }
    private void showPage() {
        LinearLayout root = vertical(); root.setBackgroundColor(CREAM);
        LinearLayout top = horizontal();
        top.setPadding(dp(23),dp(17),dp(23),dp(16));
        TextView logo = label("b", 27, LIME, true);
        logo.setGravity(Gravity.CENTER); logo.setBackground(bg(FOREST,12));
        top.addView(logo, lp(42,42));
        LinearLayout brand = vertical(); brand.setPadding(dp(11),0,0,0);
        add(brand, kicker("BACY", FOREST));
        add(brand, label("FLEX", 18, INK, true));
        top.addView(brand, lp(-2,-2));
        View space = new View(this);
        top.addView(space, new LinearLayout.LayoutParams(0,1,1));
        TextView dot = label(registered() ? "ACCOUNT" : "DEMO", 10, FOREST, true);
        dot.setLetterSpacing(.08f); dot.setBackground(bg(PALE,20));
        dot.setPadding(dp(12),dp(8),dp(12),dp(8));
        top.addView(dot);
        root.addView(top,lp(-1,-2));
        View hair = new View(this); hair.setBackgroundColor(LINE); root.addView(hair,lp(-1,1));
        ScrollView sc = new ScrollView(this); sc.setFillViewport(false);
        sc.setVerticalScrollBarEnabled(false);
        content = vertical();
        content.setPadding(dp(22),dp(24),dp(22),dp(40));
        sc.addView(content); root.addView(sc, new LinearLayout.LayoutParams(-1,0,1));
        if (page == 0) home();
        else if (page == 1) payments();
        else if (page == 2) support();
        else device();
        root.addView(bottom(), lp(-1,76));
        setContentView(root);
    }
    private View bottom() {
        LinearLayout bar = horizontal();
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(12),dp(6),dp(12),dp(9));
        String[] tabs = {"Home","Payments","Support","Device"};
        for (int i=0;i<tabs.length;i++) {
            final int index = i;
            LinearLayout slot = vertical(); slot.setGravity(Gravity.CENTER);
            TextView marker = label("━━", 11, i==page ? FOREST : Color.TRANSPARENT,true);
            marker.setGravity(Gravity.CENTER); add(slot,marker);
            gap(slot,7);
            TextView tabText = label(tabs[i],12,i==page?INK:MUTED,i==page);
            tabText.setGravity(Gravity.CENTER); add(slot,tabText);
            bar.addView(slot,new LinearLayout.LayoutParams(0,-1,1));
            slot.setOnClickListener(v -> {page=index;showPage();});
        }
        return bar;
    }
    private String money(int value) { return "R " + String.format(Locale.UK,"%,d",value) + ".00"; }
    private LocalDate due(int n) { return activationDate().plusMonths(n); }
    private String countdown() {
        long days = ChronoUnit.DAYS.between(today(),due(1));
        if (days > 1) return "Due in " + days + " days";
        if (days == 1) return "Due tomorrow";
        if (days == 0) return "Due today";
        return Math.abs(days) + " days past due";
    }
    private void row(LinearLayout parent,String a,String b) {
        LinearLayout r=horizontal();
        TextView l=label(a,13,MUTED,false);
        r.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(label(b,13,INK,true),lp(-2,-2));
        add(parent,r);
    }
    private void home() {
        add(content,kicker("YOUR FINANCE ACCOUNT",FOREST)); gap(content,8);
        add(content,label(registered() ? "Hello, " + firstName(name()) : "Welcome to BACY FLEX",27,INK,true));
        gap(content,9);
        add(content,label("Payments made clear. Your account in one place.",13,MUTED,false)); gap(content,24);
        LinearLayout hero=card(FOREST,24,25);
        add(hero,kicker("NEXT PAYMENT",LIME)); gap(hero,13);
        add(hero,label(money(MONTHLY),38,Color.WHITE,true)); gap(hero,4);
        add(hero,label("Monthly instalment",13,Color.rgb(195,219,206),false)); gap(hero,22);
        View line=new View(this);line.setBackgroundColor(Color.rgb(75,121,101));add(hero,line);gap(hero,18);
        rowLight(hero,"PAYMENT DATE",due(1).format(DATE));
        gap(hero,11);
        rowLight(hero,"STATUS",countdown());
        gap(hero,21);
        TextView chip=label(registered() ? "SCHEDULE SAVED ON DEVICE" : "SAMPLE FINANCE PLAN",10,FOREST,true);
        chip.setLetterSpacing(.1f);chip.setBackground(bg(LIME,20));
        chip.setPadding(dp(12),dp(9),dp(12),dp(9));
        hero.addView(chip,lp(-2,-2));
        add(content,hero);
        section(content,"Quick actions");
        LinearLayout box=card(Color.WHITE,16,22);box.setBackground(outlined(Color.WHITE,LINE,22));
        button(box,"View EFT payment details",true,()->eft());
        gap(box,10);button(box,"See payment schedule",false,()->{page=1;showPage();});
        add(content,box);
        section(content,"Your agreement");
        LinearLayout agreement=card(Color.WHITE,19,21);
        agreement.setBackground(outlined(Color.WHITE,LINE,21));
        row(agreement,"Device price",money(PRICE));gap(agreement,13);
        row(agreement,"Monthly payment",money(MONTHLY));gap(agreement,13);
        row(agreement,"Instalments","5 × R300 + 1 × R200");
        gap(agreement,12);
        TextView note=label("Illustrative plan only. Interest, fees and signed contract terms are not configured.",11,MUTED,false);
        add(agreement,note); add(content,agreement);
        if(!registered()){
            gap(content,18);
            button(content,"Register a test customer",false,()->openRegistration());
        }
        gap(content,14);
        add(content,label("Payment confirmation and remote unlocking require a connected, verified service.",11,MUTED,false));
    }
    private void rowLight(LinearLayout box,String left,String right) {
        LinearLayout r=horizontal();
        TextView a=kicker(left,Color.rgb(194,218,203));
        r.addView(a,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(label(right,13,Color.WHITE,true)); add(box,r);
    }
    private String firstName(String n) {
        n=n.trim();int x=n.indexOf(' ');
        return x>0?n.substring(0,x):n;
    }
    private void payments() {
        title(content,"Your payments","A clear view of what is due and when.");
        LinearLayout summary=card(PALE,19,20);
        add(summary,kicker("NEXT DUE",FOREST));gap(summary,8);
        add(summary,label(money(MONTHLY),29,INK,true));gap(summary,8);
        add(summary,label(due(1).format(DATE)+"  ·  "+countdown(),13,FOREST,true));
        add(content,summary);
        section(content,"Payment schedule");
        for(int i=1;i<=6;i++){
            int amount=Math.min(MONTHLY,PRICE-(i-1)*MONTHLY);
            LinearLayout c=horizontal();c.setBackground(outlined(Color.WHITE,LINE,18));
            c.setPadding(dp(17),dp(16),dp(17),dp(16));
            TextView num=label(String.format(Locale.UK,"%02d",i),15,FOREST,true);
            num.setGravity(Gravity.CENTER);num.setBackground(bg(PALE,12));
            c.addView(num,lp(43,43));
            LinearLayout info=vertical();info.setPadding(dp(13),0,0,0);
            add(info,label("Instalment "+i,14,INK,true));
            gap(info,5);add(info,label(due(i).format(DATE),12,MUTED,false));
            c.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            c.addView(label(money(amount),14,INK,true));
            add(content,c);gap(content,10);
        }
        gap(content,9);
        button(content,"EFT payment instructions",true,()->eft());
        gap(content,17);
        add(content,label("This schedule is calculated offline from the saved activation date. It does not confirm or record successful payments.",12,MUTED,false));
    }
    private void eft() {
        new AlertDialog.Builder(this)
          .setTitle("EFT payments")
          .setMessage("Bank account details and a secure payment-verification service have not been configured. Do not transfer money using any unverified details. Payment and automatic unlocking are not active in this test version.")
          .setPositiveButton("Understood",null).show();
    }
    private void support() {
        title(content,"Help & support","Support should be easy to find and understand.");
        LinearLayout c=card(Color.WHITE,21,22);c.setBackground(outlined(Color.WHITE,LINE,22));
        add(c,kicker("CONTACT SUPPORT",FOREST));gap(c,11);
        add(c,label("We’re here to help",22,INK,true));gap(c,9);
        add(c,label("Your support email and phone number have not been configured yet. They will be added securely before release.",13,MUTED,false));
        add(content,c);
        section(content,"Common questions");
        info("How do I make a payment?","Once EFT banking details are verified, they will appear on the Payments page.");
        gap(content,10);
        info("When will my payment show?","Your payment must be confirmed by the payment service. Sending a proof of payment does not automatically verify a transaction.");
        gap(content,10);
        info("What happens if I am overdue?","Your signed agreement will explain reminders, grace periods, dispute options and any supported restrictions.");
        section(content,"Safety and emergency access");
        LinearLayout s=card(PALE,20,20);
        add(s,label("Emergency calling stays available",16,INK,true));gap(s,8);
        add(s,label("Account servicing must never block access to emergency calling.",12,MUTED,false));gap(s,15);
        button(s,"Open emergency dialler",false,()->{
            Intent intent=new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:112"));
            try {startActivity(intent);}catch(Exception e){popup("Dialler unavailable","Open your phone app and call emergency services.");}
        });
        add(content,s);
    }
    private void info(String question,String answer){
        LinearLayout c=card(Color.WHITE,18,18);c.setBackground(outlined(Color.WHITE,LINE,18));
        add(c,label(question,14,INK,true));gap(c,7);add(c,label(answer,12,MUTED,false));add(content,c);
    }
    private String id() {
        String v=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
        if(v==null||v.length()<5)return "Not available";
        return "•••• " + v.substring(v.length()-5);
    }
    private void device(){
        title(content,"Your device","Device and management status.");
        LinearLayout c=card(FOREST,22,24);
        add(c,kicker("REGISTERED DEVICE",LIME));gap(c,10);
        add(c,label(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL,22,Color.WHITE,true));gap(c,10);
        add(c,label("Device ID  "+id(),13,Color.rgb(206,230,213),false));
        add(content,c);
        section(content,"Management status");
        LinearLayout status=card(Color.WHITE,19,21);status.setBackground(outlined(Color.WHITE,LINE,21));
        row(status,"Screen-lock permission",policy.isAdminActive(admin)?"Enabled":"Not enabled");gap(status,15);
        row(status,"Device Owner",policy.isDeviceOwnerApp(getPackageName())?"Active":"Not enrolled");gap(status,15);
        row(status,"Remote commands","Not connected");gap(status,15);
        row(status,"Online payments","Not connected");
        add(content,status);
        section(content,"Set up a test account");
        button(content,registered()?"Edit local customer details":"Register a test customer",true,()->openRegistration());
        gap(content,11);
        button(content,"Activate screen-lock test",false,()->{
            Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Allows only the manual screen-lock test in this version.");
            startActivity(i);
        });
        gap(content,11);
        button(content,"Test screen lock",false,()->{
            if(!policy.isAdminActive(admin)){
                popup("Permission needed","First activate the screen-lock test permission.");return;
            }
            new AlertDialog.Builder(this).setTitle("Lock this screen?")
             .setMessage("This is a manual local test. Unlock normally with your PIN or fingerprint.")
             .setNegativeButton("Cancel",null)
             .setPositiveButton("Lock", (dialog,which)->{
                 try { policy.lockNow(); }catch(Exception e){popup("Could not lock",e.getMessage());}
             }).show();
        });
        gap(content,16);
        add(content,label("Device Owner must be provisioned on an eligible, consented test device. Installing the APK does not make it Device Owner. No uninstall prevention or financing lock is active.",12,MUTED,false));
    }
    private void popup(String heading,String message) {
        new AlertDialog.Builder(this).setTitle(heading).setMessage(message)
            .setPositiveButton("OK",null).show();
    }
    private void openRegistration(){
        LinearLayout f=vertical(); f.setPadding(dp(22),dp(10),dp(22),0);
        TextView expl=label("For testing only. Details are saved locally on this phone, not to a server.",13,MUTED,false);
        add(f,expl);gap(f,16);
        EditText nm=new EditText(this); nm.setSingleLine(true);nm.setText(registered()?name():"");
        nm.setHint("Customer full name");nm.setTextSize(15);add(f,nm);gap(f,10);
        EditText phone=new EditText(this);phone.setSingleLine(true);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phone.setHint("Contact number (optional)");phone.setText(store.getString("phone",""));
        add(f,phone);gap(f,12);
        chosenDate=activationDate();
        TextView pick=label("Activation date: "+chosenDate.format(DATE)+"   Change",14,FOREST,true);
        pick.setPadding(0,dp(14),0,dp(14));add(f,pick);
        pick.setOnClickListener(v->{
            DatePickerDialog d=new DatePickerDialog(this,(view,y,m,day)->{
                chosenDate=LocalDate.of(y,m+1,day);
                pick.setText("Activation date: "+chosenDate.format(DATE)+"   Change");
            },chosenDate.getYear(),chosenDate.getMonthValue()-1,chosenDate.getDayOfMonth());
            d.show();
        });
        new AlertDialog.Builder(this).setTitle("Test customer registration")
            .setView(f).setNegativeButton("Cancel",null)
            .setPositiveButton("Save locally",(dialog,which)->{
                String person=nm.getText().toString().trim();
                if(person.isEmpty()){popup("Name required","Please enter the customer's name.");return;}
                store.edit().putBoolean("registered",true).putString("name",person)
                    .putString("phone",phone.getText().toString().trim())
                    .putString("date",chosenDate.toString()).apply();
                page=0;showPage();
            }).show();
    }
    @Override protected void onResume(){super.onResume();if(store!=null)showPage();}
}
