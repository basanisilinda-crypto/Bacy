package za.co.loopmanager;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import org.json.JSONObject;

final class Entitlement {
    final JSONObject data;
    final long anchorElapsed,boot;
    Entitlement(JSONObject d,long elapsed,long b){data=d;anchorElapsed=elapsed;boot=b;}
    static long boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static JSONObject verify(Store s,JSONObject envelope) throws Exception {
        byte[] raw=Base64.decode(envelope.getString("payload"),Base64.NO_WRAP);
        byte[] pub=Base64.decode(s.get("public_key"),Base64.NO_WRAP);
        AccountSignatures.verify(pub,Base64.decode(envelope.getString("signature"),Base64.NO_WRAP),raw);
        JSONObject d=new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8));
        if(!d.getString("device_id").equals(s.get("device_id")))throw new SecurityException("Authorization belongs to another phone");
        return d;
    }
    static Entitlement accept(Context c,Store s,JSONObject envelope) throws Exception {
        JSONObject d=verify(s,envelope);String saved=s.get("entitlement");
        if(!saved.isEmpty()){
            JSONObject old=verify(s,new JSONObject(saved));
            if(d.getLong("revision")<old.getLong("revision")||d.getLong("server_time")<old.getLong("server_time")||(old.getBoolean("released")&&!d.getBoolean("released")))throw new SecurityException("Old authorization rejected");
        }
        long elapsed=SystemClock.elapsedRealtime(),boot=boot(c);
        JSONObject cached=new JSONObject().put("envelope",envelope).put("elapsed",elapsed).put("boot",boot);
        // One durable write keeps the signature and monotonic anchor together.
        s.put("cached_state",cached.toString());s.put("entitlement",envelope.toString());
        return new Entitlement(d,elapsed,boot);
    }
    static Entitlement load(Context c,Store s) throws Exception {
        String raw=s.get("cached_state");if(raw.isEmpty())return null;
        JSONObject saved=new JSONObject(raw);
        return new Entitlement(verify(s,saved.getJSONObject("envelope")),saved.getLong("elapsed"),saved.getLong("boot"));
    }
    boolean clockKnown(Context c){return boot>=0&&boot==boot(c)&&SystemClock.elapsedRealtime()>=anchorElapsed;}
    long serverNow(Context c){if(!clockKnown(c))return -1;return data.optLong("server_time")+(SystemClock.elapsedRealtime()-anchorElapsed)/1000;}
    long secondsLeft(Context c){long current=serverNow(c);return current<0?-1:Math.max(0,data.optLong("due")-current);}
    AccountTiming.Stage stage(Context c){return AccountTiming.stageWithDeadline(serverNow(c),data.optLong("due"),restrictionDue(),data.optBoolean("released"));}
    boolean manualLock(){return !data.optBoolean("released")&&data.optJSONObject("policy").optBoolean("manual_lock",false);}
    boolean shouldRestrict(Context c){return !data.optBoolean("released")&&data.optJSONObject("policy").optBoolean("enforcement_enabled")&&(manualLock()||expired(c));}
    boolean expired(Context c){return stage(c)==AccountTiming.Stage.OVERDUE;}
    long restrictionDue(){return Math.max(AccountTiming.restrictionDue(data.optLong("due"),data.optJSONObject("policy").optLong("grace_hours",24)),data.optJSONObject("policy").optLong("grace_until",0));}
}
