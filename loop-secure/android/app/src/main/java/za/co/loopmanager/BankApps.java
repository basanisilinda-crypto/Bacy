package za.co.loopmanager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** One exact-package bank allowlist for launching, managed locking and diagnostics. */
final class BankApps {
    private static final List<String> DEFAULTS=Arrays.asList(
        "capitec.acuity.mobile.prod", "za.co.gotyme", "consumer.cbsa.smartapp",
        "za.co.fnb.connect.itt", "com.sbg.mobile.phone", "za.co.nedbank",
        "com.barclays.absa.banking", "za.co.android.africanbank",
        "bank.discovery.banking.production.release");
    static final class Choice {
        final String packageName,label; final Intent intent;
        Choice(String name,String text,Intent launch){packageName=name;label=text;intent=launch;}
    }
    static List<String> approved(JSONObject policy){
        LinkedHashSet<String> names=new LinkedHashSet<>(DEFAULTS);
        JSONArray configured=policy==null?null:policy.optJSONArray("banking_packages");
        if(configured!=null)for(int i=0;i<configured.length();i++){
            String name=configured.optString(i);
            if(name.matches("[A-Za-z]\\w*(\\.[A-Za-z]\\w*)+"))names.add(name);
        }
        return new ArrayList<>(names);
    }
    static List<Choice> installed(Context context,JSONObject policy){
        ArrayList<Choice> result=new ArrayList<>();PackageManager packages=context.getPackageManager();
        for(String name:approved(policy)){
            Intent launch=packages.getLaunchIntentForPackage(name);if(launch==null)continue;
            try{result.add(new Choice(name,packages.getApplicationLabel(packages.getApplicationInfo(name,0)).toString(),launch));}
            catch(PackageManager.NameNotFoundException unavailable){/* Recheck on the next open. */}
        }
        String recent=context.getSharedPreferences("loop.ui",0).getString("last_bank","");result.sort(java.util.Comparator.comparingInt(choice->choice.packageName.equals(recent)?0:1));return result;
    }
    static void allowForLock(Context context,JSONObject policy){
        if(!PolicyController.owns(context))return;
        LinkedHashSet<String> packages=new LinkedHashSet<>();packages.add(context.getPackageName());packages.addAll(approved(policy));
        packages.addAll(DeviceSettings.packages(context));
        packages.addAll(CheckoutAccess.packages(context));
        TelecomManager telecom=context.getSystemService(TelecomManager.class);
        if(telecom!=null&&telecom.getDefaultDialerPackage()!=null)packages.add(telecom.getDefaultDialerPackage());
        context.getSystemService(DevicePolicyManager.class).setLockTaskPackages(PolicyController.admin(context),packages.toArray(new String[0]));
    }
    static void open(Activity activity,Choice bank,JSONObject policy){
        Intent launch=new Intent(bank.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean locked=PolicyController.owns(activity)&&activity.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
        if(locked)allowForLock(activity,policy);
        if(locked){ActivityOptions options=ActivityOptions.makeBasic();options.setLockTaskEnabled(true);activity.startActivity(launch,options.toBundle());}
        else activity.startActivity(launch);
        activity.getSharedPreferences("loop.ui",0).edit().putString("last_bank",bank.packageName).apply();
    }
}
