package za.co.loopmanager;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.provider.Telephony;
import java.util.Collections;
import java.util.List;

/** Temporary access to the genuine default SMS app; never reads or uploads messages. */
final class CheckoutAccess {
 private static long until;private static int owners;
 static synchronized void begin(){owners++;until=SystemClock.elapsedRealtime()+10*60*1000L;}
 static synchronized void end(){owners=Math.max(0,owners-1);if(owners==0)until=0;}
 static synchronized boolean inProgress(){return owners>0;}
 static synchronized boolean active(){return owners>0&&SystemClock.elapsedRealtime()<until;}
 private static boolean known(String p){return java.util.Arrays.asList("com.google.android.apps.messaging","com.android.mms","com.android.messaging","com.samsung.android.messaging","com.sonyericsson.conversations","com.huawei.message","com.hihonor.mms").contains(p);}
 static String messages(Context c){
  String p=Telephony.Sms.getDefaultSmsPackage(c);if(p==null)return null;
  try{ApplicationInfo info=c.getPackageManager().getApplicationInfo(p,0);return info.enabled&&(((info.flags&ApplicationInfo.FLAG_SYSTEM)!=0)||known(p))&&c.getPackageManager().getLaunchIntentForPackage(p)!=null?p:null;}catch(Exception unavailable){return null;}
 }
 static List<String> packages(Context c){String p=active()?messages(c):null;return p==null?Collections.emptyList():Collections.singletonList(p);}
 static void openMessages(Activity activity,org.json.JSONObject policy){
  String p=messages(activity);if(p==null)throw new IllegalStateException("The phone's default Messages app is unavailable. Ask support to check the default SMS app.");
  synchronized(CheckoutAccess.class){if(owners<1)throw new IllegalStateException("Open your payment checkout first.");until=SystemClock.elapsedRealtime()+10*60*1000L;}
  Intent intent=activity.getPackageManager().getLaunchIntentForPackage(p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  boolean locked=PolicyController.owns(activity)&&activity.getSystemService(ActivityManager.class).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
  if(locked){BankApps.allowForLock(activity,policy);ActivityOptions options=ActivityOptions.makeBasic();options.setLockTaskEnabled(true);activity.startActivity(intent,options.toBundle());}else activity.startActivity(intent);
 }
}
