package za.co.loopmanager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
public final class UpdateReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){if(!"za.co.loopmanager.UPDATE_RESULT".equals(i.getAction()))return;int expected=c.getSharedPreferences("loop.updates",0).getInt("session",-1);if(expected<0||i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=expected)return;int result=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);c.getSharedPreferences("loop.updates",0).edit().remove("session").apply();if(result==PackageInstaller.STATUS_SUCCESS){UpdateGate.clear(c);ManagedUpdates.status(c,"current","App update installed successfully.",100);ManagedUpdates.schedule(c);}else{UpdateGate.clear(c);try{c.getPackageManager().getPackageInstaller().abandonSession(expected);}catch(Exception ignored){}ManagedUpdates.status(c,result==PackageInstaller.STATUS_PENDING_USER_ACTION?"Android requires an administrator-assisted update on this handset. Your installed app and account are unchanged.":"Android could not install this update. The next Wi-Fi check will retry.");}}
}
