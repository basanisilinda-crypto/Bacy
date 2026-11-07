package za.co.loopmanager;
import android.view.View;import android.widget.TextView;import androidx.appcompat.app.AppCompatActivity;import com.google.android.material.bottomsheet.BottomSheetDialog;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class AccountSheetsTest {
 private AppCompatActivity activity(){return Robolectric.buildActivity(MainActivity.class).setup().visible().get();}
 private View action(BottomSheetDialog d,String name){java.util.ArrayList<View> v=new java.util.ArrayList<>();d.getWindow().getDecorView().findViewsWithText(v,name,View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);return v.stream().filter(x->name.contentEquals(x.getContentDescription())).findFirst().orElseThrow();}
 @Test public void checkoutChoiceClosesBeforeInvokingTheFinancialAction(){AppCompatActivity a=activity();final BottomSheetDialog[] d={null};int[] calls={0};d[0]=AccountSheets.payments(a,"R300",()->{assertFalse(d[0].isShowing());calls[0]++;},()->{},()->{},()->{},()->{});assertTrue(d[0].isShowing());action(d[0],"Card payment").performClick();assertEquals(1,calls[0]);}
 @Test public void pendingStateCannotBePresentedAsVerified(){AppCompatActivity a=activity();BottomSheetDialog d=AccountSheets.status(a,"pending",true,"Checkout failed; no payment confirmed.",()->{},()->{},()->{});java.util.ArrayList<View> v=new java.util.ArrayList<>();d.getWindow().getDecorView().findViewsWithText(v,"Payment verified",View.FIND_VIEWS_WITH_TEXT);assertTrue(v.isEmpty());assertNotNull(action(d,"Get a fresh payment link"));d.dismiss();}
}
