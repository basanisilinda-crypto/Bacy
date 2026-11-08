package za.co.loopmanager;

/** Adds a user-selected starter without discarding an existing support draft. */
final class SupportDrafts {
 static final String[] TITLES={"Payment review","Connection help","Update help"};
 static final String[] MESSAGES={
  "Please help me check a payment. Payment date, amount and reference: ",
  "Please help me reconnect my phone. The message I see is: ",
  "Please help me update Loop Secure. The update screen says: "};
 static String append(String draft,String starter) {
  if(draft==null) draft="";
  if(starter==null || starter.trim().isEmpty() || draft.contains(starter.trim())) return draft;
  String addition=(draft.isEmpty()?"":"\n\n")+starter;
  return draft.length()+addition.length()<=2000 ? draft+addition : draft;
 }
}
