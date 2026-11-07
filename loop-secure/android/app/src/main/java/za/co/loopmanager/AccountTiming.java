package za.co.loopmanager;

/** Payment phases calculated only from the signed account and trusted elapsed-time projection. */
final class AccountTiming {
    enum Stage { ACTIVE, GRACE, OVERDUE, UNKNOWN, RELEASED }
    static long restrictionDue(long due,long graceHours){return due+Math.max(0,graceHours)*3600;}
    static Stage stage(long current,long due,long graceHours,boolean released){
        return stageWithDeadline(current,due,restrictionDue(due,graceHours),released);
    }
    static Stage stageWithDeadline(long current,long due,long restrictionDue,boolean released){
        if(released)return Stage.RELEASED;
        if(current<0)return Stage.UNKNOWN;
        if(current<due)return Stage.ACTIVE;
        return current<restrictionDue?Stage.GRACE:Stage.OVERDUE;
    }
}
