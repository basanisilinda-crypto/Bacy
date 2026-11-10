package za.co.loopmanager;
import java.net.URI;
final class PayAtPayment {
 static boolean validNumber(String value){return value!=null&&value.matches("[0-9]{20,30}");}
 static boolean trustedLink(String value){
  try{
   URI u=new URI(value);
   return "https".equalsIgnoreCase(u.getScheme())&&"payat.io".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null
    &&(u.getPort()==-1||u.getPort()==443)&&u.getRawQuery()==null&&u.getRawFragment()==null
    &&u.getPath()!=null&&u.getPath().matches("/[0-9]{20,30}");
  }catch(Exception ignored){return false;}
 }
 static String stateMessage(String value){
  return switch(value){
   case "paid"->"Pay@ confirmed your payment. The updated account will sync to this phone.";
   case "pending","creating"->"Pay@ has not confirmed a payment. Use your number or link, then check again. Do not pay twice.";
   case "review_required"->"Pay@ payment needs administrator review. Contact support before paying again.";
   case "expired"->"This Pay@ payment number expired. Contact support for a new request.";
   case "failed"->"Pay@ could not create this payment. Try another available method.";
   default->"No Pay@ payment number has been created for this account.";
  };
 }
}
