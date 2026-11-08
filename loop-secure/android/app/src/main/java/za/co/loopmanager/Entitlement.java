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
    static Entitlement accept(Context c,Store s,JSONObject envelope) throws Exception {return accept(c,s,envelope,SystemClock.elapsedRealtime());}
    static synchronized Entitlement accept(Context c,Store s,JSONObject envelope,long requestElapsed) throws Exception {
        JSONObject d=verify(s,envelope);String saved=s.get("entitlement");
        if(!saved.isEmpty()){
            JSONObject old=verify(s,new JSONObject(saved));
            if(d.getLong("revision")<old.getLong("revision")||d.getLong("server_time")<old.getLong("server_time")||(old.getBoolean("released")&&!d.getBoolean("released")))throw new SecurityException("Old authorization rejected");
        }
        long nowElapsed=SystemClock.elapsedRealtime(),elapsed=Math.min(nowElapsed,Math.max(0,requestElapsed)),boot=boot(c);
        String previous=s.get("cached_state");if(!previous.isEmpty()){JSONObject prior=new JSONObject(previous),priorData=verify(s,prior.getJSONObject("envelope"));if(d.optLong("revision")<priorData.optLong("revision")||d.optLong("server_time")<priorData.optLong("server_time"))throw new SecurityException("Old cached authorization rejected");if(prior.optLong("boot",-1)==boot&&boot>=0&&prior.optLong("elapsed",-1)>=0&&prior.optLong("elapsed")<=nowElapsed&&d.optLong("revision")==priorData.optLong("revision")&&d.optLong("server_time")==priorData.optLong("server_time"))elapsed=Math.min(elapsed,prior.getLong("elapsed"));}
        JSONObject cached=new JSONObject().put("envelope",envelope).put("elapsed",elapsed).put("boot",boot);
        // One durable write keeps the signature and monotonic anchor together.
        s.put("cached_state",cached.toString());s.put("entitlement",envelope.toString());
        Entitlement accepted=new Entitlement(d,elapsed,boot);LocalDeadline.schedule(c,accepted);return accepted;
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
    boolean enforcementEnabled(){JSONObject p=data.optJSONObject("policy");return !data.optBoolean("released")&&p!=null&&p.optBoolean("enforcement_enabled");}
    long temporaryUntil(){JSONObject p=data.optJSONObject("policy");if(p==null)return 0;long start=p.optLong("temporary_unlock_started"),end=p.optLong("temporary_unlock_until");return start>0&&end-start==300?end:0;}
    long temporarySecondsLeft(Context c){long now=serverNow(c),end=temporaryUntil();JSONObject p=data.optJSONObject("policy");long start=p==null?0:p.optLong("temporary_unlock_started");return now<0||start>data.optLong("server_time")||now<start||end<=now?0:Math.min(300,end-now);}
    boolean manualLock(){JSONObject p=data.optJSONObject("policy");return !data.optBoolean("released")&&p!=null&&p.optBoolean("manual_lock",false);}
    boolean shouldRestrict(Context c){return enforcementEnabled()&&temporarySecondsLeft(c)==0&&(manualLock()||!clockKnown(c)||expired(c));}
    boolean expired(Context c){return stage(c)==AccountTiming.Stage.OVERDUE;}
    long restrictionDue(){return Math.max(AccountTiming.restrictionDue(data.optLong("due"),data.optJSONObject("policy").optLong("grace_hours",24)),data.optJSONObject("policy").optLong("grace_until",0));}
}
