package za.co.bacy.admin;
import org.junit.Test;import org.json.JSONObject;import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk={30,35})
public class RecordSearchTest {
 @Test public void searchDoesNotConfuseReceiptWithAccountReference()throws Exception{JSONObject r=new JSONObject().put("name","Test Customer").put("reference","LOOP-123").put("bank_reference","BANK-456").put("state","pending");assertTrue(RecordSearch.payment(r," customer "));assertTrue(RecordSearch.payment(r,"loop-123"));assertTrue(RecordSearch.payment(r,"bank-456"));assertFalse(RecordSearch.payment(r,"other reference"));assertTrue(RecordSearch.payment(r,""));assertFalse(RecordSearch.payment(null,""));}
 @Test public void onlyValidTelephoneNumbersReachDialer(){assertEquals("+27600000000",RecordSearch.telephone("060 000 0000"));assertEquals("",RecordSearch.telephone("tel:abc"));assertEquals("",RecordSearch.telephone("123"));assertEquals("+27600000000",RecordSearch.telephone("+27 60 000 0000"));}
}
