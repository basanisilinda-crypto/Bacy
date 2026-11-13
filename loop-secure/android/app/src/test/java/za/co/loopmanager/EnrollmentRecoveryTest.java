package za.co.loopmanager;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import android.app.admin.DevicePolicyManager;import android.view.View;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class EnrollmentRecoveryTest {
 @Test public void unassignedPhoneCannotOfferCustomerEnrollmentRecovery(){try(var ctl=Robolectric.buildActivity(MainActivity.class).setup().visible()){java.util.ArrayList<View> found=new java.util.ArrayList<>();ctl.get().getWindow().getDecorView().findViewsWithText(found,"Connect enrollment code",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);assertTrue(found.isEmpty());}}
 @Test public void ownedUnlinkedPhoneOffersRecoveryWithoutRemovingAccounts()throws Exception{var c=RuntimeEnvironment.getApplication();Shadows.shadowOf(c.getSystemService(DevicePolicyManager.class)).setDeviceOwner(PolicyController.admin(c));try(var ctl=Robolectric.buildActivity(MainActivity.class).setup().visible()){java.util.ArrayList<View> found=new java.util.ArrayList<>();ctl.get().getWindow().getDecorView().findViewsWithText(found,"Connect enrollment code",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);assertFalse(found.isEmpty());assertEquals("",new Store(ctl.get()).get("device_token"));}}
}
