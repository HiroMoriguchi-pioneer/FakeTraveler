package cl.coders.faketraveler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class TickService extends Service  {

    public static final String TAG = "TickService";
    public static final int NOTIFY_ID = 765;

    private Context context;
    private SharedPreferences sharedPref;
    public static boolean active = false;

    private static Timer timer;

    private double x2l(double x, double y) {
        if (y == 90 || y == -90) return 0;
        final double r = 6378137.0 / 180 * Math.PI;
        return r * x * Math.cos(y / 180 * Math.PI);
    }

    private double y2l(double y) {
        final double r = 6356752.314 / 180 * Math.PI;
        return r * y;
    }

    private double diff(double x1, double y1, double x2, double y2) {
        double dy = y2l(Math.abs(y1-y2));
        double dx = x2l(Math.abs(x1-x2),y1);
        return Math.sqrt(dy*dy+dx*dx);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        context = getApplicationContext();
        sharedPref = context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
        active = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        int requestCode = intent.getIntExtra("REQUEST_CODE",0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "Foreground service";
            String title = "Mock Location";
            String msg = "Location Updating...";

            Intent aintent = new Intent(context, MainActivity.class);
            aintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, aintent,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0)
                            | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager notificationManager =
                    (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(channelId, title , NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Fake Location service");
            channel.setSound(null,null);
            channel.enableLights(false);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(false);
            channel.setShowBadge(false);

            if(notificationManager != null){
                notificationManager.createNotificationChannel(channel);
                Notification notification = new Notification.Builder(context, channelId)
                        .setContentTitle(title)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentText(msg)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setWhen(System.currentTimeMillis())
                        .build();

                // startForeground
                startForeground(NOTIFY_ID, notification);
            }
        }

        if (MainActivity.timeInterval < 100) {
            MainActivity.loadPref(sharedPref);
        }

        if (timer == null) {
            timer = new Timer(true);
        }
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (MainActivity.context == null) return;
                try {
                    double lat = Double.parseDouble(sharedPref.getString("lat", "0"));
                    double lng = Double.parseDouble(sharedPref.getString("lng", "0"));
                    String list = sharedPref.getString("list","");

                    String[] lines = list.split("\n");
                    if (lines.length>1) {
                        int i = MainActivity.index;
                        if (lines.length>i && lines[i].trim().length()>3) {
                            String[] vals = lines[i].split(",");
                            lat = Double.parseDouble(vals[1]);
                            lng = Double.parseDouble(vals[0]);
                            double d = diff(MainActivity.llat,MainActivity.llng,lat,lng)*3600/MainActivity.timeInterval;
                            double maxspd = i>0 ? MainActivity.speedLimit : Math.max(MainActivity.speedLimit,1500);
                            if (d<=maxspd) {
                                MainActivity.index++;
//                                Log.d(TAG,String.format("SpeedN=%f",d));
                            }
                            else {
                                lat = (MainActivity.llat*(d-maxspd)+lat*maxspd)/d;
                                lng = (MainActivity.llng*(d-maxspd)+lng*maxspd)/d;
                                d = diff(MainActivity.llat,MainActivity.llng,lat,lng)*3600/MainActivity.timeInterval;
//                                Log.d(TAG,String.format("SpeedL=%f",d));
                            }
                            double dx = Math.toRadians(lng-MainActivity.llng);
                            double y1 = Math.toRadians(MainActivity.llat);
                            double y2 = Math.toRadians(lat);
                            double azi = Math.toDegrees(Math.atan2(Math.sin(dx),
                                    Math.cos(y1)*Math.tan(y2)-Math.sin(y1)*Math.cos(dx)));
//                            Log.d(TAG,String.format("azi=%f",azi));
                            MainActivity.exec(lat, lng, (float)d*1000/3600, (float) azi);
                        }
                        else {
                            MainActivity.index = 0;
                        }
                    }
                    else {
                        MainActivity.exec(lat, lng,0 , 1);
                    }

                    if (MainActivity.hasEnded()) {
                        MainActivity.stopMockingLocation();
                    }
                } catch (Exception e)
                {
                    e.printStackTrace();
                }

            }
        },MainActivity.timeInterval,MainActivity.timeInterval);
        //return START_NOT_STICKY;
        return START_STICKY;
        //return START_REDELIVER_INTENT;
    }

    private void stopAlarmService() {
        Log.d(TAG, "stopAlarmService");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFY_ID);
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        stopAlarmService();
        // Stop Service
        stopSelf();
        active = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startTick(Context ctx) {
        Log.d(TAG, "startTick");
        Intent intent = new Intent(ctx.getApplicationContext(), TickService.class);
        intent.putExtra("REQUEST_CODE", 1);
        // Start Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void stopTick(Context ctx) {
        Log.d(TAG,"stopTick");

        Intent intent = new Intent(ctx, TickService.class);
        ctx.stopService(intent);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFY_ID);
            }
        }
    }
}
