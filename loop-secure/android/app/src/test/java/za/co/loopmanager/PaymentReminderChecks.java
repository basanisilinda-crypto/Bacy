package za.co.loopmanager;

public final class PaymentReminderChecks {
    public static void main(String[] args){
        long due=1700000000L;
        if(PaymentReminderPlan.forAccount(due-3*86400-1,due,24,false,1)!=null)throw new AssertionError("Too early");
        PaymentReminderPlan three=PaymentReminderPlan.forAccount(due-3*86400,due,24,false,1);
        PaymentReminderPlan one=PaymentReminderPlan.forAccount(due-1,due,24,false,1);
        if(!three.key.endsWith(":due:3")||!one.key.endsWith(":due:1"))throw new AssertionError("Due buckets");
        if(!one.key.equals(PaymentReminderPlan.forAccount(due-600,due,24,false,1).key))throw new AssertionError("Repeated notification within bucket");
        if(!PaymentReminderPlan.forAccount(due,due,24,false,1).key.endsWith(":grace"))throw new AssertionError("Grace reminder");
        if(!PaymentReminderPlan.forAccount(due+86400,due,24,false,1).key.endsWith(":overdue"))throw new AssertionError("Overdue threshold");
        if(PaymentReminderPlan.forAccount(-1,due,24,true,2)!=null)throw new AssertionError("Released must not remind");
        if(PaymentReminderPlan.forAccount(due+90000,due,24,true,1)!=null)throw new AssertionError("Paid off must not remind");
        if(PaymentReminderPlan.forAccount(due,due+30*86400,24,false,1)!=null)throw new AssertionError("Approval must clear old reminder");
        if(PaymentReminderPlan.forAccount(-1,due,24,false,1).key.equals(PaymentReminderPlan.forAccount(-1,due,24,false,2).key))throw new AssertionError("New boot must verify");
        if(!PaymentReminderPlan.forAccount(due+90000,due,24,false,1,due+172800).key.endsWith(":grace"))throw new AssertionError("Explicit grace reminder");
        System.out.println("Payment reminder timing, deduplication, restart and release checks passed.");
    }
}
