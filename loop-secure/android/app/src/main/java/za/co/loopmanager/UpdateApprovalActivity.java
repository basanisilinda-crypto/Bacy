package za.co.loopmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

/** Explicit user entry point for an Android-owned install confirmation, never a web URL. */
public final class UpdateApprovalActivity extends Activity {
    static final String ACTION="za.co.loopmanager.APPROVE_UPDATE";
    static boolean trustedConfirmation(Context c,Intent intent){
        if(intent==null||!"android.content.pm.action.CONFIRM_INSTALL".equals(intent.getAction()))return false;
        ResolveInfo resolved=c.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
        return resolved!=null&&resolved.activityInfo!=null&&resolved.activityInfo.exported
            &&resolved.activityInfo.applicationInfo!=null
            &&(resolved.activityInfo.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            int expected=getSharedPreferences("loop.updates",0).getInt("session",-1);
            int requested=getIntent().getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2);
            PackageInstaller.SessionInfo info=getPackageManager().getPackageInstaller().getSessionInfo(expected);
            Intent confirmation=getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
            if(!PolicyController.owns(this)||expected<0||requested!=expected||info==null
                ||!getPackageName().equals(info.getAppPackageName())||!trustedConfirmation(this,confirmation)
                ||confirmation.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=expected)return;
            ResolveInfo resolved=getPackageManager().resolveActivity(confirmation,PackageManager.MATCH_DEFAULT_ONLY);
            startActivity(new Intent(confirmation).setPackage(resolved.activityInfo.packageName));
        }catch(Exception unavailable){ManagedUpdates.status(this,"ready","Android approval is unavailable. Retry from App updates.",100);}
        finally{finish();}
    }
}
