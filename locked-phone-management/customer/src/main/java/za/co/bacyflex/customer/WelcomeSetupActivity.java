package za.co.bacyflex.customer;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * First-run MBMF walkthrough, native Android Views with no WebView.
 *
 * Device Administrator can be requested only through an explicit Android
 * permission screen. It is not Device Owner. Device Owner is established
 * by Android during compatible QR provisioning at the initial Welcome screen.
 * The customer may decline Device Administrator permission.
 */
public final class WelcomeSetupActivity extends Activity {
    private static final int INK = Color.rgb(42, 40, 67);
    private static final int PURPLE = Color.rgb(105, 87, 188);
    private static final int LIGHT = Color.rgb(252, 251, 248);
    private static final int FAINT = Color.rgb(125, 123, 142);
    private static final int GREEN = Color.rgb(228, 248, 235);
    private static final int PINK = Color.rgb(251, 230, 235);
    private static final int BORDER = Color.rgb(230, 227, 237);

    private LinearLayout pageBody, dots;
    private TextView section, title, description, permissionStatus;
    private Button primary, permissionButton;
    private CheckBox consent;
    private SetupArtwork artwork;
    private int page = 0;
    private boolean openedFromApp;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openedFromApp = getIntent().getBooleanExtra("from_main", false);
        SharedPreferences prefs = getSharedPreferences("mbmf_welcome", MODE_PRIVATE);
        boolean requested = getIntent().getBooleanExtra("review_setup", false);
        if (!requested && prefs.getBoolean("completed", false)) {
            enterApp();
            return;
        }
        if (state != null) page = Math.max(0, Math.min(3, state.getInt("step", 0)));
        build();
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("step", page);
        super.onSaveInstanceState(state);
    }

    private boolean isOwner() {
        DevicePolicyManager d = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        return d != null && d.isDeviceOwnerApp(getPackageName());
    }

    private boolean isAdminActive() {
        DevicePolicyManager d = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        return d != null && d.isAdminActive(
            new ComponentName(this, FinanceAdminReceiver.class));
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private Button button(String label, boolean filled) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextColor(filled ? Color.WHITE : PURPLE);
        b.setBackground(background(filled ? PURPLE : GREEN, 18));
        return b;
    }

    private void build() {
        getWindow().setStatusBarColor(LIGHT);
        getWindow().setNavigationBarColor(LIGHT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(LIGHT);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(25), dp(26), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView monogram = text("m.", 26, true, PURPLE);
        brand.addView(monogram, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView small = text("MBMF  /  FIRST SETUP", 11, true, FAINT);
        small.setLetterSpacing(.14f);
        brand.addView(small);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(-1, dp(24));
        dotParams.topMargin = dp(26);
        root.addView(dots, dotParams);

        artwork = new SetupArtwork();
        LinearLayout.LayoutParams imageSize = new LinearLayout.LayoutParams(-1, dp(245));
        imageSize.topMargin = dp(14);
        root.addView(artwork, imageSize);

        pageBody = new LinearLayout(this);
        pageBody.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        sectionParams.topMargin = dp(17);
        root.addView(pageBody, sectionParams);

        section = text("", 11, true, PURPLE);
        section.setLetterSpacing(.12f);
        pageBody.addView(section);

        title = text("", 30, true, INK);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(12);
        pageBody.addView(title, headingParams);

        description = text("", 15, false, FAINT);
        description.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(13);
        pageBody.addView(description, bodyParams);

        permissionStatus = text("", 14, true, INK);
        permissionStatus.setPadding(dp(14), dp(14), dp(14), dp(14));
        permissionStatus.setBackground(background(GREEN, 14));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(18);
        pageBody.addView(permissionStatus, statusParams);

        consent = new CheckBox(this);
        consent.setText("I understand why Android may request device management access.");
        consent.setTextColor(INK);
        consent.setTextSize(13);
        consent.setPadding(0, dp(11), 0, dp(9));
        consent.setOnCheckedChangeListener((b, selected) -> updateButtons());
        pageBody.addView(consent);

        permissionButton = button("Review Android permission", false);
        LinearLayout.LayoutParams grantParams = new LinearLayout.LayoutParams(-1, dp(56));
        grantParams.topMargin = dp(9);
        pageBody.addView(permissionButton, grantParams);
        permissionButton.setOnClickListener(v -> requestDeviceAdmin());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, dp(67));
        bottomParams.topMargin = dp(18);
        root.addView(bottom, bottomParams);

        Button back = button("Back", false);
        bottom.addView(back, new LinearLayout.LayoutParams(0, dp(55), .32f));
        back.setOnClickListener(v -> {
            if (page > 0) { page--; render(); }
            else finish();
        });
        primary = button("Continue", true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(55), .68f);
        p.leftMargin = dp(10);
        bottom.addView(primary, p);
        primary.setOnClickListener(v -> {
            if (page < 3) { page++; render(); }
            else finishSetup();
        });
    }

    private void render() {
        if (pageBody == null) return;
        String[] kicker = {"WELCOME", "YOUR FINANCE", "ANDROID ACCESS", "ALL DONE"};
        String[] headings = {
            "Hello, new phone!",
            "Simple payments.\nClear progress.",
            "Your phone.\nYour choice.",
            "Ready when\nyou are."
        };
        String[] details = {
            "Welcome to MBMF. Follow your financed phone's payments without signing up or remembering a password.",
            "See your remaining balance, next instalment date and payment history. You send an EFT reference; your finance provider checks the payment.",
            "For a financed device, Android may offer management permissions described in your agreement. You can review the system permission screen before deciding.",
            "You can explore the app now. Your finance information appears after your provider assigns the phone through supported Android setup."
        };
        section.setText(kicker[page]);
        title.setText(headings[page]);
        description.setText(details[page]);
        artwork.setMode(page);
        dots.removeAllViews();
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            dot.setBackground(background(i == page ? PURPLE : BORDER, 7));
            LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(
                dp(i == page ? 25 : 9), dp(7));
            d.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(dot, d);
        }
        boolean permissionPage = page == 2;
        permissionStatus.setVisibility(page >= 2 ? View.VISIBLE : View.GONE);
        consent.setVisibility(permissionPage && !isOwner() && !isAdminActive() ?
            View.VISIBLE : View.GONE);
        permissionButton.setVisibility(permissionPage && !isOwner() && !isAdminActive() ?
            View.VISIBLE : View.GONE);
        if (page == 2) {
            if (isOwner()) {
                permissionStatus.setText("Device Owner is active. Android has already completed managed-device provisioning.");
            } else if (isAdminActive()) {
                permissionStatus.setText("Device Administrator is active. This is limited access, not Device Owner.");
            } else {
                permissionStatus.setText("No device management permission yet. You may review Android's optional Device Administrator screen.");
            }
        }
        if (page == 3) {
            permissionStatus.setText(isOwner()
                ? "Managed-device setup: Device Owner active"
                : isAdminActive()
                    ? "Limited setup: Device Administrator active. Full Device Owner needs Android startup QR."
                    : "No device management activated. Full enrolment requires a compatible phone's Android startup QR.");
        }
        updateButtons();
    }

    private void updateButtons() {
        if (permissionButton != null) {
            boolean enabled = consent != null && consent.isChecked();
            permissionButton.setEnabled(enabled);
            permissionButton.setAlpha(enabled ? 1f : .50f);
        }
        if (primary != null) primary.setText(page == 3 ? "Open MBMF" : "Continue");
    }

    private void requestDeviceAdmin() {
        if (isOwner() || isAdminActive()) {
            render();
            return;
        }
        if (!consent.isChecked()) return;
        Intent request = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        request.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            new ComponentName(this, FinanceAdminReceiver.class));
        request.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "MBMF requests optional Device Administrator permission to allow an " +
            "explicit screen-lock action on this financed phone. This does not " +
            "grant Device Owner status. Full management requires supported " +
            "Android setup QR provisioning.");
        try {
            startActivityForResult(request, 607);
        } catch (Exception e) {
            permissionStatus.setText(
                "This phone did not open the Android permission screen. Ask your provider about supported setup.");
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == 607) render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pageBody != null && page >= 2) render();
    }

    private void finishSetup() {
        getSharedPreferences("mbmf_welcome", MODE_PRIVATE)
            .edit().putBoolean("completed", true).apply();
        enterApp();
    }

    private void enterApp() {
        if (!openedFromApp) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    @Override public void onBackPressed() {
        if (page > 0) { page--; render(); }
        else super.onBackPressed();
    }

    /** Lightweight native drawing; no external graphics, animations or WebView. */
    private final class SetupArtwork extends View {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mode;
        SetupArtwork() { super(WelcomeSetupActivity.this); }
        void setMode(int m) { mode = m; invalidate(); }
        private void fill(Canvas c, int color, float left, float top,
                          float right, float bottom, float radius) {
            pen.setColor(color);
            pen.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, pen);
        }
        private void circle(Canvas c, int color, float x, float y, float r) {
            pen.setColor(color);
            pen.setStyle(Paint.Style.FILL);
            c.drawCircle(x,y,r,pen);
        }
        @Override protected void onDraw(Canvas raw) {
            super.onDraw(raw);
            float scale=Math.min(getWidth()/340f,getHeight()/240f);
            raw.save();
            raw.translate((getWidth()-340*scale)/2f,(getHeight()-240*scale)/2f);
            raw.scale(scale,scale);
            fill(raw,mode%2==0?GREEN:PINK,18,12,322,233,43);
            circle(raw, Color.rgb(254,197,191),55,54,18);
            circle(raw,Color.rgb(185,225,211),278,196,29);
            fill(raw,Color.rgb(46,43,71),111,23,229,227,25);
            fill(raw,Color.WHITE,117,30,223,219,21);
            fill(raw,Color.rgb(234,233,246),150,38,190,44,4);
            if (mode==0) {
                circle(raw,PURPLE,170,100,34);
                fill(raw,Color.WHITE,159,87,181,116,8);
                fill(raw,Color.rgb(223,246,231),132,151,207,173,12);
                fill(raw,PURPLE,143,184,197,190,4);
            } else if (mode==1) {
                fill(raw,Color.rgb(239,237,250),129,70,211,119,13);
                pen.setColor(PURPLE);pen.setStrokeWidth(7);pen.setStrokeCap(Paint.Cap.ROUND);
                raw.drawLine(141,100,159,85,pen);
                raw.drawLine(159,85,174,96,pen);
                raw.drawLine(174,96,197,77,pen);
                fill(raw,GREEN,130,138,210,175,11);
                fill(raw,Color.rgb(120,203,156),144,150,193,158,4);
            } else if (mode==2) {
                fill(raw,Color.rgb(233,229,248),129,76,211,168,20);
                fill(raw,PURPLE,144,103,196,150,13);
                pen.setColor(Color.WHITE);
                pen.setStrokeWidth(6);pen.setStrokeCap(Paint.Cap.ROUND);
                raw.drawLine(153,126,165,138,pen);
                raw.drawLine(165,138,186,114,pen);
            } else {
                circle(raw,Color.rgb(218,244,227),170,116,43);
                pen.setColor(PURPLE);pen.setStrokeWidth(9);pen.setStrokeCap(Paint.Cap.ROUND);
                raw.drawLine(149,118,163,132,pen);
                raw.drawLine(163,132,192,98,pen);
                fill(raw,Color.rgb(246,225,235),134,178,207,193,7);
            }
            raw.restore();
        }
    }
}
