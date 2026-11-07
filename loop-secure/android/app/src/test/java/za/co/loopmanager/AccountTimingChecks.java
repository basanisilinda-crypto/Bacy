package za.co.loopmanager;

/** Runs on the JDK without a handset; no Android policy behavior is simulated. */
public final class AccountTimingChecks {
    private static void expect(AccountTiming.Stage expected,long now,long due,long grace,boolean released){
        AccountTiming.Stage actual=AccountTiming.stage(now,due,grace,released);
        if(actual!=expected)throw new AssertionError("Expected "+expected+" but got "+actual);
    }
    public static void main(String[] args){
        long due=1700000000L;
        expect(AccountTiming.Stage.ACTIVE,due-1,due,24,false);
        expect(AccountTiming.Stage.GRACE,due,due,24,false);
        expect(AccountTiming.Stage.GRACE,due+86399,due,24,false);
        expect(AccountTiming.Stage.OVERDUE,due+86400,due,24,false);
        expect(AccountTiming.Stage.OVERDUE,due,due,0,false);
        expect(AccountTiming.Stage.UNKNOWN,-1,due,24,false);
        expect(AccountTiming.Stage.RELEASED,-1,due,24,true);
        expect(AccountTiming.Stage.RELEASED,due+172800,due,24,true);
        // A newly approved deadline immediately restores the active phase.
        expect(AccountTiming.Stage.ACTIVE,due+86400,due+30*86400,24,false);
        if(AccountTiming.restrictionDue(due,24)!=due+86400)throw new AssertionError("Grace deadline");
        if(AccountTiming.stageWithDeadline(due+90000,due,due+172800,false)!=AccountTiming.Stage.GRACE)throw new AssertionError("Explicit grace must extend access");
        if(AccountTiming.stageWithDeadline(due+172800,due,due+172800,false)!=AccountTiming.Stage.OVERDUE)throw new AssertionError("Explicit grace must expire");
        System.out.println("Payment-phase boundary checks passed.");
    }
}
