package za.co.bacy.admin;
import java.util.Locale;import org.json.JSONObject;
/** Searches only already-authorized records; does not transmit or persist queries. */
final class RecordSearch {
 static boolean payment(JSONObject record,String query){if(record==null)return false;String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return true;for(String key:new String[]{"name","reference","bank_reference","receipt_reference","state"})if(record.optString(key,"").toLowerCase(Locale.ROOT).contains(q))return true;return false;}
 static String telephone(String raw){String n=raw==null?"":raw.replaceAll("[^0-9]","");if(n.startsWith("0")&&n.length()==10)n="27"+n.substring(1);return n.matches("[1-9][0-9]{7,14}")?"+"+n:"";}
}
