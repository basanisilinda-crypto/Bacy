package za.co.mdmtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Consent-based offline MDM test app. No remote commands, payments, or tracking. */
public class MainActivity extends Activity {
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private TextView status;
    private EditText packageName;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dpm = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 30, 28, 30);
        layout.setBackgroundColor(Color.rgb(247,248,250));
        addText(layout, "MDM TEST", 27);
        addText(layout, "Offline device administration demonstration. Only use on phones you own or are authorized to manage.", 15);
        status = addText(layout, "", 16);
        addButton(layout, "1. Allow screen locking", v -> {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This demo can lock the screen when you tap its button.");
            startActivity(intent);
        });
        addButton(layout, "2. Lock my screen", v -> {
            if (!dpm.isAdminActive(admin)) {
                message("Activate screen locking first."); return;
            }
            try { dpm.lockNow(); }
            catch (Exception e) { message("Android refused to lock: " + e.getMessage()); }
        });
        addText(layout, "App restrictions — Device Owner only", 20);
        addText(layout, "Enter a nonessential installed application's package ID, e.g. com.whatsapp. Restrict hides it; restore makes it available again. Never test on your only important phone.", 14);
        packageName = new EditText(this);
        packageName.setSingleLine(true);
        packageName.setHint("com.whatsapp");
        packageName.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(packageName);
        addButton(layout, "Restrict selected app", v -> {
            String pkg = validTarget();
            if (pkg == null) return;
            new AlertDialog.Builder(this)
                .setTitle("Confirm restriction")
                .setMessage("Temporarily hide " + pkg + "? You must be able to return here to restore it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restrict", (dialog, which) -> setHidden(pkg, true))
                .show();
        });
        addButton(layout, "Remove restriction", v -> {
            String pkg = validTarget(); if (pkg != null) setHidden(pkg, false);
        });
        addButton(layout, "Refresh status", v -> refresh());
        addText(layout, "This is not a financing lock. It has no network access or remote control. QR enrollment and payment management are NOT included.", 13);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(layout);
        setContentView(scroller);
        refresh();
    }

    private String validTarget() {
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            message("App restrictions need Device Owner enrollment. Ordinary admin access only allows screen locking.");
            return null;
        }
        String pkg = packageName.getText().toString().trim();
        if (!pkg.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")) {
            message("Enter a valid installed application package ID."); return null;
        }
        if (pkg.equals(getPackageName()) || pkg.equals("android") ||
            pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.") ||
            pkg.startsWith("com.hisense.") || pkg.contains("launcher") ||
            pkg.contains("systemui") || pkg.contains("settings")) {
            message("For safety, the demo blocks its own package and common system applications."); return null;
        }
        return pkg;
    }

    private void setHidden(String pkg, boolean hide) {
        try {
            boolean ok = dpm.setApplicationHidden(admin, pkg, hide);
            message(ok ? (hide ? "Restricted: " : "Restored: ") + pkg
                    : "Not changed. Confirm the app is installed and your phone supports this policy.");
        } catch (Exception e) { message("Restriction failed: " + e.getMessage()); }
    }

    private TextView addText(LinearLayout layout, String txt, int size) {
        TextView view = new TextView(this);
        view.setText(txt); view.setTextSize(size);
        view.setTextColor(Color.rgb(30,40,54));
        view.setPadding(0, 9, 0, 15);
        layout.addView(view); return view;
    }
    private void addButton(LinearLayout layout, String label, View.OnClickListener onClick) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(onClick); layout.addView(b);
    }
    private void message(String txt) {
        new AlertDialog.Builder(this).setTitle("MDM Test").setMessage(txt)
            .setPositiveButton("OK", null).show();
        refresh();
    }
    private void refresh() {
        status.setText("Screen-lock admin: " + (dpm.isAdminActive(admin) ? "ON" : "OFF")
            + "\nDevice Owner: " + (dpm.isDeviceOwnerApp(getPackageName()) ? "ON" : "OFF"));
    }
    @Override protected void onResume() { super.onResume(); if (status != null) refresh(); }
}
