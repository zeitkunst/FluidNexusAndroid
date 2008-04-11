package org.zeitkunst.FluidNexus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.Toast;

public class FluidNexusClient extends Service {
    private NotificationManager nm;
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexusClient"); 

    public class FluidNexusClientBinder extends Binder {
        FluidNexusClient getService() {
            return FluidNexusClient.this;
        }
    }

    @Override
    protected void onCreate() {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        log.info("starting fluid nexus client");
        showNotification();
    }

    @Override
    protected void onDestroy() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IBinder binder = new FluidNexusClientBinder();

    private void showNotification() {
        Intent contentIntent = new Intent(this, FluidNexusAndroid.class);
        Intent appIntent = new Intent(this, FluidNexusAndroid.class);

        CharSequence text = "this is a test.";

        nm.notify(2341234,
                new Notification(this,
                    R.drawable.menu_all,
                    text,
                    System.currentTimeMillis(),
                    "This is a label",
                    text,
                    contentIntent,
                    R.drawable.menu_all,
                    "Fluid Nexus",
                    appIntent));
    }
}
