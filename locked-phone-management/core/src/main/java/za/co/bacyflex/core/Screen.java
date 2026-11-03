package za.co.bacyflex.core;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * MBMF native shared UI. Calm navy headers, crisp white surfaces, cobalt actions.
 * All finance figures are loaded from the live backend, never hard-coded.
 */
public final class Screen {
    public static final int NAVY=Color.rgb(15,34,59);
    public static final int BLUE=Color.rgb(24,103,220);
    public static final int INK=Color.rgb(29,43,62);
    public static final int MUTED=Color.rgb(97,112,131);
    public static final int PAPER=Color.rgb(244,247,251);
    public static final int LINE=Color.rgb(223,231,242);
    public static final int GREEN=Color.rgb(19,137,86);
    public static final int AMBER=Color.rgb(172,108,24);
    private Screen(){}

    public static LinearLayout start(Activity a,String title,String subtitle){
        a.getWindow().setStatusBarColor(NAVY);
        a.getWindow().setNavigationBarColor(PAPER);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout shell=new FrameLayout(a);
        shell.setBackgroundColor(PAPER);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,17),dp(a,16),dp(a,17),dp(a,105));
        scroll.addView(root);
        shell.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        a.setContentView(shell);

        androidx.compose.ui.platform.ComposeView brandHeader=new androidx.compose.ui.platform.ComposeView(a);
        MbmfComposeHeader.attach(brandHeader,title,subtitle);
        LinearLayout.LayoutParams headerParams=new LinearLayout.LayoutParams(-1,-2);
        headerParams.bottomMargin=dp(a,18);
        root.addView(brandHeader,headerParams);
        return root;
    }

    /** Fixed bottom navigation; tabs change content without opening another page. */
    public static void tabs(Activity a,LinearLayout root,String[] labels,LinearLayout[][] pages){
        if(labels==null||pages==null||labels.length!=pages.length||labels.length==0)
            throw new IllegalArgumentException("Invalid navigation");
        ScrollView scroll=(ScrollView)root.getParent();
        FrameLayout shell=(FrameLayout)scroll.getParent();
        LinearLayout nav=new LinearLayout(a);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(a,6),dp(a,8),dp(a,6),dp(a,8));
        nav.setBackground(shape(a,Color.WHITE,LINE,18));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(a,65),Gravity.BOTTOM);
        p.leftMargin=dp(a,10);p.rightMargin=dp(a,10);p.bottomMargin=dp(a,8);
        shell.addView(nav,p);
        TextView[] buttons=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){
            final int index=i;
            TextView tab=label(a,labels[i],labels.length>=5?11:13,true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(a,3),0,dp(a,3),0);
            nav.addView(tab,new LinearLayout.LayoutParams(0,-1,1f));
            buttons[i]=tab;
            tab.setOnClickListener(v->selectTab(a,scroll,pages,buttons,index));
        }
        selectTab(a,scroll,pages,buttons,0);
    }

    private static void selectTab(Activity a,ScrollView scroll,LinearLayout[][] pages,TextView[] buttons,int index){
        for(int i=0;i<pages.length;i++){
            boolean active=(i==index);
            buttons[i].setTextColor(active?BLUE:MUTED);
            buttons[i].setBackground(active?shape(a,Color.rgb(231,241,255),Color.rgb(231,241,255),12):null);
            for(LinearLayout section:pages[i]) if(section!=null)
                section.setVisibility(active?View.VISIBLE:View.GONE);
        }
        scroll.smoothScrollTo(0,0);
    }

    public static TextView label(Activity a,String text,int size,boolean bold){
        TextView t=new TextView(a);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(INK);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setPadding(0,dp(a,4),0,dp(a,6));
        return t;
    }

    public static EditText field(Activity a,LinearLayout parent,String hint,boolean secret){
        EditText e=new EditText(a);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setPadding(dp(a,13),dp(a,12),dp(a,13),dp(a,12));
        e.setBackground(shape(a,Color.WHITE,LINE,12));
        e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(a,54));
        params.bottomMargin=dp(a,12);
        parent.addView(e,params);
        return e;
    }

    public static Button button(Activity a,LinearLayout parent,String text,View.OnClickListener action){
        Button b=new Button(a);
        b.setText(text);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(shape(a,BLUE,BLUE,12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,53));
        p.bottomMargin=dp(a,11);
        parent.addView(b,p);
        b.setOnClickListener(action);
        return b;
    }

    public static LinearLayout card(Activity a,LinearLayout parent,String heading){
        LinearLayout c=new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a,17),dp(a,16),dp(a,17),dp(a,18));
        c.setBackground(shape(a,Color.WHITE,LINE,17));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,16);
        parent.addView(c,p);
        if(heading!=null&&!heading.isEmpty()){
            TextView t=label(a,heading,18,true);
            t.setPadding(0,0,0,dp(a,13));
            c.addView(t);
        }
        return c;
    }

    public static TextView metric(Activity a,LinearLayout parent,String title){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a,13),dp(a,14),dp(a,12),dp(a,15));
        box.setBackground(shape(a,Color.rgb(237,244,255),Color.rgb(220,232,249),14));
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1f);
        cell.leftMargin=dp(a,3);cell.rightMargin=dp(a,3);
        parent.addView(box,cell);
        TextView label=label(a,title,12,false);
        label.setTextColor(MUTED);box.addView(label);
        TextView value=label(a,"—",25,true);
        value.setTextColor(NAVY);box.addView(value);
        return value;
    }

    /** Compact, tappable dashboard tile with custom vector artwork. */
    public static void tile(Activity a,LinearLayout row,String caption,int iconRes,View.OnClickListener click){
        LinearLayout tile=new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(a,9),dp(a,13),dp(a,9),dp(a,12));
        tile.setBackground(shape(a,Color.WHITE,LINE,15));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(a,118),1f);
        params.leftMargin=dp(a,4);params.rightMargin=dp(a,4);
        params.bottomMargin=dp(a,9);
        row.addView(tile,params);
        ImageView icon=new ImageView(a);
        icon.setImageResource(iconRes);
        icon.setContentDescription(caption);
        tile.addView(icon,new LinearLayout.LayoutParams(dp(a,39),dp(a,39)));
        TextView title=label(a,caption,14,true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,dp(a,12),0,0);
        tile.addView(title);
        tile.setOnClickListener(click);
        tile.setFocusable(true);
        tile.setContentDescription(caption);
    }

    public static void info(Activity a,LinearLayout card,String name,String value){
        LinearLayout line=new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0,dp(a,7),0,dp(a,8));
        TextView k=label(a,name,13,false);k.setTextColor(MUTED);
        line.addView(k,new LinearLayout.LayoutParams(0,-2,1));
        TextView v=label(a,value,13,true);
        v.setGravity(Gravity.END);
        line.addView(v);
        card.addView(line);
    }

    private static GradientDrawable shape(Activity a,int fill,int edge,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(a,radius));
        d.setStroke(dp(a,1),edge);
        return d;
    }
    private static int dp(Activity a,int n){
        return Math.round(n*a.getResources().getDisplayMetrics().density);
    }
}
