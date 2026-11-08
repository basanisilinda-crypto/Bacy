package za.co.bacy.admin;
import org.json.JSONObject;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class CustomerFilterTest {
 @Test public void filtersDistinguishStaleContactAndPendingCommands()throws Exception{Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("za.co.bacy.admin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.overview=new JSONObject().put("server_time",2000);JSONObject d=new JSONObject().put("last_seen",1900).put("enforcement_status","unrestricted").put("revision",4).put("acknowledged_revision",4);a.customerFilter="offline";assertFalse(a.matchesCustomerFilter(d));d.put("last_seen",1000);assertTrue(a.matchesCustomerFilter(d));a.customerFilter="attention";assertFalse(a.matchesCustomerFilter(d));d.put("acknowledged_revision",3);assertTrue(a.matchesCustomerFilter(d));a.customerFilter="all";assertTrue(a.matchesCustomerFilter(d));a.onDestroy();}
}
