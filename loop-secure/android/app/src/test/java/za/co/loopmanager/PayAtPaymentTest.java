package za.co.loopmanager;
import org.junit.Test;
import static org.junit.Assert.*;
public class PayAtPaymentTest{
 @Test public void paymentNumberMustBeNumericProviderLength(){
  assertTrue(PayAtPayment.validNumber("117008737361111111111111"));
  assertFalse(PayAtPayment.validNumber("117008"));
  assertFalse(PayAtPayment.validNumber("https://payat.io/117008737361111111111111"));
 }
 @Test public void onlyTrustedHttpsPaymentLinksAreAllowed(){
  assertTrue(PayAtPayment.trustedLink("https://payat.io/117008737361111111111111"));
  assertFalse(PayAtPayment.trustedLink("http://payat.io/117008737361111111111111"));
  assertFalse(PayAtPayment.trustedLink("https://payat.io.evil.test/117008737361111111111111"));
  assertFalse(PayAtPayment.trustedLink("https://payat.io/117008737361111111111111?next=https://evil.test"));
 }
 @Test public void pendingPaymentCannotBePresentedAsConfirmed(){
  assertTrue(PayAtPayment.stateMessage("pending").contains("not confirmed"));
  assertTrue(PayAtPayment.stateMessage("paid").contains("confirmed"));
 }
}
