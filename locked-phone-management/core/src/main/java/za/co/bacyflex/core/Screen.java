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
 * MBMF / EDITION ONE. Completely rebuilt native Android design system:
 * editorial typography, warm paper, terracotta actions, unboxed content,
 * understated lists and a quiet type-only navigation rail.
 */
public final class Screen {
    public static final int INK = Color.rgb(26,25,23);
    public static final int PAPER = Color.rgb(247,245,238);
    public static final int CLAY = Color.rgb(180,65,46);
    public static final int MUTED = Color.rgb(110,106,97);
    public static final int LINE = Color.rgb(222,218,206);
    public static final int MILK = Color.rgb(255,254,250);
    public static final int WINE = Color.rgb(67,31,39);
    private Screen() {}

    public static LinearLayout start(Activity a,String title,String subtitle) {
        a.getWindow().setStatusBarColor(PAPER);
        a.getWindow().setNavigationBarColor(MILK);
        a.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        FrameLayout shell=new FrameLayout(a);
        shell.setBackgroundColor(PAPER);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,25),dp(a,29),dp(a,25),dp(a,115));
        scroll.addView(root);
        shell.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        a.setContentView(shell);

        LinearLayout masthead=new LinearLayout(a);
        masthead.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);
        mp.bottomMargin=dp(a,31);
        root.addView(masthead,mp);
        LinearLayout top=new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        masthead.addView(top);
        TextView mark=label(a,"M / B / M / F",12,true);
        mark.setTextColor(CLAY);
        mark.setLetterSpacing(.11f);
        mark.setPadding(0,0,0,0);
        top.addView(mark,new LinearLayout.LayoutParams(0,-2,1));
        TextView edition=label(a,"FINANCE   •   MOBILE",10,true);
        edition.setTextColor(MUTED);
        edition.setGravity(Gravity.END);
        edition.setPadding(0,0,0,0);
        top.addView(edition);
        TextView heading=label(a,title,35,true);
        heading.setTextColor(WINE);
        heading.setLetterSpacing(-.035f);
        heading.setPadding(0,dp(a,17),0,dp(a,5));
        masthead.addView(heading);
        TextView sub=label(a,subtitle,13,false);
        sub.setTextColor(MUTED);
        masthead.addView(sub);
        View line=new View(a);
        line.setBackgroundColor(INK);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,2));
        lp.topMargin=dp(a,19);
        masthead.addView(line,lp);
        return root;
    }

    /** No floating boxes or emoji icon bar: a quiet, fixed text-only navigation line. */
    public static void tabs(Activity a,LinearLayout root,String[] labels,LinearLayout[][] pages){
        if(labels==null||pages==null||labels.length!=pages.length||labels.length==0)
            throw new IllegalArgumentException("Invalid navigation structure");
        ScrollView scroll=(ScrollView)root.getParent();
        FrameLayout shell=(FrameLayout)scroll.getParent();
        LinearLayout footer=new LinearLayout(a);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(a,13),dp(a,3),dp(a,13),dp(a,5));
        footer.setBackgroundColor(MILK);
        FrameLayout.LayoutParams f=new FrameLayout.LayoutParams(-1,dp(a,73),Gravity.BOTTOM);
        shell.addView(footer,f);
        View divider=new View(a);
        divider.setBackgroundColor(LINE);
        FrameLayout.LayoutParams dl=new FrameLayout.LayoutParams(-1,dp(a,1),Gravity.BOTTOM);
        dl.bottomMargin=dp(a,73);
        shell.addView(divider,dl);

        TextView[] controls=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){
            final int index=i;
            TextView label=label(a,labels[i].toUpperCase(java.util.Locale.ROOT),
                labels.length>=5?10:11,true);
            label.setGravity(Gravity.CENTER);
            label.setLetterSpacing(.035f);
            label.setPadding(dp(a,2),0,dp(a,2),0);
            footer.addView(label,new LinearLayout.LayoutParams(0,-1,1f));
            controls[i]=label;
            label.setContentDescription(labels[i]);
            label.setOnClickListener(v->showTab(root,index));
        }
        root.setTag(new Navigation(a,scroll,pages,controls));
        showTab(root,0);
    }

    /** Navigation is callable by action buttons, not just the bottom labels. */
    public static void showTab(LinearLayout root,int index){
        Object object=root.getTag();
        if(!(object instanceof Navigation))return;
        Navigation n=(Navigation)object;
        if(index<0||index>=n.pages.length)return;
        for(int i=0;i<n.pages.length;i++){
            boolean selected=(i==index);
            n.controls[i].setTextColor(selected?CLAY:MUTED);
            n.controls[i].setTypeface(Typeface.create("sans-serif",selected?Typeface.BOLD:Typeface.NORMAL));
            for(LinearLayout page:n.pages[i])if(page!=null)
                page.setVisibility(selected?View.VISIBLE:View.GONE);
        }
        n.scroll.smoothScrollTo(0,0);
    }
    private static final class Navigation{
        final Activity activity;
        final ScrollView scroll;
        final LinearLayout[][] pages;
        final TextView[] controls;
        Navigation(Activity a,ScrollView s,LinearLayout[][] p,TextView[] c){
            activity=a;scroll=s;pages=p;controls=c;
        }
    }

    public static TextView label(Activity a,String text,int size,boolean bold){
        TextView t=new TextView(a);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(INK);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setPadding(0,dp(a,5),0,dp(a,7));
        return t;
    }

    public static EditText field(Activity a,LinearLayout parent,String hint,boolean secret){
        LinearLayout entry=new LinearLayout(a);
        entry.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);
        ep.bottomMargin=dp(a,18);
        parent.addView(entry,ep);
        TextView name=label(a,hint.toUpperCase(java.util.Locale.ROOT),11,true);
        name.setTextColor(MUTED);
        name.setLetterSpacing(.055f);
        name.setPadding(0,0,0,dp(a,6));
        entry.addView(name);
        EditText edit=new EditText(a);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setPadding(dp(a,14),dp(a,11),dp(a,14),dp(a,11));
        edit.setBackground(shape(a,MILK,LINE,4));
        edit.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        entry.addView(edit,new LinearLayout.LayoutParams(-1,dp(a,51)));
        return edit;
    }

    public static Button button(Activity a,LinearLayout parent,String text,View.OnClickListener action){
        Button button=new Button(a);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        button.setBackground(shape(a,INK,INK,4));
        button.setMinHeight(dp(a,54));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,54));
        p.bottomMargin=dp(a,10);
        parent.addView(button,p);
        button.setOnClickListener(action);
        return button;
    }

    /**
     * This intentionally is NOT a card. It is a section in an editorial document,
     * separated by a rule and whitespace. No shadows, tiles, floating rounded panels.
     */
    public static LinearLayout card(Activity a,LinearLayout parent,String heading){
        LinearLayout section=new LinearLayout(a);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(a,25);
        parent.addView(section,p);
        View rule=new View(a);rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,1));
        rp.bottomMargin=dp(a,15);
        section.addView(rule,rp);
        if(heading!=null&&!heading.isEmpty()){
            TextView title=label(a,heading,19,true);
            title.setPadding(0,0,0,dp(a,16));
            section.addView(title);
        }
        return section;
    }

    /** Unboxed compact figures instead of a dashboard of colorful metric cards. */
    public static TextView metric(Activity a,LinearLayout parent,String title){
        LinearLayout column=new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        p.bottomMargin=dp(a,18);
        parent.addView(column,p);
        TextView name=label(a,title,12,false);
        name.setTextColor(MUTED);
        column.addView(name);
        TextView value=label(a,"—",29,true);
        value.setTextColor(WINE);
        column.addView(value);
        return value;
    }

    /** Plain native action rows; no blue icons, cards, or decorative dashboard tiles. */
    public static void action(Activity a,LinearLayout parent,String heading,String description,View.OnClickListener click){
        LinearLayout line=new LinearLayout(a);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0,dp(a,10),0,dp(a,14));
        parent.addView(line,new LinearLayout.LayoutParams(-1,-2));
        TextView label=label(a,heading,18,true);
        label.setTextColor(INK);
        label.setPadding(0,0,0,dp(a,5));
        line.addView(label);
        TextView supporting=label(a,description,12,false);
        supporting.setTextColor(MUTED);
        line.addView(supporting);
        View divider=new View(a);divider.setBackgroundColor(LINE);
        line.addView(divider,new LinearLayout.LayoutParams(-1,dp(a,1)));
        line.setOnClickListener(click);
        line.setFocusable(true);
        line.setContentDescription(heading);
    }

    public static void info(Activity a,LinearLayout parent,String title,String value){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(a,9),0,dp(a,9));
        parent.addView(row);
        TextView label=label(a,title,13,false);label.setTextColor(MUTED);
        row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView val=label(a,value,13,true);val.setGravity(Gravity.END);
        row.addView(val);
    }

    private static GradientDrawable shape(Activity a,int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(a,radius));
        d.setStroke(dp(a,1),stroke);
        return d;
    }
    private static int dp(Activity a,int units){
        return Math.round(units*a.getResources().getDisplayMetrics().density);
    }
}
