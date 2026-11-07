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
    public static final int BLUE=0xff315e48, INK=0xff1c2c24, MUTED=0xff6c766d;
    public static final int PAPER=0xfff4f4ee, SURFACE=0xffffffff, RULE=0xffdce1d6, SOFT=0xffe5eddf;
    public static final int GREEN=0xff315e48, AMBER=0xffa46135;
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
    static LinearLayout panel(Context c){LinearLayout l=col(c);l.setPadding(d(c,18),d(c,18),d(c,18),d(c,18));l.setBackground(shape(c,SURFACE,22,0));l.setElevation(d(c,2));return l;}
    static void addPanel(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(parent.getContext(),top);parent.addView(child,p);}
    static void tap(View v,Runnable action,int fill,int radius){Context c=v.getContext();v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20315e48),shape(c,fill,radius,0),null));v.setFocusable(true);v.setOnClickListener(view->action.run());v.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.99f).scaleY(.99f).setDuration(80).start();else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(120).start();return false;});}
    static void icon(LinearLayout p,String name,int color,int size){Context c=p.getContext();p.addView(new InstrumentIcon(c,name,color),new LinearLayout.LayoutParams(d(c,size),d(c,size)));}
    static void divider(LinearLayout p){View rule=new View(p.getContext());rule.setBackgroundColor(RULE);p.addView(rule,new LinearLayout.LayoutParams(-1,d(p.getContext(),1)));}
    static TextView reading(Context c,String value,int size){TextView t=text(c,value,size,INK,true);t.setMaxLines(1);t.setAutoSizeTextTypeUniformWithConfiguration(18,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    static void button(LinearLayout p,String title,String symbol,int action,Actions actions){Context c=p.getContext();LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER);b.setPadding(d(c,16),d(c,15),d(c,16),d(c,15));b.setMinimumHeight(d(c,54));icon(b,symbol,SURFACE,21);TextView t=text(c,title,15,SURFACE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=d(c,10);b.addView(t,tp);tap(b,()->actions.run(action),BLUE,12);b.setContentDescription(title);addPanel(p,b,18);}
    public static View create(Context c,Model m,Actions actions){
        LinearLayout page=col(c);page.setBackgroundColor(PAPER);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=col(c);content.setPadding(d(c,18),d(c,14),d(c,18),d(c,16));scroll.addView(content);
        LinearLayout brand=new LinearLayout(c);brand.setGravity(Gravity.CENTER_VERTICAL);brand.addView(new Logo(c),new LinearLayout.LayoutParams(d(c,52),d(c,62)));
        LinearLayout brandCopy=col(c);TextView wordmark=heading(c,"Loop Secure",20);wordmark.setTypeface(BrandFonts.heading(c));brandCopy.addView(wordmark);space(brandCopy,3);brandCopy.addView(text(c,m.appVersion.isEmpty()?"Your device account":"Your device account · v"+m.appVersion,10,MUTED,false));LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(0,-2,1);brandParams.leftMargin=d(c,10);brand.addView(brandCopy,brandParams);
        LinearLayout help=new LinearLayout(c);help.setGravity(Gravity.CENTER);icon(help,"help",INK,22);tap(help,()->actions.run(3),SURFACE,16);help.setContentDescription("Get support");brand.addView(help,new LinearLayout.LayoutParams(d(c,46),d(c,46)));content.addView(brand);space(content,14);
        if(!m.appVersion.isEmpty()){
            LinearLayout release=new LinearLayout(c);release.setGravity(Gravity.CENTER_VERTICAL);release.setPadding(d(c,14),d(c,12),d(c,14),d(c,12));release.setMinimumHeight(d(c,52));icon(release,"refresh",INK,22);TextView releaseText=text(c,"New in "+m.appVersion+" · What’s new",13,INK,true);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=d(c,10);release.addView(releaseText,rp);release.addView(text(c,"→",20,INK,true));tap(release,()->actions.run(13),0xffffdfa8,14);release.setContentDescription("What’s new in "+m.appVersion);content.addView(release);space(content,14);
        }else space(content,6);
        String state=value(m.status,"Not connected");boolean paid=m.paidOff||"Paid off".equals(state);String normalized=state.toLowerCase(java.util.Locale.ROOT);boolean attention=normalized.contains("grace")||normalized.contains("overdue")||normalized.contains("restrict")||normalized.contains("expired");boolean connected=paid||normalized.equals("account connected");
        LinearLayout identity=new LinearLayout(c);identity.setGravity(Gravity.CENTER_VERTICAL);identity.setPadding(d(c,20),d(c,20),d(c,8),d(c,20));identity.setBackground(shape(c,INK,24,0));identity.setElevation(d(c,3));
        LinearLayout accountCopy=col(c);TextView status=text(c,m.preview?"View-only preview":state,10,attention?0xffffd5af:0xffd8e9c6,true);status.setPadding(d(c,9),d(c,6),d(c,9),d(c,6));status.setBackground(shape(c,0xff31463b,8,0));accountCopy.addView(status,new LinearLayout.LayoutParams(-2,-2));space(accountCopy,15);
        accountCopy.addView(text(c,m.preview?"Your phone.\nYour progress.":value(m.name,"Your account"),24,SURFACE,true));space(accountCopy,9);accountCopy.addView(text(c,value(m.device,"Your device"),12,0xffc5d1c8,false));space(accountCopy,5);TextView reference=text(c,value(m.reference,"Not enrolled")+(m.appVersion.isEmpty()?"":"  ·  Copy"),11,0xffd7e8dc,false);if(!m.appVersion.isEmpty()){reference.setPadding(0,d(c,8),d(c,6),d(c,8));reference.setMinHeight(d(c,44));reference.setFocusable(true);reference.setContentDescription("Copy EFT reference");reference.setOnClickListener(v->actions.run(14));}accountCopy.addView(reference);identity.addView(accountCopy,new LinearLayout.LayoutParams(0,-2,1));
        ImageView phones=new ImageView(c);phones.setImageResource(PhoneArtwork.resource(c));phones.setScaleType(ImageView.ScaleType.FIT_CENTER);phones.setContentDescription("Loop phone illustration, front and back");LinearLayout.LayoutParams phoneParams=new LinearLayout.LayoutParams(d(c,106),d(c,150));phoneParams.leftMargin=d(c,8);identity.addView(phones,phoneParams);content.addView(identity);
        LinearLayout timing=new LinearLayout(c);timing.setGravity(Gravity.CENTER_VERTICAL);timing.setPadding(d(c,4),d(c,17),d(c,4),d(c,17));
        LinearLayout due=col(c);due.addView(text(c,paid?"Finance plan":"Next payment",10,MUTED,false));space(due,5);due.addView(text(c,paid?"Completed":value(m.due,"Available after enrollment"),12,INK,true));timing.addView(due,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout remaining=col(c);remaining.setGravity(Gravity.END);TextView timeLabel=text(c,paid?"No further payments":value(m.ringCaption,"days left"),10,MUTED,false);timeLabel.setGravity(Gravity.END);remaining.addView(timeLabel);space(remaining,5);TextView timeValue=text(c,paid?"Paid off":value(m.remaining,"—"),17,attention?AMBER:BLUE,true);timeValue.setGravity(Gravity.END);remaining.addView(timeValue);LinearLayout.LayoutParams remainingParams=new LinearLayout.LayoutParams(0,-2,.65f);remainingParams.leftMargin=d(c,12);timing.addView(remaining,remainingParams);content.addView(timing);
        LinearLayout payment=panel(c);payment.setPadding(d(c,20),d(c,20),d(c,20),d(c,18));addPanel(content,payment,0);
        LinearLayout amountRow=new LinearLayout(c);amountRow.setGravity(Gravity.CENTER_VERTICAL);LinearLayout amountCopy=col(c);amountCopy.addView(text(c,paid?"Plan completed":m.finalPayment?"Final payment":"Monthly payment",11,MUTED,false));space(amountCopy,7);amountCopy.addView(reading(c,paid?"Paid off":value(m.monthlyPayment,"—"),34));space(amountCopy,5);amountCopy.addView(text(c,paid?"No further plan payments are due":value(m.monthlyPayment,"").isEmpty()?"Amount available after enrollment":m.finalPayment?"Completes your phone payments":"Per 30-day period",11,MUTED,false));amountRow.addView(amountCopy,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout paymentIcon=new LinearLayout(c);paymentIcon.setGravity(Gravity.CENTER);paymentIcon.setBackground(shape(c,SOFT,18,0));icon(paymentIcon,paid?"check":"payment",BLUE,26);amountRow.addView(paymentIcon,new LinearLayout.LayoutParams(d(c,56),d(c,56)));payment.addView(amountRow);
        if(!paid)button(payment,"Make a payment","payment",6,actions);
        if(!value(m.banner,"").isEmpty()){space(payment,12);payment.addView(text(c,m.banner,12,attention?AMBER:MUTED,false));}
        space(content,23);section(c,content,"Payment progress","History",()->actions.run(1));
        LinearLayout progress=new LinearLayout(c);progress.setGravity(Gravity.CENTER_VERTICAL);progress.setPadding(d(c,18),d(c,18),d(c,18),d(c,18));progress.setBackground(shape(c,SURFACE,22,0));addPanel(content,progress,10);
        boolean hasPrice=!value(m.totalPrice,"").isEmpty();boolean hasProgress=hasPrice&&m.progressPercent>=0&&m.progressPercent<=100;
        progress.addView(new ProgressDial(c,hasProgress?m.progressPercent:-1,m.approvedPayments),new LinearLayout.LayoutParams(d(c,104),d(c,104)));
        LinearLayout progressCopy=col(c);progressCopy.addView(text(c,"Paid so far",11,MUTED,false));space(progressCopy,6);progressCopy.addView(reading(c,value(m.paidTotal,"—"),27));space(progressCopy,7);progressCopy.addView(text(c,m.approvedPayments<0?"Approved payments unavailable":m.approvedPayments+(m.approvedPayments==1?" payment approved":" payments approved"),11,MUTED,false));if(hasPrice){space(progressCopy,6);progressCopy.addView(text(c,"of "+m.totalPrice,11,BLUE,true));if(!m.balance.isEmpty()){space(progressCopy,6);progressCopy.addView(text(c,m.balance+" left to pay",12,INK,true));}if(!m.paymentsRemaining.isEmpty()){space(progressCopy,4);progressCopy.addView(text(c,m.paymentsRemaining+" payments remaining",10,MUTED,false));}}LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(0,-2,1);progressParams.leftMargin=d(c,20);progress.addView(progressCopy,progressParams);
        space(content,23);section(c,content,"Quick access","",null);space(content,10);
        LinearLayout first=new LinearLayout(c);tile(first,"Banking","Open your bank","bank",9,actions,true);tile(first,"My plan","Your agreement","payment",0,actions,false);content.addView(first);space(content,10);
        LinearLayout second=new LinearLayout(c);tile(second,"My device","Management status","shield",5,actions,false);tile(second,"Messages","Account updates","message",4,actions,false);content.addView(second);space(content,10);LinearLayout connectivity=new LinearLayout(c);tile(connectivity,"Wi-Fi","Connect to internet","wifi",10,actions,false);tile(connectivity,"Settings","Phone preferences","settings",11,actions,false);content.addView(connectivity);
        space(content,17);LinearLayout footer=new LinearLayout(c);footer.setGravity(Gravity.TOP);icon(footer,"shield",BLUE,17);TextView foot=text(c,value(m.foot,"Your account details load after enrollment."),10,MUTED,false);LinearLayout.LayoutParams footParams=new LinearLayout.LayoutParams(0,-2,1);footParams.leftMargin=d(c,8);footer.addView(foot,footParams);content.addView(footer);
        TextView emergency=text(c,"Emergency calling",11,MUTED,true);emergency.setGravity(Gravity.CENTER);emergency.setPadding(d(c,10),d(c,14),d(c,10),d(c,14));emergency.setMinimumHeight(d(c,48));tap(emergency,()->actions.run(8),PAPER,12);addPanel(content,emergency,5);
        page.addView(SecureNavigation.create(c,new String[]{"Home","Payments","Banking","Support"},new String[]{"home","payment","bank","help"},0,index->actions.run(new int[]{7,1,9,3}[index])),new LinearLayout.LayoutParams(-1,-2));return page;
    }
    private static void section(Context c,LinearLayout parent,String title,String action,Runnable run){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(c,title,16,INK,true);name.setAccessibilityHeading(true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));if(run!=null){TextView link=text(c,action+"  →",12,BLUE,true);link.setPadding(d(c,8),d(c,8),0,d(c,8));link.setMinimumHeight(d(c,44));link.setGravity(Gravity.CENTER);link.setOnClickListener(v->run.run());link.setFocusable(true);row.addView(link);}parent.addView(row);}
    private static void tile(LinearLayout row,String title,String detail,String symbol,int action,Actions actions,boolean highlighted){Context c=row.getContext();LinearLayout tile=col(c);tile.setPadding(d(c,16),d(c,17),d(c,12),d(c,17));int color=highlighted?SURFACE:INK;icon(tile,symbol,highlighted?0xffd8e9c6:BLUE,24);space(tile,16);tile.addView(text(c,title,14,color,true));space(tile,5);tile.addView(text(c,detail,10,highlighted?0xffd0ddcf:MUTED,false));int fill=highlighted?0xff075e55:switch(action){case 0->0xffffe4bd;case 4->0xffd8eee5;case 2->0xfff0d9d1;case 10->0xffdcecf0;case 11->0xfff0e6c9;default->SURFACE;};tap(tile,()->actions.run(action),fill,19);tile.setContentDescription(title+". "+detail);tile.setMinimumHeight(d(c,126));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()==0)params.rightMargin=d(c,10);row.addView(tile,params);}
    private static final class ProgressDial extends View{
        final float percent;final int count;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final android.graphics.Typeface font;
        ProgressDial(Context c,float percent,int count){super(c);this.percent=percent;this.count=count;font=BrandFonts.sans(c,true);setContentDescription(percent>=0?String.format(java.util.Locale.getDefault(),"%.0f percent repaid",percent):count>=0?count+" approved payments":"Repayment progress unavailable");}
        protected void onDraw(Canvas canvas){float size=Math.min(getWidth(),getHeight()),centerX=getWidth()/2f,centerY=getHeight()/2f,r=size*.42f;android.graphics.RectF bounds=new android.graphics.RectF(centerX-r,centerY-r,centerX+r,centerY+r);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*.075f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(SOFT);canvas.drawArc(bounds,135,270,false,paint);if(percent>=0){paint.setColor(BLUE);canvas.drawArc(bounds,135,270*percent/100,false,paint);}paint.setStyle(Paint.Style.FILL);paint.setTypeface(font);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(INK);paint.setTextSize(size*.23f);String number=percent>=0?String.format(java.util.Locale.getDefault(),"%.0f%%",percent):count>=0?String.valueOf(count):"—";canvas.drawText(number,centerX,centerY+size*.025f,paint);paint.setTextSize(size*.09f);paint.setColor(MUTED);canvas.drawText(percent>=0?"REPAID":count>=0?"APPROVED":"UNAVAILABLE",centerX,centerY+size*.18f,paint);}
    }
    public static class Logo extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Drawable mark;
        public Logo(Context c){super(c);setContentDescription("Loop Secure");int id=c.getResources().getIdentifier("loop_secure_mark","drawable",c.getPackageName());Drawable resolved=null;if(id!=0){try{resolved=c.getDrawable(id);}catch(android.content.res.Resources.NotFoundException ignored){}}mark=resolved;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(mark!=null){int width=Math.max(1,mark.getIntrinsicWidth()),height=Math.max(1,mark.getIntrinsicHeight());float scale=Math.min(getWidth()/(float)width,getHeight()/(float)height);int w=Math.round(width*scale),h=Math.round(height*scale);int left=(getWidth()-w)/2,top=(getHeight()-h)/2;mark.setBounds(left,top,left+w,top+h);mark.draw(canvas);return;}canvas.save();canvas.scale(getWidth()/44f,getHeight()/44f);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff075e55);canvas.drawRoundRect(new android.graphics.RectF(6,4,18,39),5,5,paint);canvas.drawRoundRect(new android.graphics.RectF(6,27,39,39),5,5,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xffe87843);Path check=new Path();check.moveTo(23,26);check.lineTo(28,31);check.lineTo(38,20);canvas.drawPath(check,paint);canvas.restore();}
    }
}
