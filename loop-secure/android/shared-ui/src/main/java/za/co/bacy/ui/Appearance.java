package za.co.bacy.ui;
import android.content.Context;import android.content.res.Configuration;
/** Per-app preference. This changes presentation only, never finance or device policy. */
public final class Appearance {
 private Appearance(){}
 public static String selected(Context c){return c.getSharedPreferences("appearance",0).getString("mode","system");}
 public static void select(Context c,String mode){if(!java.util.Arrays.asList("light","dark","system").contains(mode))throw new IllegalArgumentException("Unknown appearance");c.getSharedPreferences("appearance",0).edit().putString("mode",mode).apply();}
 public static boolean dark(Context c){String mode=selected(c);return mode.equals("dark")||(mode.equals("system")&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
 public static int paper(Context c){return dark(c)?0xff111827:0xffffffff;}
 public static int surface(Context c){return dark(c)?0xff1c2636:0xffffffff;}
 public static int ink(Context c){return dark(c)?0xfff0f2f4:0xff000000;}
 public static int muted(Context c){return dark(c)?0xffb5bdc7:0xff595959;}
 public static int rule(Context c){return dark(c)?0xff42516a:0xffdddddd;}
 public static int soft(Context c){return dark(c)?0xff26354c:0xfff3f5f4;}
}
