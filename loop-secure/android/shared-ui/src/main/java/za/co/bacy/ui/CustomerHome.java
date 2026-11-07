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
    public static final int BLUE=0xff285ce2, INK=0xff202a3a, MUTED=0xff657184;
    public static final int PAPER=0xfff3f5f8, SURFACE=0xffffffff, RULE=0xffe1e6ee, SOFT=0xffebf0fa;
    public static final int GREEN=0xff17866a, AMBER=0xffa86a14;
    private static final int GREEN_SOFT=0xffeaf6f1, AMBER_SOFT=0xfffff4e3;
    public static class Model {
        public String name="Your account",device="Your device",reference="Not enrolled";
        public String status="Not connected",due="Available after enrollment",remaining="—";
        public String monthlyPayment="",subline="Complete enrollment to connect";
        public String foot="Account connection required",banner="",ringCaption="days left";
        /** Formatted verified monetary values. Empty means the value is not available. */
        public String paidTotal="",totalPrice="";
        /** Verified approved claim count; -1 means unknown. */
        public int approvedPayments=-1;
        /** Financed-plan repayment percentage, 0..100; -1 means unknown. Requires totalPrice. */
        public float progressPercent=-1f;
        /** Retained for caller compatibility. Time entitlement is never shown as repayment progress. */
        public float fraction=0;
        public boolean preview=false,paidOff=false;
    }
    public interface Actions { void run(int index); }
    private CustomerHome(){}
    static int d(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static LinearLayout col(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Context c,String value,int size,int color,boolean strong){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(BrandFonts.sans(c,strong));t.setLineSpacing(d(c,2),1);t.setIncludeFontPadding(false);return t;}
    static TextView heading(Context c,String value,int size){TextView t=text(c,value,size,INK,false);t.setTypeface(BrandFonts.heading(c));t.setAccessibilityHeading(true);return t;}
    static String value(String text,String fallback){return text==null||text.trim().isEmpty()?fallback:text;}
    static GradientDrawable shape(Context c,int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(d(c,radius));if(stroke!=0)g.setStroke(d(c,1),stroke);return g;}
    static void space(LinearLayout p,int h){p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,d(p.getContext(),h)));}
    static LinearLayout panel(Context c){LinearLayout l=col(c);l.setPadding(d(c,18),d(c,18),d(c,18),d(c,18));l.setBackground(shape(c,SURFACE,18,0));l.setElevation(d(c,1));return l;}
    static void addPanel(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(parent.getContext(),top);parent.addView(child,p);}
    static void tap(View v,Runnable action,int fill,int radius){Context c=v.getContext();v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20285ce2),shape(c,fill,radius,0),null));v.setFocusable(true);v.setOnClickListener(view->action.run());v.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)view.animate().scaleX(.99f).scaleY(.99f).setDuration(80).start();else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1).scaleY(1).setDuration(120).start();return false;});}
    static void icon(LinearLayout p,String name,int color,int size){Context c=p.getContext();p.addView(new InstrumentIcon(c,name,color),new LinearLayout.LayoutParams(d(c,size),d(c,size)));}
    static void divider(LinearLayout p){View rule=new View(p.getContext());rule.setBackgroundColor(RULE);p.addView(rule,new LinearLayout.LayoutParams(-1,d(p.getContext(),1)));}
    static TextView reading(Context c,String value,int size){TextView t=text(c,value,size,INK,true);t.setMaxLines(1);t.setAutoSizeTextTypeUniformWithConfiguration(18,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    static void button(LinearLayout p,String title,String symbol,int action,Actions actions){Context c=p.getContext();LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER);b.setPadding(d(c,16),d(c,15),d(c,16),d(c,15));b.setMinimumHeight(d(c,54));icon(b,symbol,SURFACE,21);TextView t=text(c,title,15,SURFACE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=d(c,10);b.addView(t,tp);tap(b,()->actions.run(action),BLUE,12);b.setContentDescription(title);addPanel(p,b,18);}
    static void stat(LinearLayout row,String label,String amount,String caption,boolean last){Context c=row.getContext();LinearLayout l=col(c);l.addView(text(c,label,12,MUTED,false));space(l,9);l.addView(reading(c,amount,27));space(l,5);l.addView(text(c,caption,10,MUTED,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(!last)lp.rightMargin=d(c,18);row.addView(l,lp);if(!last){View line=new View(c);line.setBackgroundColor(RULE);LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(d(c,1),d(c,66));rule.rightMargin=d(c,18);row.addView(line,rule);}}
    static void accountRow(LinearLayout parent,String label,String detail,String symbol,int action,Actions actions,boolean last){Context c=parent.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(c,16),d(c,15),d(c,14),d(c,15));row.setMinimumHeight(d(c,68));LinearLayout badge=new LinearLayout(c);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(c,PAPER,11,0));icon(badge,symbol,MUTED,21);row.addView(badge,new LinearLayout.LayoutParams(d(c,38),d(c,38)));LinearLayout copy=col(c);copy.addView(text(c,label,14,INK,true));if(!value(detail,"").isEmpty()){space(copy,4);copy.addView(text(c,detail,11,MUTED,false));}LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=d(c,12);cp.rightMargin=d(c,8);row.addView(copy,cp);icon(row,"chevron",MUTED,17);tap(row,()->actions.run(action),SURFACE,12);row.setContentDescription(label+(value(detail,"").isEmpty()?"":". "+detail));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));if(!last){LinearLayout rule=col(c);rule.setPadding(d(c,66),0,d(c,16),0);divider(rule);parent.addView(rule);}}

    public static View create(Context c,Model m,Actions actions){
        LinearLayout page=col(c);page.setBackgroundColor(PAPER);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=col(c);content.setPadding(d(c,20),d(c,16),d(c,20),d(c,24));scroll.addView(content);
        LinearLayout brand=new LinearLayout(c);brand.setGravity(Gravity.CENTER_VERTICAL);brand.addView(new Logo(c),new LinearLayout.LayoutParams(d(c,38),d(c,38)));TextView wordmark=heading(c,"Loop Secure",22);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=d(c,9);brand.addView(wordmark,wp);LinearLayout help=new LinearLayout(c);help.setGravity(Gravity.CENTER);icon(help,"help",MUTED,23);tap(help,()->actions.run(3),SURFACE,24);help.setContentDescription("Get support");brand.addView(help,new LinearLayout.LayoutParams(d(c,46),d(c,46)));content.addView(brand);space(content,22);
        LinearLayout introduction=new LinearLayout(c);introduction.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout greeting=col(c);greeting.addView(heading(c,"Your account",28));space(greeting,6);greeting.addView(text(c,m.preview?"No customer account connected":("Your account".equals(m.name)?value(m.subline,"Your device plan"):value(m.name,"Your device plan")),14,MUTED,false));
        introduction.addView(greeting,new LinearLayout.LayoutParams(0,-2,1));
        ImageView phones=new ImageView(c);phones.setImageResource(za.co.bacy.ui.PhoneArtwork.resource(c));phones.setScaleType(ImageView.ScaleType.FIT_CENTER);phones.setContentDescription("Loop phone illustration, front and back");
        LinearLayout.LayoutParams phoneParams=new LinearLayout.LayoutParams(d(c,104),d(c,124));phoneParams.leftMargin=d(c,12);introduction.addView(phones,phoneParams);content.addView(introduction);
        String state=value(m.status,"Not connected");boolean paid=m.paidOff||"Paid off".equals(state);String normalized=state.toLowerCase(java.util.Locale.ROOT);boolean attention=normalized.contains("grace")||normalized.contains("overdue")||normalized.contains("restrict")||normalized.contains("expired");boolean connected=paid||normalized.equals("account connected");int stateColor=attention?AMBER:connected?GREEN:MUTED;int stateFill=attention?AMBER_SOFT:connected?GREEN_SOFT:PAPER;
        LinearLayout summary=panel(c);addPanel(content,summary,20);
        LinearLayout summaryHead=new LinearLayout(c);summaryHead.setGravity(Gravity.CENTER_VERTICAL);TextView plan=text(c,paid?"Plan completed":"Monthly payment",13,MUTED,true);summaryHead.addView(plan,new LinearLayout.LayoutParams(0,-2,1));TextView chip=text(c,m.preview?"View only":state,10,stateColor,true);chip.setPadding(d(c,9),d(c,6),d(c,9),d(c,6));chip.setBackground(shape(c,stateFill,7,0));chip.setMaxWidth(d(c,152));summaryHead.addView(chip);summary.addView(summaryHead);space(summary,14);
        if(paid){summary.addView(text(c,"Paid off",30,INK,true));space(summary,7);summary.addView(text(c,"No further plan payments are due.",13,MUTED,false));}
        else {
            LinearLayout amountRow=new LinearLayout(c);amountRow.setGravity(Gravity.BOTTOM);amountRow.addView(reading(c,value(m.monthlyPayment,"—"),34),new LinearLayout.LayoutParams(-2,-2));TextView cadence=text(c,value(m.monthlyPayment,"").isEmpty()?"amount unavailable":" / 30 days",12,MUTED,false);LinearLayout.LayoutParams cadenceParams=new LinearLayout.LayoutParams(0,-2,1);cadenceParams.leftMargin=d(c,7);cadenceParams.bottomMargin=d(c,4);amountRow.addView(cadence,cadenceParams);summary.addView(amountRow);space(summary,20);divider(summary);space(summary,16);
            LinearLayout timing=new LinearLayout(c);LinearLayout due=col(c);due.addView(text(c,attention?"Payment deadline":"Next payment due",11,MUTED,false));space(due,6);due.addView(text(c,value(m.due,"Available after enrollment"),14,INK,true));timing.addView(due,new LinearLayout.LayoutParams(0,-2,1));LinearLayout remaining=col(c);remaining.setGravity(Gravity.END);String left=value(m.remaining,"—");remaining.addView(text(c,"—".equals(left)?"Time unavailable":value(m.ringCaption,"days left"),11,MUTED,false));space(remaining,6);remaining.addView(text(c,left,16,attention?AMBER:INK,true));LinearLayout.LayoutParams remainingParams=new LinearLayout.LayoutParams(-2,-2);remainingParams.leftMargin=d(c,12);timing.addView(remaining,remainingParams);summary.addView(timing);
            button(summary,"Make a payment","payment",6,actions);
        }
        if(!value(m.banner,"").isEmpty()){space(summary,13);TextView note=text(c,m.banner,11,attention?AMBER:MUTED,false);summary.addView(note);}
        LinearLayout bank=new LinearLayout(c);bank.setGravity(Gravity.CENTER_VERTICAL);bank.setPadding(d(c,15),d(c,15),d(c,13),d(c,15));LinearLayout bankIcon=new LinearLayout(c);bankIcon.setGravity(Gravity.CENTER);bankIcon.setBackground(shape(c,SOFT,12,0));icon(bankIcon,"bank",BLUE,23);bank.addView(bankIcon,new LinearLayout.LayoutParams(d(c,42),d(c,42)));LinearLayout bankCopy=col(c);bankCopy.addView(text(c,"Open banking app",14,INK,true));space(bankCopy,4);bankCopy.addView(text(c,"Choose an installed bank",11,MUTED,false));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=d(c,12);bank.addView(bankCopy,bp);icon(bank,"chevron",BLUE,18);tap(bank,()->actions.run(9),SURFACE,16);bank.setContentDescription("Open banking app. Choose an installed banking app.");addPanel(content,bank,12);
        space(content,23);TextView activityTitle=text(c,"Payment activity",14,INK,true);activityTitle.setAccessibilityHeading(true);content.addView(activityTitle);LinearLayout activity=panel(c);addPanel(content,activity,11);LinearLayout totals=new LinearLayout(c);totals.setGravity(Gravity.CENTER_VERTICAL);stat(totals,"Paid so far",value(m.paidTotal,"—"),value(m.paidTotal,"").isEmpty()?"Total unavailable":"Approved payments",false);stat(totals,"Payments approved",m.approvedPayments<0?"—":String.valueOf(m.approvedPayments),m.approvedPayments<0?"Count unavailable":"Verified payments",true);activity.addView(totals,new LinearLayout.LayoutParams(-1,-2));
        boolean hasPrice=!value(m.totalPrice,"").isEmpty();boolean hasProgress=hasPrice&&m.progressPercent>=0&&m.progressPercent<=100;
        if(hasPrice){space(activity,17);divider(activity);space(activity,16);LinearLayout totalRow=new LinearLayout(c);totalRow.addView(text(c,"Financed total",12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));totalRow.addView(text(c,m.totalPrice,13,INK,true));activity.addView(totalRow);if(hasProgress){space(activity,13);activity.addView(new RepaymentTrack(c,m.progressPercent),new LinearLayout.LayoutParams(-1,d(c,6)));space(activity,8);activity.addView(text(c,String.format(java.util.Locale.getDefault(),"%.0f%% repaid",m.progressPercent),11,BLUE,true));}}
        space(content,23);TextView accountTitle=text(c,"Account & device",14,INK,true);accountTitle.setAccessibilityHeading(true);content.addView(accountTitle);LinearLayout account=col(c);account.setBackground(shape(c,SURFACE,16,0));addPanel(content,account,11);accountRow(account,"My plan","Reference · "+value(m.reference,"Not enrolled"),"payment",0,actions,false);accountRow(account,"My device",value(m.device,"Your device"),"phone",2,actions,false);accountRow(account,"Messages","Account updates and notices","message",4,actions,false);accountRow(account,"Device management","Management status and permissions","shield",5,actions,true);
        space(content,16);content.addView(text(c,value(m.foot,"Your account details load after enrollment."),11,MUTED,false));TextView emergency=text(c,"Emergency calling",12,MUTED,true);emergency.setGravity(Gravity.CENTER);emergency.setPadding(d(c,10),d(c,15),d(c,10),d(c,15));emergency.setMinimumHeight(d(c,48));tap(emergency,()->actions.run(8),PAPER,12);addPanel(content,emergency,8);
        dock(page,actions);return page;
    }
    private static void dock(LinearLayout page,Actions actions){Context c=page.getContext();LinearLayout dock=col(c);dock.setBackgroundColor(SURFACE);divider(dock);LinearLayout nav=new LinearLayout(c);nav.setGravity(Gravity.CENTER);nav.setPadding(d(c,8),d(c,5),d(c,8),d(c,5));String[] labels={"Home","Payments","Banking","Support"};String[] icons={"home","payment","bank","help"};int[] mappings={7,1,9,3};for(int i=0;i<labels.length;i++){final int index=mappings[i];LinearLayout item=col(c);item.setGravity(Gravity.CENTER);item.setPadding(d(c,3),d(c,9),d(c,3),d(c,9));int color=i==0?BLUE:MUTED;icon(item,icons[i],color,22);space(item,5);item.addView(text(c,labels[i],10,color,i==0));tap(item,()->actions.run(index),i==0?SOFT:SURFACE,12);item.setContentDescription(labels[i]);item.setSelected(i==0);item.setMinimumHeight(d(c,58));LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(0,-2,1);itemParams.leftMargin=d(c,2);itemParams.rightMargin=d(c,2);nav.addView(item,itemParams);}dock.addView(nav,new LinearLayout.LayoutParams(-1,-2));page.addView(dock,new LinearLayout.LayoutParams(-1,-2));}
    private static final class RepaymentTrack extends View{final float percent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);RepaymentTrack(Context c,float percent){super(c);this.percent=percent;setContentDescription(String.format(java.util.Locale.getDefault(),"%.0f percent repaid",percent));}protected void onDraw(Canvas canvas){paint.setColor(SOFT);canvas.drawRoundRect(0,0,getWidth(),getHeight(),getHeight()/2f,getHeight()/2f,paint);paint.setColor(BLUE);canvas.drawRoundRect(0,0,getWidth()*percent/100f,getHeight(),getHeight()/2f,getHeight()/2f,paint);}}
    public static class Logo extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Drawable mark;
        public Logo(Context c){super(c);setContentDescription("Loop Secure");int id=c.getResources().getIdentifier("loop_secure_mark","drawable",c.getPackageName());Drawable resolved=null;if(id!=0){try{resolved=c.getDrawable(id);}catch(android.content.res.Resources.NotFoundException ignored){}}mark=resolved;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(mark!=null){int width=Math.max(1,mark.getIntrinsicWidth()),height=Math.max(1,mark.getIntrinsicHeight());float scale=Math.min(getWidth()/(float)width,getHeight()/(float)height);int w=Math.round(width*scale),h=Math.round(height*scale);int left=(getWidth()-w)/2,top=(getHeight()-h)/2;mark.setBounds(left,top,left+w,top+h);mark.draw(canvas);return;}canvas.save();canvas.scale(getWidth()/44f,getHeight()/44f);paint.setStyle(Paint.Style.FILL);Path p=new Path();p.moveTo(22,3);p.lineTo(40,13);p.lineTo(22,23);p.lineTo(4,13);p.close();paint.setColor(0xffcbdafb);canvas.drawPath(p,paint);p.reset();p.moveTo(4,13);p.lineTo(22,23);p.lineTo(22,42);p.lineTo(4,32);p.close();paint.setColor(BLUE);canvas.drawPath(p,paint);p.reset();p.moveTo(22,23);p.lineTo(40,13);p.lineTo(40,32);p.lineTo(22,42);p.close();paint.setColor(0xff1746b9);canvas.drawPath(p,paint);p.reset();p.moveTo(12,14);p.lineTo(22,8);p.lineTo(33,14);p.lineTo(22,20);p.close();paint.setColor(SURFACE);canvas.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);p.reset();p.moveTo(10,24);p.lineTo(15,31);p.lineTo(24,24);paint.setColor(SURFACE);canvas.drawPath(p,paint);canvas.restore();}
    }
}
