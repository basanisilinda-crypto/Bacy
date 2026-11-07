package za.co.bacy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native navigation rail, including readable names for every destination. */
public final class SecureNavigation {
    public interface Select { void open(int index); }
    private SecureNavigation() {}
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static View create(Context c,String[] names,String[] icons,int selected,Select select){
        LinearLayout outer=new LinearLayout(c);outer.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,10));outer.setBackgroundColor(CustomerHome.PAPER);
        LinearLayout rail=new LinearLayout(c);rail.setPadding(dp(c,6),dp(c,6),dp(c,6),dp(c,6));GradientDrawable background=new GradientDrawable();background.setColor(CustomerHome.INK);background.setCornerRadius(dp(c,23));rail.setBackground(background);rail.setElevation(dp(c,4));
        for(int i=0;i<names.length;i++){
            final int index=i;boolean active=i==selected;int color=active?CustomerHome.INK:0xffc5d1c8;
            LinearLayout item=new LinearLayout(c);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(c,3),dp(c,10),dp(c,3),dp(c,10));
            GradientDrawable tile=new GradientDrawable();tile.setColor(active?0xffd8e9c6:CustomerHome.INK);tile.setCornerRadius(dp(c,17));item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),tile,null));
            item.addView(new InstrumentIcon(c,icons[i],color),new LinearLayout.LayoutParams(dp(c,21),dp(c,21)));
            TextView label=new TextView(c);label.setText(names[i]);label.setTextSize(10);label.setTextColor(color);label.setTypeface(BrandFonts.sans(c,active));label.setGravity(Gravity.CENTER);label.setPadding(0,dp(c,5),0,0);item.addView(label);
            item.setMinimumHeight(dp(c,60));item.setContentDescription(names[i]+(active?", current screen":""));item.setSelected(active);item.setFocusable(true);item.setOnClickListener(v->select.open(index));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(c,2),0,dp(c,2),0);rail.addView(item,p);
        }
        outer.addView(rail,new LinearLayout.LayoutParams(-1,-2));return outer;
    }
}
