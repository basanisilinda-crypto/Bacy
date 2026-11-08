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
    public static final int BLUE=0xff000000, INK=0xff000000, MUTED=0xff595959;
    public static final int PAPER=0xffffffff, SURFACE=0xffffffff, RULE=0xffdedede, SOFT=0xfff3f5f4;
    public static final int GREEN=0xff17634d, AMBER=0xff000000;
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
    static void tap(View v,Runnable action,int fill,int radius){Context c=v.getContext();v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20000000),shape(c,fill,radius,0),null));v.setFocusable(true);v.setOnClickListener(view->action.run());v.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.99f).scaleY(.99f).setDuration(80).start();else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(120).start();return false;});}
    static void icon(LinearLayout p,String name,int color,int size){Context c=p.getContext();p.addView(new InstrumentIcon(c,name,color),new LinearLayout.LayoutParams(d(c,size),d(c,size)));}
    static void divider(LinearLayout p){View rule=new View(p.getContext());rule.setBackgroundColor(RULE);p.addView(rule,new LinearLayout.LayoutParams(-1,d(p.getContext(),1)));}
    static TextView reading(Context c,String value,int size){TextView t=text(c,value,size,INK,true);t.setMaxLines(1);t.setAutoSizeTextTypeUniformWithConfiguration(18,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    static void button(LinearLayout p,String title,String symbol,int action,Actions actions){Context c=p.getContext();LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER);b.setPadding(d(c,16),d(c,15),d(c,16),d(c,15));b.setMinimumHeight(d(c,54));icon(b,symbol,SURFACE,21);TextView t=text(c,title,15,SURFACE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=d(c,10);b.addView(t,tp);tap(b,()->actions.run(action),BLUE,12);b.setContentDescription(title);addPanel(p,b,18);}
    public static View create(Context c,Model m,Actions actions){
        LinearLayout page=col(c);page.setBackgroundColor(PAPER);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=col(c);content.setPadding(d(c,20),d(c,14),d(c,20),d(c,12));scroll.addView(content);
        LinearLayout masthead=new LinearLayout(c);masthead.setGravity(Gravity.CENTER_VERTICAL);TextView brand=heading(c,"LOOP SECURE",28);brand.setTypeface(BrandFonts.heading(c));brand.setLetterSpacing(.035f);masthead.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView support=text(c,"Help ↗",13,INK,true);support.setMinHeight(d(c,48));support.setGravity(Gravity.CENTER);support.setContentDescription("Get support");support.setOnClickListener(v->actions.run(3));masthead.addView(support);content.addView(masthead);divider(content);space(content,18);
        TextView hello=text(c,m.preview?"Your phone, your progress.":"Hello, "+value(m.name,"Customer")+".",21,INK,true);content.addView(hello);space(content,6);content.addView(text(c,value(m.status,"Not connected"),12,MUTED,false));space(content,20);
        boolean paid=m.paidOff||"Paid off".equals(m.status);boolean attention=value(m.status,"").toLowerCase(java.util.Locale.ROOT).matches(".*(grace|overdue|restrict|expired).*");
        LinearLayout hero=col(c);hero.setPadding(d(c,20),d(c,20),d(c,18),d(c,18));hero.setBackground(shape(c,0xff000000,5,0));LinearLayout face=new LinearLayout(c);face.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=col(c);copy.addView(text(c,paid?"YOUR PHONE IS PAID OFF":m.finalPayment?"ONE LAST PAYMENT":"YOUR NEXT PAYMENT",11,0xffcccccc,true));space(copy,5);TextView amount=text(c,paid?"Complete":value(m.monthlyPayment,"—"),56,0xffffffff,true);amount.setTypeface(BrandFonts.heading(c));copy.addView(amount);space(copy,5);copy.addView(text(c,paid?"No more instalments":"Due "+value(m.due,"after enrollment"),13,0xffffffff,false));if(!paid){space(copy,8);LinearLayout time=new LinearLayout(c);time.addView(text(c,value(m.remaining,"—"),13,0xffffffff,true));TextView caption=text(c,"  "+value(m.ringCaption,"days left"),12,0xffcccccc,false);time.addView(caption);copy.addView(time);}face.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView phone=new ImageView(c);phone.setImageResource(PhoneArtwork.resource(c));phone.setScaleType(ImageView.ScaleType.FIT_CENTER);phone.setContentDescription("Loop phone, front and back");face.addView(phone,new LinearLayout.LayoutParams(d(c,88),d(c,120)));hero.addView(face);
        if(!paid){TextView pay=text(c,"Pay now     ↗",16,INK,true);pay.setGravity(Gravity.CENTER);pay.setMinHeight(d(c,52));tap(pay,()->actions.run(6),0xffffffff,3);pay.setContentDescription("Make a payment");addPanel(hero,pay,18);}content.addView(hero);space(content,22);
        section(c,content,"OWN IT, BIT BY BIT","Details",()->actions.run(0));space(content,12);boolean hasProgress=!value(m.totalPrice,"").isEmpty()&&m.progressPercent>=0&&m.progressPercent<=100;
        LinearLayout progress=new LinearLayout(c);progress.setGravity(Gravity.BOTTOM);TextView percent=text(c,hasProgress?String.format(java.util.Locale.getDefault(),"%.0f%%",m.progressPercent):"—",48,INK,true);percent.setTypeface(BrandFonts.heading(c));progress.addView(percent,new LinearLayout.LayoutParams(0,-2,1));LinearLayout totals=col(c);totals.addView(text(c,value(m.paidTotal,"—")+" paid",17,INK,true));space(totals,4);totals.addView(text(c,"of "+value(m.totalPrice,"your phone price"),12,MUTED,false));content.addView(progress);space(content,5);content.addView(text(c,value(m.paidTotal,"—")+" paid  /  "+value(m.totalPrice,"—")+" total",14,INK,true));space(content,12);content.addView(new RepaymentTrack(c,hasProgress?m.progressPercent:-1,m.approvedPayments),new LinearLayout.LayoutParams(-1,d(c,12)));space(content,13);
        LinearLayout numbers=new LinearLayout(c);metric(numbers,"LEFT TO PAY",value(m.balance,"—"));metric(numbers,"PAYMENTS MADE",m.approvedPayments<0?"—":String.valueOf(m.approvedPayments));metric(numbers,"TO GO",value(m.paymentsRemaining,"—"));content.addView(numbers);space(content,20);divider(content);
        LinearLayout utilities=new LinearLayout(c);tool(utilities,"Check payment","refresh",16,actions);tool(utilities,"Payment reminder","history",17,actions);content.addView(utilities);divider(content);space(content,22);
        section(c,content,"YOUR PHONE, CONNECTED","Status",()->actions.run(5));space(content,8);LinearLayout first=new LinearLayout(c);tool(first,"Banking apps","bank",9,actions);tool(first,"Messages","message",4,actions);content.addView(first);LinearLayout second=new LinearLayout(c);tool(second,"Wi-Fi","wifi",10,actions);tool(second,"Phone settings","settings",11,actions);content.addView(second);space(content,18);
        if(attention){TextView code=text(c,"Have an administrator unlock code?  →",13,INK,true);code.setMinHeight(d(c,48));code.setOnClickListener(v->actions.run(15));content.addView(code);}
        TextView reference=text(c,"Reference  "+value(m.reference,"Not enrolled")+"   ⧉",12,INK,true);reference.setMinHeight(d(c,48));reference.setGravity(Gravity.CENTER_VERTICAL);reference.setContentDescription("Copy EFT reference");reference.setOnClickListener(v->actions.run(14));content.addView(reference);content.addView(text(c,value(m.foot,"Account connection required"),12,MUTED,false));if(!value(m.banner,"").isEmpty()){space(content,6);content.addView(text(c,m.banner,11,MUTED,false));}space(content,10);
        LinearLayout footer=new LinearLayout(c);TextView release=text(c,"Your device account · v"+m.appVersion+"  /  What’s new",11,MUTED,true);release.setMinHeight(d(c,48));release.setGravity(Gravity.CENTER_VERTICAL);release.setContentDescription("What’s new in "+m.appVersion);release.setOnClickListener(v->actions.run(13));footer.addView(release,new LinearLayout.LayoutParams(0,-2,1));TextView emergency=text(c,"Emergency call",11,INK,true);emergency.setMinHeight(d(c,48));emergency.setGravity(Gravity.CENTER_VERTICAL);emergency.setOnClickListener(v->actions.run(8));footer.addView(emergency);content.addView(footer);
        page.addView(SecureNavigation.create(c,new String[]{"Home","Payments","Banking","Support"},new String[]{"home","payment","bank","help"},0,index->actions.run(new int[]{7,1,9,3}[index])),new LinearLayout.LayoutParams(-1,-2));return page;
    }
    private static void metric(LinearLayout row,String label,String value){Context c=row.getContext();LinearLayout cell=col(c);cell.addView(text(c,label,9,MUTED,true));space(cell,6);TextView number=text(c,value,22,INK,true);number.setTypeface(BrandFonts.heading(c));cell.addView(number);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}
    private static void section(Context c,LinearLayout p,String name,String action,Runnable run){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);TextView title=heading(c,name,21);title.setTypeface(BrandFonts.heading(c));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView link=text(c,action+" →",12,BLUE,true);link.setMinHeight(d(c,44));link.setGravity(Gravity.CENTER);link.setFocusable(true);link.setOnClickListener(v->run.run());row.addView(link);p.addView(row);}
    private static void tool(LinearLayout parent,String title,String glyph,int index,Actions actions){Context c=parent.getContext();LinearLayout tile=new LinearLayout(c);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(d(c,8),d(c,16),d(c,4),d(c,16));icon(tile,glyph,INK,20);TextView label=text(c,title,12,INK,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=d(c,9);tile.addView(label,lp);tap(tile,()->actions.run(index),SURFACE,0);tile.setContentDescription(title+". Open");tile.setMinimumHeight(d(c,58));parent.addView(tile,new LinearLayout.LayoutParams(0,-2,1));}
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
