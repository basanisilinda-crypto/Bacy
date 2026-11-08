package za.co.loopmanager;
import android.content.*;import android.os.*;import android.view.WindowManager;import org.json.JSONObject;
/** Restores an already-confirmed restriction; never invents a new deadline from wall-clock time. */
final class RestrictionStartup {
 static void remember(Context c,JSONObject d,boolean restricted){var p=c.getSharedPreferences("loop.restriction",0);if(!restricted){p.edit().clear().apply();return;}p.edit().putString("device",d.optString("device_id")).putLong("revision",d.optLong("revision")).putLong("due",d.optLong("due")).putBoolean("restricted",true).apply();}
 static boolean wasRestricted(Context c,JSONObject d){var p=c.getSharedPreferences("loop.restriction",0);return p.getBoolean("restricted",false)&&p.getString("device","").equals(d.optString("device_id"))&&p.getLong("revision",-1)==d.optLong("revision")&&p.getLong("due",-1)==d.optLong("due")&&!d.optBoolean("released");}
 static void resume(Context c,boolean payment){try{if(!PolicyController.owns(c))return;Entitlement e=Entitlement.load(c,new Store(c));if(e==null)return;PolicyController.apply(c,e);if(!CheckoutAccess.inProgress()&&!SupportAccess.active(c)&&(e.shouldRestrict(c)||wasRestricted(c,e.data))&&e.data.getJSONObject("policy").optBoolean("enforcement_enabled")){c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_payment_options",payment));}}catch(Exception unavailable){/* Retry through the existing management service; no account or credential is removed. */}}
}
