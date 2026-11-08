package za.co.bacy.ui;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Read-only views of already-authorized account records. Never credits a payment. */
public final class PaymentRecords {
 private PaymentRecords() {}
 public static boolean matches(JSONObject record, String state) {
  return record != null && ("all".equals(state) || state.equals(record.optString("state")));
 }
 public static List<JSONObject> ordered(JSONArray records, String state, boolean oldestFirst) {
  List<JSONObject> result = new ArrayList<>();
  if (records != null) for (int i=0; i<records.length(); i++) {
   JSONObject record=records.optJSONObject(i);
   if (matches(record,state)) result.add(record);
  }
  result.sort((a,b)->{
   long x=a.optLong("submitted"), y=b.optLong("submitted");
   if(x<=0 || y<=0) return x<=0 ? (y<=0 ? 0 : 1) : -1;
   return oldestFirst ? Long.compare(x,y) : Long.compare(y,x);
  });
  return result;
 }
 public static String label(String state) {
  return switch(state) { case "approved" -> "Received"; case "pending" -> "In review";
   case "rejected" -> "Not approved"; default -> "Recorded"; };
 }
 private static String money(JSONObject data,String key) {
  return data!=null && data.has(key) && !data.isNull(key) && data.optLong(key,-1)>=0
   ? "R"+data.optLong(key) : "Not available";
 }
 private static String when(long seconds) {
  return seconds>0 && seconds<=253402300799L
   ? DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(seconds*1000))
   : "Not recorded";
 }
 public static String statement(JSONObject account) {
  JSONObject summary=account.optJSONObject("payment_summary");
  StringBuilder out=new StringBuilder("Loop Secure account statement\nReference: ")
   .append(account.optString("reference","Not available"))
   .append("\nAccount checked: ").append(when(account.optLong("server_time")))
   .append("\nPhone price: ").append(money(summary,"contract_total"))
   .append("\nVerified paid: ").append(money(summary,"approved_total"))
   .append("\nRemaining balance: ").append(money(summary,"remaining_balance"))
   .append("\n\nAvailable payment records (newest first)\n");
  List<JSONObject> records=ordered(account.optJSONArray("claims"),"all",false);
  if(records.isEmpty()) out.append("No records loaded.\n");
  for(JSONObject record:records) out.append(when(record.optLong("submitted"))).append(" · ")
   .append(money(record,"amount")).append(" · ").append(label(record.optString("state"))).append('\n');
  return out.append("\nThis is a snapshot of the records loaded in this app, not a bank receipt. Only verified payments count. Pending and not-approved claims are not paid.\n").toString();
 }
}
