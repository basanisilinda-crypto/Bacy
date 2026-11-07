package za.co.loopmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visible, owner-authorized connection; never masquerades as a push receipt. */
public class LiveConnectionService extends Service {
    static final String UPDATED="za.co.loopmanager.ENTITLEMENT_UPDATED";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean stopped=false;
    private final AtomicBoolean syncRequested=new AtomicBoolean(true);
    private final Object reconnectWake=new Object();
    private long networkGeneration;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network validatedNetwork;
    private void requestCloudSync(){syncRequested.set(true);synchronized(reconnectWake){networkGeneration++;reconnectWake.notifyAll();}}
    private void watchConnectivity(){
        connectivity=getSystemService(ConnectivityManager.class);
        networkCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){
                if(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&network.equals(validatedNetwork))validatedNetwork=null;
                if(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&!network.equals(validatedNetwork)){validatedNetwork=network;requestCloudSync();ManagedUpdates.checkSoon(LiveConnectionService.this);}
            }
            @Override public void onLost(Network network){if(network.equals(validatedNetwork))validatedNetwork=null;}
        };
        try{connectivity.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException unavailable){networkCallback=null;}
    }
    private final Handler enforcementHandler=new Handler(Looper.getMainLooper());
    // Local enforcement must not wait behind network timeouts or reconnect backoff.
    private final Runnable enforcementCheck=new Runnable(){
        @Override public void run(){
            if(stopped)return;
            try{
                if(!PolicyController.owns(LiveConnectionService.this)){stopSelf();return;}
                Store store=new Store(LiveConnectionService.this);
                Entitlement cached=Entitlement.load(LiveConnectionService.this,store);
                if(cached!=null){
                    String applied=PolicyController.apply(LiveConnectionService.this,cached);
                    PaymentReminders.check(LiveConnectionService.this,cached);
                    if(!applied.equals(store.get("last_status"))){
                        store.put("last_status",applied);
                        sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()),getPackageName()+".permission.INTERNAL");
                    }
                    if(cached.data.optBoolean("released")){stopSelf();return;}
                }
            }catch(Exception unavailable){
                // Keep retrying locally. Never mark a failed restriction as applied.
                try{Store store=new Store(LiveConnectionService.this);if(!"policy_error".equals(store.get("last_status"))){store.put("last_status","policy_error");sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()),getPackageName()+".permission.INTERNAL");}}catch(Exception ignored){}
            }
            if(!stopped)enforcementHandler.postDelayed(this,5000);
        }
    };
    static void start(Context c){
        if(!PolicyController.owns(c))return;
        try{if(!new Store(c).get("device_token").isEmpty())c.startForegroundService(new Intent(c,LiveConnectionService.class));}
        catch(Exception unavailable){SyncJobService.schedule(c);}
    }
    @Override public void onCreate(){super.onCreate();
        if(!PolicyController.owns(this)){stopSelf();return;}
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("connection","Device management connection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,400,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"connection").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Loop Secure management").setContentText("Manages this enrolled phone and checks for account updates.").setContentIntent(open).setOngoing(true).build();
        try{if(android.os.Build.VERSION.SDK_INT>=34)startForeground(400,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);else startForeground(400,n);}
        catch(RuntimeException unavailable){SyncJobService.schedule(this);stopSelf();return;}
        enforcementHandler.post(enforcementCheck);
        watchConnectivity();
        worker.execute(this::connect);
    }
    private void connect(){
        String cursor="";int failures=0;long lastSync=0;
        while(!stopped){
            long observedGeneration;synchronized(reconnectWake){observedGeneration=networkGeneration;}
            try{
                if(!PolicyController.owns(this)){stopSelf();return;}
                Store store=new Store(this);
                if(syncRequested.getAndSet(false)){
                    Entitlement fresh=Sync.run(this);String applied=PolicyController.apply(this,fresh);Sync.report(this,fresh,applied);
                    ManagedUpdates.checkSoon(this);lastSync=android.os.SystemClock.elapsedRealtime();sendBroadcast(new Intent(UPDATED).putExtra("account_verified",true).setPackage(getPackageName()),getPackageName()+".permission.INTERNAL");
                    if(fresh!=null&&fresh.data.optBoolean("released")){stopSelf();return;}
                }
                JSONObject event=new Api(store).post("/api/device/events",new JSONObject().put("cursor",cursor).put("wait_seconds",10),true);
                if(event.optBoolean("changed")||android.os.SystemClock.elapsedRealtime()-lastSync>=60000){
                    Entitlement e=Sync.run(this);String status=PolicyController.apply(this,e);Sync.report(this,e,status);
                    cursor=event.getString("cursor");lastSync=android.os.SystemClock.elapsedRealtime();ManagedUpdates.checkSoon(this);
                    sendBroadcast(new Intent(UPDATED).putExtra("account_verified",true).setPackage(getPackageName()),getPackageName()+".permission.INTERNAL");
                    if(e!=null&&e.data.optBoolean("released")){stopSelf();return;}
                }else cursor=event.getString("cursor");
                failures=0;
            }catch(Exception unavailable){
                if(stopped)return;
                syncRequested.set(true);failures=Math.min(failures+1,6);
                synchronized(reconnectWake){
                    try{if(!stopped&&networkGeneration==observedGeneration)reconnectWake.wait(Math.min(60000L,1000L*(1L<<failures)));}
                    catch(InterruptedException interrupted){if(stopped)return;}
                }
            }
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    @Override public void onDestroy(){stopped=true;enforcementHandler.removeCallbacksAndMessages(null);
        if(connectivity!=null&&networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
        synchronized(reconnectWake){reconnectWake.notifyAll();}worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
