package za.co.loopmanager;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** A denied notification or storage failure must never interrupt policy enforcement. */
final class PaymentReminders {
    static synchronized void check(Context context,Entitlement entitlement){
        if(entitlement==null)return;
        try{
            PaymentReminderPlan plan=PaymentReminderPlan.forAccount(entitlement.serverNow(context),entitlement.data.optLong("due"),entitlement.data.optJSONObject("policy").optLong("grace_hours",24),entitlement.data.optBoolean("released"),Entitlement.boot(context),entitlement.data.optJSONObject("policy").optLong("grace_until",0));
            Store store=new Store(context);String previous=store.get("payment_reminder_key");
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            if(plan==null){if(!previous.isEmpty()){manager.cancel(30);store.put("payment_reminder_key","");}return;}
            if(!PolicyController.owns(context)||plan.key.equals(previous)||(android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)||!manager.areNotificationsEnabled())return;
            manager.createNotificationChannel(new NotificationChannel("payments","Payment reminders",NotificationManager.IMPORTANCE_DEFAULT));
            NotificationChannel channel=manager.getNotificationChannel("payments");if(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
            PendingIntent open=PendingIntent.getActivity(context,30,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            manager.notify(30,new Notification.Builder(context,"payments").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(plan.title).setContentText(plan.message).setStyle(new Notification.BigTextStyle().bigText(plan.message)).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build());
            store.put("payment_reminder_key",plan.key);
        }catch(Exception unavailable){/* Retry on the next check; policy and payment state are unchanged. */}
    }
}
