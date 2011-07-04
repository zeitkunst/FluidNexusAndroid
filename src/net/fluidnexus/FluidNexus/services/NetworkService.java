/*
 *  This file is part of Fluid Nexus.
 *
 *  Fluid Nexus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Fluid Nexus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Fluid Nexus.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


package net.fluidnexus.FluidNexus.services;

import java.lang.reflect.Method;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.os.Binder;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import net.fluidnexus.FluidNexus.provider.MessagesProvider;
import net.fluidnexus.FluidNexus.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexus.Logger;
import net.fluidnexus.FluidNexus.MainActivity;
import net.fluidnexus.FluidNexus.R;

/*
 * TODO
 * * See if it's possible to manually enter paired devices to speed up creation of the network
 * * Improve error handling dramatically
 */

public class NetworkService extends Service {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // For database access
    private MessagesProviderHelper messagesProviderHelper = null;

    // Keeping track of items from the database
    private HashSet<String> currentHashes = new HashSet<String>();
    private ArrayList<Vector> currentData = new ArrayList<Vector>();


    private BluetoothServiceThread bluetoothServiceThread = null;
    private ZeroconfServiceThread zeroconfServiceThread = null;

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();
    public static final int MSG_REGISTER_CLIENT = 0x10;
    public static final int MSG_UNREGISTER_CLIENT = 0x11;
    public static final int MSG_NEW_MESSAGE_RECEIVED = 0x20;
    public static final int MSG_BLUETOOTH_SCAN_FREQUENCY = 0x30;
    public static final int MSG_ZEROCONF_SCAN_FREQUENCY = 0x40;
    public static final int MSG_BLUETOOTH_ENABLED = 0x50;
    public static final int MSG_ZEROCONF_ENABLED = 0x60;

    private int bluetoothScanFrequency = 120;
    private int zeroconfScanFrequency = 120;
    private int bluetoothEnabled = 0;
    private int zeroconfEnabled = 0;

    // Target we publish for clients to send messages to
    final Messenger messenger = new Messenger(new IncomingHandler());

    // Thread Handler message constants
    private final int CONNECT_THREAD_FINISHED = 0x30;
    private final int UPDATE_HASHES = 0x31;

    // UUID
    private static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");


    private NotificationManager nm;
    private int NOTIFICATION = R.string.service_started;
    
    // Timers
    // TODO
    // implement timers :-)
    private Timer timer;
    
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    log.debug("Adding client: " + msg.replyTo);
                    clients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    log.debug("Removing client: " + msg.replyTo);
                    clients.remove(msg.replyTo);
                    break;
                case MSG_BLUETOOTH_SCAN_FREQUENCY:
                    log.debug("Changing bluetooth scan frequency to: " + msg.arg1);
                    bluetoothScanFrequency = msg.arg1;
                    break;
                case MSG_ZEROCONF_SCAN_FREQUENCY:
                    log.debug("Changing zeroconf scan frequency to: " + msg.arg1);
                    zeroconfScanFrequency = msg.arg1;
                    break;
                case MSG_BLUETOOTH_ENABLED:
                    if (msg.arg1 != bluetoothEnabled) {
                        // If the received value is not what we currently have, then we need to start or stop the service
                        if ((msg.arg1 == 1) && (bluetoothServiceThread == null)) {
                            bluetoothServiceThread = new BluetoothServiceThread(getApplicationContext(), clients);
                            log.info("Starting our bluetooth service thread for discovered and paired devices...");
                            bluetoothServiceThread.start();
                        } 
                        /*
                        else {
                            // TODO
                            // this doesn't work as desired
                            log.info("Stopping bluetooth service thread");
                            bluetoothServiceThread.cancel();
                            bluetoothServiceThread = null;
                        }
                        */
                    }
                    bluetoothEnabled = msg.arg1;
                    break;
                case MSG_ZEROCONF_ENABLED:
                    if (msg.arg1 != zeroconfEnabled) {
                        if ((msg.arg1 == 1) && (zeroconfServiceThread == null)) {
                            zeroconfServiceThread = new ZeroconfServiceThread(getApplicationContext(), clients);
                            log.info("Starting our zeroconf service thread...");
                            zeroconfServiceThread.start();
                        } 

                    }
                    zeroconfEnabled = msg.arg1;
                    break;
                case MainActivity.MSG_NEW_MESSAGE_CREATED:
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.updateHashes();
                        bluetoothServiceThread.updateData();
                    }
                    log.debug("MSG_NEW_MESSAGE_CREATED received");
                    break;
                case MainActivity.MSG_MESSAGE_DELETED:
                    log.debug("MSG_MESSAGE_DELETED received");
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.updateHashes();
                        bluetoothServiceThread.updateData();
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);


        // setup database object
        messagesProviderHelper = new MessagesProviderHelper(this);

        // Show a notification regarding the service
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.debug("Received start id " + startId + ": " + intent);
        // Run until explicitly stopped
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nm.cancel(NOTIFICATION);
        if (bluetoothServiceThread != null) {
            bluetoothServiceThread.cancel();
        }
    }


    /**
     * Show a notification while the service is running
     */
    private void showNotification() {
        CharSequence text = getText(R.string.service_started);

        // Set icon, scrolling text, and timestamp
        Notification notification = new Notification(R.drawable.fluid_nexus_icon, text, System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        // The PendingIntent to launch the activity of the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        // Set the info for the view that show in the notification panel
        notification.setLatestEventInfo(this, getText(R.string.service_label), text, contentIntent);

        nm.notify(NOTIFICATION, notification);
    }

}
