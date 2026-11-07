package za.co.loopmanager;

/** Privacy-preserving reminders derived from signed-account time, not the editable phone clock. */
final class PaymentReminderPlan {
    final String key,title,message;
    private PaymentReminderPlan(String key,String title,String message){this.key=key;this.title=title;this.message=message;}
    static PaymentReminderPlan forAccount(long current,long due,long graceHours,boolean released,long boot){
        return forAccount(current,due,graceHours,released,boot,0);
    }
    static PaymentReminderPlan forAccount(long current,long due,long graceHours,boolean released,long boot,long graceUntil){
        AccountTiming.Stage stage=AccountTiming.stageWithDeadline(current,due,Math.max(AccountTiming.restrictionDue(due,graceHours),graceUntil),released);
        if(stage==AccountTiming.Stage.RELEASED)return null;
        if(stage==AccountTiming.Stage.UNKNOWN)return new PaymentReminderPlan(due+":verify:"+boot,"Verify your phone account","Connect to check your payment deadline after restart.");
        if(stage==AccountTiming.Stage.ACTIVE){
            long remaining=due-current;if(remaining>3*86400)return null;
            long days=(remaining+86399)/86400;
            return new PaymentReminderPlan(due+":due:"+days,"Payment due soon","Your payment is due within "+days+(days==1?" day.":" days.")+" Open Phone Finance for EFT details.");
        }
        if(stage==AccountTiming.Stage.GRACE)return new PaymentReminderPlan(due+":grace","Payment grace period","Your payment is due. Open Phone Finance to see when the grace period ends.");
        return new PaymentReminderPlan(due+":overdue","Payment overdue","Open Phone Finance for payment, review status or support.");
    }
}
