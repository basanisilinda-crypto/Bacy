package za.co.loopmanager;
import android.app.job.JobService;
import android.app.job.JobParameters;
public final class UpdateJobService extends JobService {
 private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();private volatile boolean stopped;private java.util.concurrent.Future<?> task;
 @Override public boolean onStartJob(JobParameters p){stopped=false;task=worker.submit(()->{boolean retry=false;try{retry=ManagedUpdates.run(this,p.getExtras().getBoolean("manual",false));}catch(Exception e){ManagedUpdates.status(this,"The app update could not complete. Your installed app and account are retained; the next Wi-Fi check will retry.");retry=true;}if(!stopped)jobFinished(p,retry);});return true;}
 @Override public boolean onStopJob(JobParameters p){stopped=true;if(task!=null)task.cancel(true);return true;}
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
