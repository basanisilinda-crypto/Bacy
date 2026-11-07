package za.co.loopmanager.preview;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import za.co.bacy.ui.BrandFonts;
import za.co.bacy.ui.CustomerHome;
import za.co.bacy.ui.InstrumentIcon;

/** Disconnected native design preview. It never enrolls or invents a customer account. */
public class MainActivity extends AppCompatActivity {
    private static final int PAPER=CustomerHome.PAPER,SURFACE=CustomerHome.SURFACE,INK=CustomerHome.INK,
            MUTED=CustomerHome.MUTED,BLUE=CustomerHome.BLUE,RULE=CustomerHome.RULE,SOFT=CustomerHome.SOFT;
    private LinearLayout content;
    private int tab;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null)tab=state.getInt("tab");if(tab<0||tab>3)tab=0;show();}
    @Override public void onSaveInstanceState(Bundle state){state.putInt("tab",tab);super.onSaveInstanceState(state);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(BrandFonts.sans(this,strong));t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private TextView heading(LinearLayout parent,String value,int size){TextView t=text(parent,value,size,INK,false);t.setTypeface(BrandFonts.sans(this,true));t.setAccessibilityHeading(true);return t;}
    private void space(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(18),dp(18),dp(18),dp(18));l.setBackground(shape(SURFACE,22,0));l.setElevation(dp(2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);content.addView(l,p);return l;}
    private void button(LinearLayout p,String title,boolean primary,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(title);b.setTextSize(14);b.setTypeface(BrandFonts.sans(this,true));b.setAllCaps(false);b.setLetterSpacing(0);b.setCornerRadius(dp(15));b.setInsetTop(0);b.setInsetBottom(0);b.setElevation(0);b.setTextColor(primary?SURFACE:BLUE);b.setBackgroundTintList(ColorStateList.valueOf(primary?BLUE:SOFT));b.setRippleColor(ColorStateList.valueOf(primary?0x33FFFFFF:0x24315E48));b.setMinHeight(dp(52));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(14);p.addView(b,bp);b.setOnClickListener(v->action.run());}
    private void info(String title,String message){LinearLayout body=column();body.setPadding(dp(24),dp(24),dp(24),dp(12));body.setBackgroundColor(SURFACE);heading(body,title,25);space(body,16);text(body,message,14,MUTED,false);space(body,12);ScrollView scroll=new ScrollView(this);scroll.addView(body);AlertDialog dialog=new MaterialAlertDialogBuilder(this).setBackground(shape(SURFACE,24,RULE)).setView(scroll).setPositiveButton("Close",null).create();dialog.setOnShowListener(v->{android.widget.Button close=dialog.getButton(AlertDialog.BUTTON_POSITIVE);close.setTypeface(BrandFonts.sans(this,true));close.setTextColor(BLUE);close.setAllCaps(false);close.setMinHeight(dp(48));});dialog.show();}
    private void go(int selected){tab=selected;show();}
    private void show(){if(tab==0){home();return;}LinearLayout page=column();page.setBackgroundColor(PAPER);page.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);content=column();content.setPadding(dp(20),dp(20),dp(20),dp(18));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));masthead();if(tab==1)payments();else if(tab==2)banking();else support();navigation(page);setContentView(page);}
    private void masthead(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(new CustomerHome.Logo(this),new LinearLayout.LayoutParams(dp(36),dp(36)));TextView name=new TextView(this);name.setText("Loop Secure");name.setTextSize(20);name.setTextColor(INK);name.setTypeface(BrandFonts.heading(this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(10);row.addView(name,p);TextView mode=new TextView(this);mode.setText("PREVIEW");mode.setTextSize(9);mode.setLetterSpacing(.08f);mode.setTextColor(MUTED);mode.setTypeface(BrandFonts.sans(this,true));mode.setPadding(dp(9),dp(7),dp(9),dp(7));mode.setBackground(shape(SURFACE,8,RULE));row.addView(mode);content.addView(row);space(content,26);}
    private void title(String title,String detail){heading(content,title,25);space(content,8);text(content,detail,13,MUTED,false);space(content,20);}
    private void record(LinearLayout p,String label,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);TextView key=new TextView(this);key.setText(label);key.setTextSize(13);key.setTextColor(MUTED);key.setTypeface(BrandFonts.sans(this,false));key.setIncludeFontPadding(false);row.addView(key,new LinearLayout.LayoutParams(0,-2,1));TextView result=new TextView(this);result.setText(value);result.setTextSize(13);result.setTextColor(INK);result.setTypeface(BrandFonts.sans(this,true));result.setGravity(Gravity.END);result.setIncludeFontPadding(false);row.addView(result,new LinearLayout.LayoutParams(0,-2,1));p.addView(row);space(p,12);}
    private void divider(LinearLayout p){View rule=new View(this);rule.setBackgroundColor(RULE);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.topMargin=dp(16);line.bottomMargin=dp(16);p.addView(rule,line);}
    private void sectionLead(LinearLayout p,String icon,String title,String detail){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setBackground(shape(SOFT,12,0));tile.addView(new InstrumentIcon(this,icon,BLUE),new LinearLayout.LayoutParams(dp(23),dp(23)));row.addView(tile,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout labels=column();LinearLayout.LayoutParams labelsSize=new LinearLayout.LayoutParams(0,-2,1);labelsSize.leftMargin=dp(12);text(labels,title,16,INK,true);space(labels,4);text(labels,detail,12,MUTED,false);row.addView(labels,labelsSize);p.addView(row);}
    private void step(LinearLayout p,String number,String title,String detail){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);TextView numberView=new TextView(this);numberView.setText(number);numberView.setTextSize(12);numberView.setTextColor(BLUE);numberView.setTypeface(BrandFonts.sans(this,true));numberView.setGravity(Gravity.CENTER);numberView.setBackground(shape(SOFT,10,0));row.addView(numberView,new LinearLayout.LayoutParams(dp(30),dp(30)));LinearLayout labels=column();LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(0,-2,1);labelSize.leftMargin=dp(12);text(labels,title,14,INK,true);space(labels,5);text(labels,detail,12,MUTED,false);row.addView(labels,labelSize);p.addView(row);space(p,18);}
    private void navigation(LinearLayout page){page.addView(za.co.bacy.ui.SecureNavigation.create(this,new String[]{"Home","Payments","Banking","Support"},new String[]{"home","payment","bank","help"},tab,this::go),new LinearLayout.LayoutParams(-1,-2));}
    private void home(){CustomerHome.Model m=new CustomerHome.Model();m.preview=true;m.foot="View-only preview. Real account details appear after QR enrollment.";setContentView(CustomerHome.create(this,m,index->{if(index==7)go(0);else if(index==0)info("Your plan","The standard contract is R300 per 30 days.\n\nYour actual due date, approved payments and grace period appear after enrollment. No customer account is connected to this preview.");else if(index==1||index==6)go(1);else if(index==2||index==5)device();else if(index==9)go(2);else if(index==8)info("Emergency calling","Emergency calling remains available in the enrolled app. This separate preview does not open the dialer or change device restrictions.");else if(index==4)info("Messages","Administrator messages appear after enrollment. This preview has no connected inbox and cannot send or receive messages.");else go(3);}));}
    private void payments(){
        title("Payments","Your payment plan and EFT steps, in one place.");
        LinearLayout account=panel();sectionLead(account,"payment","Standard plan","Contract terms");space(account,18);
        LinearLayout amountRow=new LinearLayout(this);amountRow.setGravity(Gravity.BOTTOM);
        TextView amount=new TextView(this);amount.setText("R300");amount.setTextSize(30);amount.setTextColor(INK);amount.setTypeface(BrandFonts.heading(this));amount.setIncludeFontPadding(false);amountRow.addView(amount);
        TextView period=new TextView(this);period.setText(" / 30 days");period.setTextSize(13);period.setTextColor(MUTED);period.setTypeface(BrandFonts.sans(this,false));period.setPadding(0,0,0,dp(3));amountRow.addView(period);account.addView(amountRow);
        divider(account);record(account,"Paid so far","Not connected");record(account,"Approved payments","Not connected");text(account,"Your actual due date and payment record appear after enrollment.",12,MUTED,false);
        LinearLayout eft=panel();heading(eft,"Pay by EFT",23);space(eft,18);
        step(eft,"1","Use your account reference","Verified bank details and your unique reference appear in the enrolled app.");
        step(eft,"2","Pay in your banking app","Make the EFT, then return to Loop Secure.");
        step(eft,"3","Submit “I have paid”","Your administrator reviews the claim and confirms settled funds.");
        button(eft,"Open banking",true,()->go(2));
        button(eft,"View payment steps",false,()->info("Payment steps","Open the verified EFT destination in your enrolled app. Make the payment in your bank using your unique reference. Then submit I have paid for administrator review.\n\nThis preview cannot transfer money or submit a claim."));
        text(content,"A claim stays pending until your administrator verifies the EFT. Approved time changes after review.",12,MUTED,false);
        button(content,"How payment review works",false,()->info("Payment review","A payment claim requests a review. It does not confirm receipt of funds.\n\nThe enrolled app displays pending, approved and rejected claims using the real account record."));
    }
    private void banking(){
        title("Banking","Banking access stays available when a payment is due.");
        LinearLayout banks=panel();sectionLead(banks,"bank","Open your bank","Sign in directly with your bank");space(banks,16);
        text(banks,"Choose from banking apps installed on your phone in the enrolled app.",14,MUTED,false);
        button(banks,"Choose banking app",true,()->info("Banking preview","This view-only preview cannot open banking apps.\n\nIn the enrolled app, choose an installed bank and sign in directly with that bank. Loop Secure does not handle your bank sign-in details."));
        LinearLayout details=panel();heading(details,"Before you pay",23);space(details,18);
        step(details,"1","Check your EFT details","Use the verified destination and exact account reference shown in Loop Secure.");
        step(details,"2","Return to Payments","Submit your payment claim after making the EFT.");
        text(details,"Banking apps remain accessible while normal device use is restricted.",12,MUTED,false);button(details,"View payment information",false,()->go(1));
    }
    private void device(){info("Device & management","No customer or managed device is connected to this separate preview.\n\nCustomer record: not connected\nDevice owner: not requested\nLive sync: unavailable\nManagement commands: unavailable\n\nYour administrator supplies the managed app QR code during Android initial setup. The real device and account record loads after enrollment.");}
    private void support(){
        title("Support","Help with your account, payments and phone.");
        LinearLayout contact=panel();sectionLead(contact,"help","Contact Loop Secure","Account and payment support");divider(contact);
        text(contact,"basanisilinda@gmail.com",14,BLUE,true).setTextIsSelectable(true);space(contact,12);
        text(contact,"Include your EFT reference with a payment query so your administrator can identify the transaction.",13,MUTED,false);
        button(contact,"Payment help",true,()->info("Payment support","Include your account reference and describe the payment question when contacting your administrator.\n\nSupport: basanisilinda@gmail.com\n\nThis disconnected preview cannot send support messages."));
        LinearLayout messages=panel();sectionLead(messages,"help","Administrator messages","Available after enrollment");space(messages,14);
        text(messages,"No account is connected to this preview. Your inbox becomes available after enrollment.",13,MUTED,false);button(messages,"Device & enrollment",false,this::device);
    }
}
