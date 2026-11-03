package za.co.bacyflex.core;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/**
 * Shared native UI kit. Understated financial-service design for customer
 * and administrator apps; no WebView, mock screens, or external UI service.
 */
public final class Screen {
    private static final int INK=Color.rgb(31,44,39);
    private static final int FOREST=Color.rgb(25,82,61);
    private static final int PAPER=Color.rgb(247,248,245);
    private static final int MUTED=Color.rgb(103,118,110);
    private static final int LINE=Color.rgb(221,227,220);
    private static final int PALE=Color.rgb(226,239,230);
    private Screen() { }

    public static LinearLayout start(Activity a,String title,String subtitle) {
        a.getWindow().setStatusBarColor(PAPER);
        a.getWindow().setNavigationBarColor(PAPER);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=dp(a,20);
        root.setPadding(p,dp(a,18),p,dp(a,38));
        scroll.addView(root);
        a.setContentView(scroll);

        LinearLayout banner=new LinearLayout(a);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bannerParams=new LinearLayout.LayoutParams(-1,-2);
        bannerParams.bottomMargin=dp(a,27);
        root.addView(banner,bannerParams);

        TextView icon=new TextView(a);
        icon.setText("L");
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(22);
        icon.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        icon.setBackground(shape(a,FOREST,FOREST,12));
        banner.addView(icon,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));

        LinearLayout brand=new LinearLayout(a);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(a,13),0,0,0);
        banner.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView heading=label(a,title,23,true);
        heading.setPadding(0,0,0,dp(a,3));
        brand.addView(heading);
        TextView sub=label(a,subtitle,12,false);
        sub.setTextColor(MUTED);
        sub.setPadding(0,0,0,0);
        brand.addView(sub);
        return root;
    }

    public static TextView label(Activity a,String value,int size,boolean strong) {
        TextView v=new TextView(a);
        v.setText(value);
        v.setTextColor(INK);
        v.setTextSize(size);
        v.setIncludeFontPadding(false);
        v.setPadding(0,dp(a,6),0,dp(a,7));
        v.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        return v;
    }

    public static EditText field(Activity a,LinearLayout box,String hint,boolean secret) {
        EditText e=new EditText(a);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setPadding(dp(a,15),dp(a,12),dp(a,15),dp(a,12));
        e.setBackground(shape(a,Color.WHITE,LINE,13));
        e.setInputType(secret
            ?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD
            :InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,54));
        lp.bottomMargin=dp(a,12);
        box.addView(e,lp);
        return e;
    }

    public static Button button(Activity a,LinearLayout box,String caption,View.OnClickListener listener) {
        Button b=new Button(a);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(shape(a,FOREST,FOREST,14));
        b.setMinHeight(dp(a,53));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,53));
        lp.bottomMargin=dp(a,11);
        box.addView(b,lp);
        b.setOnClickListener(listener);
        return b;
    }

    public static LinearLayout card(Activity a,LinearLayout outer,String title) {
        LinearLayout card=new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a,19),dp(a,17),dp(a,19),dp(a,20));
        card.setBackground(shape(a,Color.WHITE,LINE,20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,17);
        outer.addView(card,lp);
        TextView h=label(a,title,18,true);
        h.setPadding(0,0,0,dp(a,15));
        card.addView(h);
        return card;
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
