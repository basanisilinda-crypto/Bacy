package za.co.bacyflex.customer;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/**
 * Schedules best-effort sync using Android's background job infrastructure.
 * No exact-time promise; offline snapshots stay readable and payments remain pending
 * until the server returns an independently verified approval.
 */
public final class DeviceSyncScheduler {
    private static final String UNIQUE_NAME="mbmf-finance-snapshot";
    private DeviceSyncScheduler() {}
    public static void start(Context context) {
        if (DeviceSession.read(context,"device_secret")==null) return;
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            FinanceSnapshotWorker.class, 6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniquePeriodicWork(UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
