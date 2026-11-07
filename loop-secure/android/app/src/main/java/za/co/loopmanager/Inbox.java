package za.co.loopmanager;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;

final class Inbox {
    static synchronized void receive(Context c,Entitlement e) throws Exception {
        JSONArray messages=e.data.optJSONArray("messages");if(messages==null)return;
        Store store=new Store(c);JSONArray delivered=new JSONArray();
        java.util.TreeMap<Long,JSONObject> saved=new java.util.TreeMap<>();
        String previous=store.get("inbox");JSONArray history=previous.isEmpty()?new JSONArray():new JSONArray(previous);
        for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);saved.put(m.getLong("id"),m);}
        for(int i=0;i<messages.length();i++){
            JSONObject m=messages.getJSONObject(i);JSONObject older=saved.get(m.getLong("id"));
            if(older!=null&&!older.isNull("read_at")&&m.isNull("read_at"))m.put("read_at",older.get("read_at"));
            saved.put(m.getLong("id"),m);
        }
        while(saved.size()>1000)saved.pollFirstEntry();
        JSONArray merged=new JSONArray();for(JSONObject m:saved.descendingMap().values())merged.put(m);
        store.put("inbox",merged.toString());
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("messages","Messages from your administrator",NotificationManager.IMPORTANCE_DEFAULT));
        for(int i=0;i<messages.length();i++){
            JSONObject m=messages.getJSONObject(i);long id=m.getLong("id");
            if(m.isNull("delivered"))delivered.put(id);
            if(m.isNull("read_at")&&store.get("notified_"+id).isEmpty()&&(android.os.Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){
                Intent intent=new Intent(c,MainActivity.class).putExtra("open_messages",true);
                PendingIntent open=PendingIntent.getActivity(c,(int)id,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                nm.notify((int)(1000+id),new Notification.Builder(c,"messages").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("New message from Loop").setContentText("Open your account to read your administrator's message.").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build());
                store.put("notified_"+id,"1");
            }
        }
        // The signed inbox was durably stored before we acknowledge receipt.
        if(delivered.length()>0)new Api(store).post("/api/device/messages/receipt",new JSONObject().put("ids",delivered).put("state","delivered"),true);
    }
}
