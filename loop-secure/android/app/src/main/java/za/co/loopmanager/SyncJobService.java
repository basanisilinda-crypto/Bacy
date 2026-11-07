package za.co.loopmanager;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.Executors;

public class SyncJobService extends JobService {
    private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor();
    public static void schedule(Context c){JobScheduler js=c.getSystemService(JobScheduler.class);js.schedule(new JobInfo.Builder(400,new ComponentName(c,SyncJobService.class)).setPeriodic(15*60*1000L).setPersisted(true).build());}
    public static void recoverSoon(Context c){
        if(!PolicyController.owns(c))return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        // Separate one-off job: do not wait for the periodic interval after reboot or upgrade.
        scheduler.schedule(new JobInfo.Builder(401,new ComponentName(c,SyncJobService.class)).setMinimumLatency(0).setOverrideDeadline(30000).build());
    }
    @Override public boolean onStartJob(JobParameters params){
        LiveConnectionService.start(this);
        WORKER.execute(()->{
            try{
                Entitlement e;
                try{e=Sync.run(this);}catch(Exception offline){e=Entitlement.load(this,new Store(this));}
                String status=PolicyController.apply(this,e);new Store(this).put("last_status",status);
                try{Sync.report(this,e,status);}catch(Exception ignored){}
                PaymentReminders.check(this,e);
            }catch(Exception failure){try{new Store(this).put("last_status","policy_error");}catch(Exception ignored){}}
            jobFinished(params,false);
        });return true;
    }
    @Override public boolean onStopJob(JobParameters p){return true;}
}
