package za.co.bacyflex.core;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PorterDuff;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/**
 * MBMF Ledger: a restrained, high-contrast native financial interface.
 * No blue neon, gradients, glowing borders, simulated totals or stock artwork.
 * Business values continue to come only from the actual finance backend.
 */
public final class Screen {
    public static final int INK=Color.rgb(29,37,33);
    public static final int FOREST=Color.rgb(36,78,60);
    public static final int CLAY=Color.rgb(174,90,57);
    public static final int MUTED=Color.rgb(102,109,102);
    public static final int PAPER=Color.rgb(247,245,239);
    public static final int WHITE=Color.rgb(255,254,249);
    public static final int LINE=Color.rgb(220,216,207);
    public static final int GREEN=Color.rgb(36,110,77);
    public static final int AMBER=Color.rgb(149,100,52);
    private Screen() {}

    public static LinearLayout start(Activity a,String title,String subtitle) {
        a.getWindow().setStatusBarColor(INK);
        a.getWindow().setNavigationBarColor(INK);
        a.getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout shell=new FrameLayout(a);
        shell.setBackgroundColor(PAPER);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,22),0,dp(a,22),dp(a,100));
        scroll.addView(root);
        shell.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        a.setContentView(shell);

        androidx.compose.ui.platform.ComposeView masthead=new androidx.compose.ui.platform.ComposeView(a);
        MbmfComposeHeader.attach(masthead,title,subtitle);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,20);
        root.addView(masthead,p);
        return root;
    }

    /** A fixed full-width bottom bar, intentionally unlike a floating icon dock. */
    public static void tabs(Activity a,LinearLayout root,String[] labels,LinearLayout[][] pages) {
        if(labels==null||pages==null||labels.length==0||labels.length!=pages.length)
            throw new IllegalArgumentException("Invalid screen navigation");
        ScrollView scroll=(ScrollView)root.getParent();
        FrameLayout shell=(FrameLayout)scroll.getParent();
        LinearLayout bar=new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a,6),dp(a,4),dp(a,6),dp(a,4));
        bar.setBackgroundColor(INK);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(a,73),Gravity.BOTTOM);
        shell.addView(bar,p);
        TextView[] tabs=new TextView[labels.length];
        for(int i=0;i<labels.length;i++) {
            final int selection=i;
            TextView tab=label(a,labels[i].toUpperCase(java.util.Locale.ROOT),labels.length>4?10:11,true);
            tab.setLetterSpacing(0.06f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(a,2),dp(a,15),dp(a,2),dp(a,15));
            tab.setTextColor(0xffafbbb4);
            bar.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
            tab.setOnClickListener(v->selectTab(scroll,pages,tabs,selection));
            tab.setContentDescription(labels[i]+" screen");
            tabs[i]=tab;
        }
        selectTab(scroll,pages,tabs,0);
    }

    private static void selectTab(ScrollView scroll,LinearLayout[][] pages,TextView[] tabs,int selected) {
        for(int i=0;i<pages.length;i++) {
            boolean active=i==selected;
            tabs[i].setTextColor(active?WHITE:0xffb4bdb7);
            tabs[i].setBackgroundColor(active?FOREST:Color.TRANSPARENT);
            for(LinearLayout section:pages[i]) {
                if(section!=null)section.setVisibility(active?View.VISIBLE:View.GONE);
            }
        }
        scroll.smoothScrollTo(0,0);
    }

    public static TextView label(Activity a,String value,int sp,boolean bold) {
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(INK);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setPadding(0,dp(a,5),0,dp(a,7));
        t.setLineSpacing(dp(a,2),1f);
        return t;
    }

    public static TextView eyebrow(Activity a,String value) {
        TextView t=label(a,value.toUpperCase(java.util.Locale.ROOT),11,true);
        t.setLetterSpacing(0.13f);
        t.setTextColor(MUTED);
        return t;
    }

    /** A ledger section, not a repeated glassy/rounded card. */
    public static LinearLayout card(Activity a,LinearLayout parent,String heading) {
        LinearLayout section=new LinearLayout(a);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0,dp(a,15),0,dp(a,19));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,9);
        parent.addView(section,p);
        View rule=new View(a);rule.setBackgroundColor(LINE);
        section.addView(rule,new LinearLayout.LayoutParams(-1,dp(a,1)));
        if(heading!=null&&!heading.isEmpty()){
            TextView h=eyebrow(a,heading);
            h.setPadding(0,dp(a,16),0,dp(a,12));
            section.addView(h);
        }
        return section;
    }

    /** A full-bleed editorial highlight for verified figures, not a fake dashboard chart. */
    public static LinearLayout spotlight(Activity a,LinearLayout parent,String heading){
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,24),dp(a,22),dp(a,24),dp(a,23));
        panel.setBackground(shape(a,FOREST,FOREST,4));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,22);
        parent.addView(panel,p);
        TextView kicker=label(a,heading.toUpperCase(java.util.Locale.ROOT),11,true);
        kicker.setLetterSpacing(0.17f);
        kicker.setTextColor(0xffd9e3d9);
        panel.addView(kicker);
        return panel;
    }

    public static TextView spotlightAmount(Activity a,LinearLayout parent,String text){
        TextView value=label(a,text,35,true);
        value.setTextColor(WHITE);
        value.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        value.setPadding(0,dp(a,8),0,dp(a,5));
        parent.addView(value);
        return value;
    }

    public static TextView spotlightDetail(Activity a,LinearLayout parent,String text){
        TextView value=label(a,text,13,false);
        value.setTextColor(0xffe5ebe5);
        parent.addView(value);
        return value;
    }

    /** Journal-like, understated figures with a simple baseline. */
    public static TextView metric(Activity a,LinearLayout parent,String heading){
        LinearLayout column=new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(a,8),dp(a,12),dp(a,7),dp(a,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        p.rightMargin=dp(a,12);
        parent.addView(column,p);
        TextView h=eyebrow(a,heading);h.setTextSize(10);
        h.setLetterSpacing(0.04f);
        column.addView(h);
        TextView value=label(a,"—",29,true);
        column.addView(value);
        View rule=new View(a);rule.setBackgroundColor(FOREST);
        LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(dp(a,38),dp(a,2));
        r.topMargin=dp(a,5);column.addView(rule,r);
        return value;
    }

    public static EditText field(Activity a,LinearLayout parent,String hint,boolean secret) {
        LinearLayout wrap=new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(a,16);
        parent.addView(wrap,params);
        TextView caption=eyebrow(a,hint);
        caption.setTextSize(10);
        caption.setPadding(0,0,0,dp(a,6));
        wrap.addView(caption);
        EditText e=new EditText(a);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setPadding(dp(a,12),dp(a,9),dp(a,12),dp(a,10));
        e.setBackground(shape(a,WHITE,LINE,4));
        e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        wrap.addView(e,new LinearLayout.LayoutParams(-1,dp(a,52)));
        return e;
    }

    public static Button button(Activity a,LinearLayout parent,String caption,View.OnClickListener callback) {
        Button button=new Button(a);
        button.setAllCaps(false);
        button.setText(caption);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        boolean secondary=caption.startsWith("Refresh")||caption.startsWith("Copy")
           ||caption.startsWith("Check")||caption.startsWith("Create administrator")
           ||caption.startsWith("Open phone")||caption.startsWith("Show customer");
        button.setTextColor(secondary?INK:WHITE);
        button.setBackground(shape(a,secondary?PAPER:INK,secondary?LINE:INK,4));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,52));
        p.bottomMargin=dp(a,12);
        parent.addView(button,p);
        button.setOnClickListener(callback);
        return button;
    }

    /**
     * Quick links are compact, horizontal business actions—not oversized gradient icon tiles.
     * Existing vector resources are tinted with the current design tokens.
     */
    public static void tile(Activity a,LinearLayout parent,String caption,int drawable,View.OnClickListener callback) {
        LinearLayout link=new LinearLayout(a);
        link.setOrientation(LinearLayout.HORIZONTAL);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(dp(a,11),dp(a,12),dp(a,10),dp(a,12));
        link.setBackground(shape(a,WHITE,LINE,4));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,84),1f);
        p.leftMargin=dp(a,3);p.rightMargin=dp(a,5);p.bottomMargin=dp(a,9);
        parent.addView(link,p);
        ImageView image=new ImageView(a);
        image.setImageResource(drawable);
        image.setColorFilter(FOREST,PorterDuff.Mode.SRC_IN);
        image.setContentDescription(caption);
        link.addView(image,new LinearLayout.LayoutParams(dp(a,25),dp(a,25)));
        TextView text=label(a,caption,13,true);
        text.setPadding(dp(a,9),0,0,0);
        text.setMaxLines(2);
        link.addView(text,new LinearLayout.LayoutParams(0,-2,1f));
        link.setOnClickListener(callback);
        link.setFocusable(true);
        link.setContentDescription(caption);
    }

    public static void info(Activity a,LinearLayout parent,String key,String value) {
        LinearLayout row=new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0,dp(a,8),0,dp(a,8));
        TextView left=label(a,key,13,false);left.setTextColor(MUTED);
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1f));
        TextView right=label(a,value,13,true);
        right.setGravity(Gravity.END);
        row.addView(right);
        parent.addView(row);
        View rule=new View(a);rule.setBackgroundColor(LINE);
        parent.addView(rule,new LinearLayout.LayoutParams(-1,dp(a,1)));
    }

    private static GradientDrawable shape(Activity a,int fill,int edge,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(a,radius));
        d.setStroke(dp(a,1),edge);
        return d;
    }

    private static int dp(Activity a,int n) {
        return Math.round(n*a.getResources().getDisplayMetrics().density);
    }
}