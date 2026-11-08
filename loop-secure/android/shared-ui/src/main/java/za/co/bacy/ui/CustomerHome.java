package za.co.bacy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared native account overview. Every account reading comes from the enrolled model. */
public final class CustomerHome {
    public static final int BLUE=0xff6441ac, INK=0xff38275c, MUTED=0xff766987;
    public static final int PAPER=0xfffcfbff, SURFACE=0xffffffff, RULE=0xffe3deee, SOFT=0xfff0ebfa;
    public static final int GREEN=0xff6441ac, AMBER=0xffa95b26;
    public static class Model {
        public String appVersion="";
        public String name="Your account",device="Your device",reference="Not enrolled";
        public String status="Not connected",due="Available after enrollment",remaining="—";
        public String monthlyPayment="",subline="Complete enrollment to connect";
        public String foot="Account connection required",banner="",ringCaption="days left";
        /** Formatted verified monetary values. Empty means the value is not available. */
        public String paidTotal="",totalPrice="",balance="",paymentsRemaining="";
        /** Verified approved claim count; -1 means unknown. */
        public int approvedPayments=-1;
        /** Financed-plan repayment percentage, 0..100; -1 means unknown. Requires totalPrice. */
        public float progressPercent=-1f;
        /** Retained for caller compatibility. Time entitlement is never shown as repayment progress. */
        public float fraction=0;
        public boolean preview=false,paidOff=false,finalPayment=false;
    }
    public interface Actions { void run(int index); }
    private CustomerHome(){}
    static int d(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static LinearLayout col(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Context c,String value,int size,int color,boolean strong){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(BrandFonts.sans(c,strong));t.setLineSpacing(d(c,2),1);t.setIncludeFontPadding(false);return t;}
    static TextView heading(Context c,String value,int size){TextView t=text(c,value,size,INK,false);t.setTypeface(BrandFonts.sans(c,true));t.setAccessibilityHeading(true);return t;}
    static String value(String text,String fallback){return text==null||text.trim().isEmpty()?fallback:text;}
    static GradientDrawable shape(Context c,int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(d(c,radius));if(stroke!=0)g.setStroke(d(c,1),stroke);return g;}
    static void space(LinearLayout p,int h){p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,d(p.getContext(),h)));}
    static LinearLayout panel(Context c){LinearLayout l=col(c);l.setPadding(d(c,18),d(c,18),d(c,18),d(c,18));l.setBackground(shape(c,SURFACE,12,RULE));l.setElevation(0);return l;}
    static void addPanel(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(parent.getContext(),top);parent.addView(child,p);}
    static void tap(View v,Runnable action,int fill,int radius){Context c=v.getContext();v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20b83e32),shape(c,fill,radius,0),null));v.setFocusable(true);v.setOnClickListener(view->action.run());v.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.99f).scaleY(.99f).setDuration(80).start();else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(120).start();return false;});}
    static void icon(LinearLayout p,String name,int color,int size){Context c=p.getContext();p.addView(new InstrumentIcon(c,name,color),new LinearLayout.LayoutParams(d(c,size),d(c,size)));}
    static void divider(LinearLayout p){View rule=new View(p.getContext());rule.setBackgroundColor(RULE);p.addView(rule,new LinearLayout.LayoutParams(-1,d(p.getContext(),1)));}
    static TextView reading(Context c,String value,int size){TextView t=text(c,value,size,INK,true);t.setMaxLines(1);t.setAutoSizeTextTypeUniformWithConfiguration(18,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    static void button(LinearLayout p,String title,String symbol,int action,Actions actions){Context c=p.getContext();LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER);b.setPadding(d(c,16),d(c,15),d(c,16),d(c,15));b.setMinimumHeight(d(c,54));icon(b,symbol,SURFACE,21);TextView t=text(c,title,15,SURFACE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=d(c,10);b.addView(t,tp);tap(b,()->actions.run(action),BLUE,12);b.setContentDescription(title);addPanel(p,b,18);}
    public static View create(Context c,Model m,Actions actions){
        LinearLayout page=col(c);page.setBackgroundColor(PAPER);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=col(c);content.setPadding(d(c,20),d(c,12),d(c,20),d(c,18));scroll.addView(content);
        LinearLayout brand=new LinearLayout(c);brand.setGravity(Gravity.CENTER_VERTICAL);brand.addView(new Logo(c),new LinearLayout.LayoutParams(d(c,42),d(c,52)));LinearLayout words=col(c);TextView wordmark=text(c,"Loop Secure",24,INK,true);wordmark.setTypeface(BrandFonts.heading(c));words.addView(wordmark);words.addView(text(c,m.appVersion.isEmpty()?"Your device account":"Your device account · v"+m.appVersion,10,MUTED,false));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=d(c,9);brand.addView(words,wp);LinearLayout help=new LinearLayout(c);help.setGravity(Gravity.CENTER);icon(help,"help",BLUE,23);tap(help,()->actions.run(3),SURFACE,10);help.setContentDescription("Get support");brand.addView(help,new LinearLayout.LayoutParams(d(c,46),d(c,46)));content.addView(brand);space(content,16);
        TextView greeting=heading(c,m.preview?"Your phone plan":value(m.name,"Customer")+"’s phone plan",25);greeting.setTypeface(BrandFonts.heading(c));content.addView(greeting);space(content,5);
        TextView reference=text(c,value(m.reference,"Not enrolled")+"  ·  Copy",11,MUTED,true);reference.setPadding(0,d(c,7),0,d(c,7));reference.setMinHeight(d(c,44));reference.setContentDescription("Copy EFT reference");reference.setFocusable(true);reference.setOnClickListener(v->actions.run(14));content.addView(reference);space(content,7);
        boolean paid=m.paidOff||"Paid off".equals(m.status);String state=value(m.status,"Not connected");boolean attention=state.toLowerCase(java.util.Locale.ROOT).matches(".*(grace|overdue|restrict|expired).*");
        LinearLayout payment=col(c);payment.setPadding(d(c,20),d(c,20),d(c,18),d(c,18));payment.setBackground(shape(c,0xffffffff,12,RULE));LinearLayout paymentTop=new LinearLayout(c);paymentTop.setGravity(Gravity.CENTER_VERTICAL);LinearLayout amount=col(c);amount.addView(text(c,paid?"Phone paid in full":m.finalPayment?"Your final payment":"Next payment",13,INK,true));space(amount,6);TextView reading=reading(c,paid?"Paid off":value(m.monthlyPayment,"—"),46);reading.setTypeface(BrandFonts.heading(c));amount.addView(reading);space(amount,8);amount.addView(text(c,paid?"No further payments":value(m.due,"Available after enrollment"),12,MUTED,false));space(amount,7);TextView status=text(c,m.preview?"View-only preview":state,11,attention?0xff806521:0xff5d5c39,true);amount.addView(status);paymentTop.addView(amount,new LinearLayout.LayoutParams(0,-2,1));ImageView phones=new ImageView(c);phones.setImageResource(PhoneArtwork.resource(c));phones.setScaleType(ImageView.ScaleType.FIT_CENTER);phones.setContentDescription("Loop phone illustration, front and back");paymentTop.addView(phones,new LinearLayout.LayoutParams(d(c,91),d(c,123)));payment.addView(paymentTop);if(!paid)button(payment,"Make a payment","payment",6,actions);if(attention){TextView code=text(c,"Enter administrator code →",12,BLUE,true);code.setGravity(Gravity.CENTER);code.setMinHeight(d(c,48));code.setContentDescription("Enter administrator code");code.setFocusable(true);code.setOnClickListener(v->actions.run(15));payment.addView(code);}space(content,8);
        section(c,content,"Plan overview","History",()->actions.run(1));space(content,13);boolean hasPrice=!value(m.totalPrice,"").isEmpty();boolean hasProgress=hasPrice&&m.progressPercent>=0&&m.progressPercent<=100;
        LinearLayout ledger=new LinearLayout(c);String[] labels={"Paid","Remaining","Total"},values={value(m.paidTotal,"—"),value(m.balance,"—"),value(m.totalPrice,"—")};for(int i=0;i<3;i++){LinearLayout cell=col(c);cell.addView(text(c,labels[i],11,MUTED,false));space(cell,5);TextView number=reading(c,values[i],23);number.setTypeface(BrandFonts.heading(c));cell.addView(number);ledger.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}content.addView(ledger);space(content,14);content.addView(new RepaymentTrack(c,hasProgress?m.progressPercent:-1,m.approvedPayments),new LinearLayout.LayoutParams(-1,d(c,10)));space(content,9);LinearLayout caption=new LinearLayout(c);caption.addView(text(c,m.approvedPayments<0?"Approved payments unavailable":m.approvedPayments+(m.approvedPayments==1?" payment approved":" payments approved"),10,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));if(hasProgress)caption.addView(text(c,String.format(java.util.Locale.getDefault(),"%.0f%% repaid",m.progressPercent),12,BLUE,true));content.addView(caption);space(content,12);
        LinearLayout time=new LinearLayout(c);time.setGravity(Gravity.CENTER_VERTICAL);time.addView(text(c,paid?"Plan completed":value(m.ringCaption,"days left"),11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));time.addView(text(c,paid?"Paid off":value(m.remaining,"—"),15,INK,true));content.addView(time);space(content,20);content.addView(payment);space(content,16);
        if(!m.appVersion.isEmpty()){TextView release=text(c,"What’s new in "+m.appVersion+"   →",12,BLUE,true);release.setPadding(d(c,12),d(c,12),d(c,12),d(c,12));release.setMinHeight(d(c,44));release.setContentDescription("What’s new in "+m.appVersion);tap(release,()->actions.run(13),0xfff0ebfa,8);content.addView(release);space(content,18);}
        LinearLayout tools=new LinearLayout(c);tool(tools,"Banking","bank",9,actions);tool(tools,"My plan","payment",0,actions);tool(tools,"My device","shield",5,actions);content.addView(tools);space(content,12);LinearLayout network=new LinearLayout(c);tool(network,"Messages","message",4,actions);tool(network,"Wi-Fi","wifi",10,actions);tool(network,"Settings","settings",11,actions);content.addView(network);space(content,18);divider(content);space(content,12);content.addView(text(c,value(m.foot,"Account connection required"),11,MUTED,false));if(!value(m.banner,"").isEmpty()){space(content,7);content.addView(text(c,m.banner,10,MUTED,false));}TextView emergency=text(c,"Emergency calling",12,BLUE,true);emergency.setGravity(Gravity.CENTER);emergency.setMinHeight(d(c,48));tap(emergency,()->actions.run(8),PAPER,8);content.addView(emergency);
        page.addView(SecureNavigation.create(c,new String[]{"Home","Payments","Banking","Support"},new String[]{"home","payment","bank","help"},0,index->actions.run(new int[]{7,1,9,3}[index])),new LinearLayout.LayoutParams(-1,-2));return page;
    }
    private static void section(Context c,LinearLayout p,String name,String action,Runnable run){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);TextView title=heading(c,name,21);title.setTypeface(BrandFonts.heading(c));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView link=text(c,action+" →",12,BLUE,true);link.setMinHeight(d(c,44));link.setGravity(Gravity.CENTER);link.setFocusable(true);link.setOnClickListener(v->run.run());row.addView(link);p.addView(row);}
    private static void tool(LinearLayout parent,String title,String glyph,int index,Actions actions){Context c=parent.getContext();LinearLayout v=col(c);v.setGravity(Gravity.CENTER);v.setPadding(d(c,4),d(c,13),d(c,4),d(c,12));icon(v,glyph,BLUE,24);space(v,10);v.addView(text(c,title,11,INK,true));tap(v,()->actions.run(index),SURFACE,10);v.setContentDescription(title+". Open");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=parent.getChildCount()<2?d(c,8):0;parent.addView(v,p);}
    private static final class RepaymentTrack extends View{
        final float percent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        RepaymentTrack(Context c,float value,int count){super(c);percent=value;setContentDescription(value>=0?String.format(java.util.Locale.getDefault(),"%.0f percent repaid",value):count>=0?count+" approved payments":"Repayment progress unavailable");}
        @Override protected void onDraw(Canvas c){float gap=getResources().getDisplayMetrics().density*3,w=(getWidth()-gap*9)/10f;for(int i=0;i<10;i++){float left=i*(w+gap);paint.setColor(RULE);c.drawRoundRect(left,0,left+w,getHeight(),2,2,paint);float fill=percent<0?0:Math.max(0,Math.min(1,percent/10-i));if(fill>0){paint.setColor(BLUE);c.drawRoundRect(left,0,left+w*fill,getHeight(),2,2,paint);}}}
    }
    public static class Logo extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Drawable mark;
        public Logo(Context c){super(c);setContentDescription("Loop Secure");int id=c.getResources().getIdentifier("loop_secure_mark","drawable",c.getPackageName());Drawable resolved=null;if(id!=0){try{resolved=c.getDrawable(id);}catch(android.content.res.Resources.NotFoundException ignored){}}mark=resolved;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(mark!=null){int width=Math.max(1,mark.getIntrinsicWidth()),height=Math.max(1,mark.getIntrinsicHeight());float scale=Math.min(getWidth()/(float)width,getHeight()/(float)height);int w=Math.round(width*scale),h=Math.round(height*scale);int left=(getWidth()-w)/2,top=(getHeight()-h)/2;mark.setBounds(left,top,left+w,top+h);mark.draw(canvas);return;}canvas.save();canvas.scale(getWidth()/44f,getHeight()/44f);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff075e55);canvas.drawRoundRect(new android.graphics.RectF(6,4,18,39),5,5,paint);canvas.drawRoundRect(new android.graphics.RectF(6,27,39,39),5,5,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xffe87843);Path check=new Path();check.moveTo(23,26);check.lineTo(28,31);check.lineTo(38,20);canvas.drawPath(check,paint);canvas.restore();}
    }
}
