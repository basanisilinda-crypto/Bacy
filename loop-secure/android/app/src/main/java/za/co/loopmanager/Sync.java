package za.co.loopmanager;

import android.content.Context;
import org.json.JSONObject;

final class Sync {
    static synchronized Entitlement run(Context c) throws Exception {
        Store store=new Store(c);if(store.get("device_token").isEmpty())return null;
        Entitlement previous=Entitlement.load(c,store);
        String previousStatus=store.get("last_status");if(previousStatus.isEmpty())previousStatus="needs_online_check";
        JSONObject request=new JSONObject().put("revision",previous==null?0:previous.data.optLong("revision")).put("status",previousStatus);
        request.put("app_version",version(c));
        long requestedAt=android.os.SystemClock.elapsedRealtime();JSONObject envelope=new Api(store).post("/api/device/sync",request,true);
        Entitlement accepted=Entitlement.accept(c,store,envelope,requestedAt);
        PaymentReminders.check(c,accepted);
        try{Inbox.receive(c,accepted);}catch(Exception delayedReceipt){/* durable signed inbox is retried on the next sync */}
        return accepted;
    }
    static JSONObject version(Context c)throws Exception {var p=c.getSharedPreferences("loop.updates",0);return new JSONObject().put("code",BuildConfig.VERSION_CODE).put("name",BuildConfig.VERSION_NAME).put("update_state",p.getString("stage","idle")).put("progress",p.getInt("progress",0));}
    static void report(Context c,Entitlement e,String status) throws Exception {
        Store s=new Store(c);s.put("last_status",status);
        if(e!=null)new Api(s).post("/api/device/sync",new JSONObject().put("revision",e.data.getLong("revision")).put("status",status).put("app_version",version(c)),true);
    }
}
