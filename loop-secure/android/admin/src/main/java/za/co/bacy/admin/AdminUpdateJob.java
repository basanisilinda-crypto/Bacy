package za.co.bacy.admin;
import android.app.job.*;
public final class AdminUpdateJob extends JobService {
 private java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();private java.util.concurrent.Future<?> task;private volatile boolean stopped;
 @Override public boolean onStartJob(JobParameters p){stopped=false;task=worker.submit(()->{boolean retry=AdminUpdates.run(this);if(!stopped)jobFinished(p,retry);});return true;}
 @Override public boolean onStopJob(JobParameters p){stopped=true;if(task!=null)task.cancel(true);return true;}
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
