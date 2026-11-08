package za.co.loopmanager;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String action=i.getAction();if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)||!c.getSystemService(android.os.UserManager.class).isUserUnlocked()){try{StartupProtection.lockedBoot(c);}catch(RuntimeException unavailable){}return;}if(Intent.ACTION_USER_UNLOCKED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){StartupProtection.recovering(c);RestrictionStartup.resume(c,true);ManagedUpdates.schedule(c);ManagedUpdates.checkSoon(c);SyncJobService.schedule(c);SyncJobService.recoverSoon(c);LiveConnectionService.start(c);}}
}
