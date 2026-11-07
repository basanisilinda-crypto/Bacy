package za.co.loopmanager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){RestrictionStartup.resume(c,true);ManagedUpdates.schedule(c);ManagedUpdates.checkSoon(c);SyncJobService.schedule(c);SyncJobService.recoverSoon(c);LiveConnectionService.start(c);}}
}
