package za.co.bacyflex.core;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Shared native finance UI with high-contrast navigation and no web views. */
public final class Screen {
    private static final int INK=Color.rgb(28,34,39);
    private static final int DARK=Color.rgb(30,39,47);
    private static final int PAPER=Color.rgb(248,247,244);
    private static final int WHITE=Color.WHITE;
    private static final int MUTED=Color.rgb(100,109,111);
    private static final int LINE=Color.rgb(227,225,218);
    private static final int GOLD=Color.rgb(227,173,73);
    private Screen(){}

    public static LinearLayout start(Activity a,String title,String subtitle){
        a.getWindow().setStatusBarColor(PAPER);
        a.getWindow().setNavigationBarColor(PAPER);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(PAPER);
        LinearLayout root=new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,34));
        scroll.addView(root);
        a.setContentView(scroll);

        LinearLayout header=new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(a,18),dp(a,19),dp(a,18),dp(a,19));
        header.setBackground(shape(a,DARK,DARK,22));
        LinearLayout.LayoutParams hparams=new LinearLayout.LayoutParams(-1,-2);
        hparams.bottomMargin=dp(a,17);
        root.addView(header,hparams);

        ImageView mark=new ImageView(a);
        mark.setImageResource(R.drawable.lpm_mark);
        mark.setContentDescription("Locked Phone Management logo");
        header.addView(mark,new LinearLayout.LayoutParams(dp(a,47),dp(a,47)));

        LinearLayout namebox=new LinearLayout(a);
        namebox.setOrientation(LinearLayout.VERTICAL);
        namebox.setPadding(dp(a,13),0,0,0);
        header.addView(namebox,new LinearLayout.LayoutParams(0,-2,1));
        TextView kicker=label(a,"LOCKED PHONE MANAGEMENT",11,true);
        kicker.setTextColor(GOLD);
        kicker.setLetterSpacing(.08f);
        kicker.setPadding(0,0,0,dp(a,5));
        namebox.addView(kicker);
        TextView heading=label(a,title,20,true);
        heading.setTextColor(WHITE);
        heading.setPadding(0,0,0,dp(a,6));
        namebox.addView(heading);
        TextView sub=label(a,subtitle,11,false);
        sub.setTextColor(Color.rgb(210,216,211));
        sub.setPadding(0,0,0,0);
        namebox.addView(sub);
        return root;
    }

    /** Adds visible, actual native tab navigation between sections of one app. */
    public static void tabs(Activity a,LinearLayout root,String[] labels,LinearLayout[][] pages){
        if(labels==null||pages==null||labels.length!=pages.length||labels.length==0) {
            throw new IllegalArgumentException("Each tab must have a matching section group.");
        }
        LinearLayout nav=new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(a,4),dp(a,4),dp(a,4),dp(a,4));
        nav.setBackground(shape(a,Color.rgb(238,236,230),Color.rgb(238,236,230),15));
        LinearLayout.LayoutParams nparams=new LinearLayout.LayoutParams(-1,dp(a,55));
        nparams.bottomMargin=dp(a,18);
        root.addView(nav,1,nparams);
        TextView[] tabs=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){
            final int index=i;
            TextView tab=label(a,labels[i],labels.length>3?11:13,true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(a,3),0,dp(a,3),0);
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,-1,1f);
            nav.addView(tab,item);
            tabs[i]=tab;
            tab.setOnClickListener(view->selectTab(a,root,pages,tabs,index));
        }
        selectTab(a,root,pages,tabs,0);
    }

    private static void selectTab(Activity a,LinearLayout root,LinearLayout[][] pages,TextView[] tabs,int selected){
        for(int i=0;i<pages.length;i++){
            boolean active=i==selected;
            tabs[i].setTextColor(active?INK:MUTED);
            tabs[i].setBackground(active?shape(a,WHITE,WHITE,12):null);
            for(LinearLayout page:pages[i]){
                if(page!=null) page.setVisibility(active?View.VISIBLE:View.GONE);
            }
        }
        if(root.getParent() instanceof ScrollView){
            ((ScrollView)root.getParent()).smoothScrollTo(0,0);
        }
    }

    public static TextView label(Activity a,String value,int size,boolean strong){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextColor(INK);
        t.setTextSize(size);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setPadding(0,dp(a,5),0,dp(a,7));
        return t;
    }

    public static EditText field(Activity a,LinearLayout box,String hint,boolean secret){
        EditText t=new EditText(a);
        t.setSingleLine(true);
        t.setTextSize(15);
        t.setTextColor(INK);
        t.setHintTextColor(MUTED);
        t.setHint(hint);
        t.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
        t.setBackground(shape(a,WHITE,LINE,12));
        t.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,dp(a,54));
        item.bottomMargin=dp(a,11);
        box.addView(t,item);
        return t;
    }

    public static Button button(Activity a,LinearLayout box,String text,View.OnClickListener click){
        Button b=new Button(a);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(shape(a,DARK,DARK,13));
        b.setMinHeight(dp(a,52));
        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,dp(a,52));
        item.bottomMargin=dp(a,10);
        box.addView(b,item);
        b.setOnClickListener(click);
        return b;
    }

    public static LinearLayout card(Activity a,LinearLayout root,String heading){
        LinearLayout c=new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a,18),dp(a,18),dp(a,18),dp(a,20));
        c.setBackground(shape(a,WHITE,LINE,18));
        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2);
        item.bottomMargin=dp(a,15);
        root.addView(c,item);
        TextView title=label(a,heading,19,true);
        title.setPadding(0,0,0,dp(a,15));
        c.addView(title);
        return c;
    }

    private static GradientDrawable shape(Activity a,int fill,int stroke,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(a,radius));
        bg.setStroke(dp(a,1),stroke);
        return bg;
    }
    private static int dp(Activity a,int n){
        return Math.round(n*a.getResources().getDisplayMetrics().density);
    }
}
