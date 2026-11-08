package za.co.bacy.admin;
import android.view.View;import org.json.*;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class PaymentQueueTest {
 private boolean contains(MainActivity a,String value){java.util.ArrayList<View> views=new java.util.ArrayList<>();a.content.findViewsWithText(views,value,View.FIND_VIEWS_WITH_TEXT);return !views.isEmpty();}
 @Test public void statusAndSearchCombineAndRefreshKeepsSelection()throws Exception{
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("za.co.bacy.admin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   MainActivity a=controller.get();a.overview=new JSONObject().put("claims",new JSONArray()
    .put(new JSONObject().put("name","Pending Customer").put("reference","LOOP-A").put("state","pending").put("submitted",200))
    .put(new JSONObject().put("name","Received Customer").put("reference","LOOP-B").put("state","approved").put("submitted",100)));
   a.tab=2;a.paymentFilter="pending";a.paymentQuery="LOOP-A";a.render();
   assertTrue(contains(a,"Pending Customer"));assertFalse(contains(a,"Received Customer"));
   a.paymentSearch.requestFocus();a.paymentSearch.setSelection(1,4);a.render();
   assertTrue(a.paymentSearch.hasFocus());assertEquals("LOOP-A",a.paymentSearch.getText().toString());assertEquals(1,a.paymentSearch.getSelectionStart());assertEquals(4,a.paymentSearch.getSelectionEnd());
   a.paymentSearch.setText("LOOP-B");assertTrue(contains(a,"No payments in this view"));assertFalse(contains(a,"Received Customer"));
   a.paymentFilter="approved";a.render();assertTrue(contains(a,"Received Customer"));assertFalse(contains(a,"Pending Customer"));
  }
 }
}
