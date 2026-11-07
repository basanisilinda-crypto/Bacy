package za.co.loopmanager;
import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.SystemClock;

public final class UpdateReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(!"za.co.loopmanager.UPDATE_RESULT".equals(i.getAction()))return;
  var p=c.getSharedPreferences("loop.updates",0);int expected=p.getInt("session",-1);
  if(expected<0||i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=expected)return;
  int result=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  UpdateGate.clear(c);
  if(result==PackageInstaller.STATUS_PENDING_USER_ACTION){
   Intent confirmation=i.getParcelableExtra(Intent.EXTRA_INTENT);
   if(UpdateApprovalActivity.trustedConfirmation(c,confirmation)){
    p.edit().putBoolean("approval_required",true).apply();
    ManagedUpdates.status(c,"ready","Android needs installation approval. Tap the update notification or Install update.",100);
    Intent review=new Intent(c,UpdateApprovalActivity.class).setAction(UpdateApprovalActivity.ACTION)
      .putExtra(PackageInstaller.EXTRA_SESSION_ID,expected).putExtra(Intent.EXTRA_INTENT,confirmation);
    PendingIntent approve=PendingIntent.getActivity(c,expected,review,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    NotificationManager manager=c.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel("app-updates","App updates",NotificationManager.IMPORTANCE_DEFAULT));
    manager.notify(405,new Notification.Builder(c,"app-updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle("Approve Loop Secure update").setContentText("Android requires your approval to finish installation.")
      .setContentIntent(approve).setAutoCancel(false).build());
    return;
   }
  }
  p.edit().remove("session").remove("approval_required").apply();
  if(result==PackageInstaller.STATUS_SUCCESS){
   p.edit().remove("failed_version").remove("failed_elapsed").remove("failed_boot").apply();
   c.getSystemService(NotificationManager.class).cancel(405);
   ManagedUpdates.status(c,"current","App update installed successfully.",100);ManagedUpdates.schedule(c);
  }else{
   try{c.getPackageManager().getPackageInstaller().abandonSession(expected);}catch(Exception ignored){}
   p.edit().putLong("failed_version",p.getLong("candidate_version",-1)).putLong("failed_boot",Entitlement.boot(c)).putLong("failed_elapsed",SystemClock.elapsedRealtime()).apply();
   ManagedUpdates.status(c,"failed","Android could not install this update. Your existing app and account remain; automatic installation retries later.",0);
  }
 }
}
