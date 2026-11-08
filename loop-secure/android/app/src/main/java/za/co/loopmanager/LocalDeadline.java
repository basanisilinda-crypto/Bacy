package za.co.loopmanager;
import android.app.AlarmManager;import android.app.PendingIntent;import android.content.*;import android.os.SystemClock;
/** Offline wake-up uses elapsed realtime, never the editable wall clock or a network request. */
final class LocalDeadline {
 static final String ACTION="za.co.loopmanager.LOCAL_DEADLINE";
 private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,435,new Intent(c,DeadlineReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static synchronized void schedule(Context c,Entitlement e){try{
  AlarmManager manager=c.getSystemService(AlarmManager.class);var prefs=c.getSharedPreferences("loop.deadline",0);
  if(e==null||e.data.optBoolean("released")||!PolicyController.owns(c)||!e.enforcementEnabled()||!e.clockKnown(c)){manager.cancel(alarm(c));prefs.edit().clear().apply();return;}
  long now=e.serverNow(c),deadline=e.temporarySecondsLeft(c)>0?e.temporaryUntil():e.manualLock()?now:e.restrictionDue();
  if(deadline<=now){manager.cancel(alarm(c));prefs.edit().clear().apply();return;}
  long trigger=e.anchorElapsed+Math.max(0,deadline-e.data.optLong("server_time"))*1000;
  String key=e.data.optString("device_id")+":"+e.boot+":"+deadline;
  if(key.equals(prefs.getString("key",""))&&prefs.getLong("trigger",-1)==trigger)return;
  PendingIntent pending=alarm(c);boolean exact=android.os.Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms();
  try{if(exact)manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,Math.max(SystemClock.elapsedRealtime()+1,trigger),pending);else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,Math.max(SystemClock.elapsedRealtime()+1,trigger),pending);}catch(SecurityException denied){exact=false;manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,Math.max(SystemClock.elapsedRealtime()+1,trigger),pending);}
  prefs.edit().putString("key",key).putLong("trigger",trigger).putBoolean("exact",exact).apply();
 }catch(RuntimeException unavailable){/* Foreground local loop and screen-on enforcement still apply. */}}
}
