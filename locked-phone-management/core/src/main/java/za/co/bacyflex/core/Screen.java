package za.co.bacyflex.core;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.*;

public final class Screen {
    private static final int INK=Color.rgb(22,33,53);
    private static final int BLUE=Color.rgb(35,84,176);
    private static final int PAPER=Color.rgb(245,247,250);
    private Screen() {}
    public static LinearLayout start(Activity a,String title,String subtitle) {
        ScrollView scroll=new ScrollView(a); scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout root=new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        int p=dp(a,20); root.setPadding(p,p,p,p);
        scroll.addView(root); a.setContentView(scroll);
        TextView heading=label(a,title,26,true); root.addView(heading);
        TextView sub=label(a,subtitle,14,false); sub.setPadding(0,0,0,dp(a,22)); root.addView(sub);
        return root;
    }
    public static TextView label(Activity a,String value,int size,boolean strong) {
        TextView v=new TextView(a); v.setText(value); v.setTextColor(INK);
        v.setTextSize(size); v.setPadding(0,dp(a,8),0,dp(a,7));
        if(strong) v.setTypeface(null,Typeface.BOLD); return v;
    }
    public static EditText field(Activity a,LinearLayout box,String hint,boolean secret) {
        EditText e=new EditText(a); e.setSingleLine(true); e.setTextSize(16); e.setHint(hint);
        e.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        e.setBackground(background(a,Color.WHITE,Color.rgb(216,223,231)));
        e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,52)); lp.bottomMargin=dp(a,12);
        box.addView(e,lp); return e;
    }
    public static Button button(Activity a,LinearLayout box,String caption,View.OnClickListener listener) {
        Button b=new Button(a); b.setText(caption); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(15); b.setBackground(background(a,BLUE,BLUE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,50)); lp.bottomMargin=dp(a,10);
        box.addView(b,lp); b.setOnClickListener(listener); return b;
    }
    public static LinearLayout card(Activity a,LinearLayout outer,String title) {
        LinearLayout card=new LinearLayout(a); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,16));
        card.setBackground(background(a,Color.WHITE,Color.rgb(221,227,235)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(a,18); outer.addView(card,lp);
        card.addView(label(a,title,18,true)); return card;
    }
    private static GradientDrawable background(Activity a,int fill,int edge) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(a,12));
        d.setStroke(dp(a,1),edge); return d;
    }
    private static int dp(Activity a,int n) { return Math.round(n*a.getResources().getDisplayMetrics().density); }
}

