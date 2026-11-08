package za.co.loopmanager;
import org.json.*;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import za.co.bacy.ui.PaymentRecords;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class PaymentRecordsTest {
 private JSONObject record(String state,long submitted,int amount)throws Exception{return new JSONObject().put("state",state).put("submitted",submitted).put("amount",amount);}
 @Test public void filtersAndSortsWithoutMutatingOriginalRecords()throws Exception{
  JSONObject old=record("pending",100,300),recent=record("approved",200,300),unknown=record("pending",0,200);
  JSONArray source=new JSONArray().put(recent).put(JSONObject.NULL).put("invalid").put(old).put(unknown);
  assertEquals(old,PaymentRecords.ordered(source,"all",true).get(0));
  assertEquals(recent,PaymentRecords.ordered(source,"all",false).get(0));
  assertEquals(unknown,PaymentRecords.ordered(source,"all",false).get(2));
  assertEquals(unknown,PaymentRecords.ordered(source,"pending",true).get(1));
  assertEquals(0,PaymentRecords.ordered(source,"rejected",true).size());
  assertSame(recent,source.getJSONObject(0));assertEquals(5,source.length());
 }
 @Test public void statementUsesVerifiedSummaryNotSumOfPendingClaims()throws Exception{
  JSONObject account=new JSONObject().put("reference","LOOP-TEST").put("server_time",1000).put("token","do-not-export")
   .put("payment_summary",new JSONObject().put("contract_total",1700).put("approved_total",300).put("remaining_balance",1400))
   .put("claims",new JSONArray().put(record("approved",100,300)).put(record("pending",200,900)).put(record("rejected",300,500)));
  String output=PaymentRecords.statement(account);
  assertTrue(output.contains("Verified paid: R300"));assertTrue(output.contains("Remaining balance: R1400"));
  assertTrue(output.contains("R900 · In review"));assertTrue(output.contains("R500 · Not approved"));assertFalse(output.contains("do-not-export"));
  assertTrue(output.contains("not a bank receipt"));assertTrue(output.contains("snapshot"));
 }
 @Test public void missingSummaryAndDateNeverInventPaidTotals()throws Exception{
  String output=PaymentRecords.statement(new JSONObject().put("claims",new JSONArray().put(new JSONObject().put("submitted",Long.MAX_VALUE))));
  assertTrue(output.contains("Verified paid: Not available"));assertTrue(output.contains("Not recorded · Not available · Recorded"));
 }
 @Test public void supportStartersPreserveDraftAndNeverTruncateIt(){
  String starter=SupportDrafts.MESSAGES[0],existing="My own details";
  String result=SupportDrafts.append(existing,starter);assertTrue(result.startsWith(existing+"\n\n"));assertTrue(result.endsWith(starter));
  assertEquals(result,SupportDrafts.append(result,starter));String full="x".repeat(1990);assertEquals(full,SupportDrafts.append(full,starter));
  assertEquals(starter,SupportDrafts.append("",starter));
 }
}
