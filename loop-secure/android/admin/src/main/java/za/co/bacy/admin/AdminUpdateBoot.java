package za.co.bacy.admin;
import android.content.*;
public final class AdminUpdateBoot extends BroadcastReceiver {@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))AdminUpdates.schedule(c);}}
