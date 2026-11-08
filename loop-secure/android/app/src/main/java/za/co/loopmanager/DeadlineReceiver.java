package za.co.loopmanager;
import android.content.*;
/** Android delivers this private alarm even without internet; policy runs before any cloud work. */
public final class DeadlineReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){if(!LocalDeadline.ACTION.equals(intent.getAction()))return;c.getSharedPreferences("loop.deadline",0).edit().clear().apply();RestrictionStartup.resume(c,true);c.sendBroadcast(new Intent(LiveConnectionService.UPDATED).setPackage(c.getPackageName()),c.getPackageName()+".permission.INTERNAL");LiveConnectionService.start(c);}
}
