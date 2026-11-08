package za.co.bacy.admin;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import android.view.View;import com.google.android.material.button.MaterialButton;import za.co.bacy.ui.Appearance;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,35})
public class AppearanceTest {
 @After public void reset(){Appearance.select(RuntimeEnvironment.getApplication(),"system");}
 @Test public void darkSignInActionRemainsReadable(){var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions("za.co.bacy.admin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Appearance.select(app,"dark");try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){MainActivity a=controller.get();java.util.ArrayList<View> matches=new java.util.ArrayList<>();a.content.findViewsWithText(matches,"Connect admin app",View.FIND_VIEWS_WITH_TEXT);MaterialButton b=(MaterialButton)matches.stream().filter(v->v instanceof MaterialButton).findFirst().orElseThrow();assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(b.getCurrentTextColor(),b.getBackgroundTintList().getDefaultColor())>4.5);assertEquals(Appearance.paper(a),a.paper);}}
}
