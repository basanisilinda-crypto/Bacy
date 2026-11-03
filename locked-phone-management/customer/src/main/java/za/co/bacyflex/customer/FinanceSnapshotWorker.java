package za.co.bacyflex.customer;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/**
 * Periodically refreshes the encrypted last-verified finance snapshot.
 * Does not approve payments, apply device restrictions, or change ledger data.
 * WorkManager handles connectivity constraints and rescheduling after reboot.
 */
public final class FinanceSnapshotWorker extends Worker {
    public FinanceSnapshotWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);
    }
    @NonNull @Override public Result doWork() {
        String secret=DeviceSession.read(getApplicationContext(),"device_secret");
        if(secret==null || secret.isEmpty())return Result.success();
        try {
            JSONObject snapshot=DeviceFinanceApi.snapshot(secret);
            if(snapshot.optJSONObject("account")==null) return Result.failure();
            DeviceSession.store(getApplicationContext(),"latest_snapshot",snapshot.toString());
            return Result.success();
        } catch(Exception error) {
            // Never overwrite a previously verified snapshot with an error response.
            return getRunAttemptCount()<4 ? Result.retry() : Result.failure();
        }
    }
}
