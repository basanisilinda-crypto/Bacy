package za.co.bacy.ui;
/** The Android-reported model, not a guessed marketing name. */
public final class DeviceName {
 private DeviceName(){}
 public static String current(){return format(android.os.Build.MANUFACTURER,android.os.Build.MODEL);}
 public static String format(String maker,String model){
  maker=maker==null?"":maker.trim();model=model==null?"":model.trim();
  if("unknown".equalsIgnoreCase(maker))maker="";
  if("unknown".equalsIgnoreCase(model))model="";
  if(model.isEmpty())return maker.isEmpty()?"Your Android phone":maker+" phone";
  return maker.isEmpty()||model.toLowerCase(java.util.Locale.ROOT).startsWith(maker.toLowerCase(java.util.Locale.ROOT))?model:maker+" "+model;
 }
}
