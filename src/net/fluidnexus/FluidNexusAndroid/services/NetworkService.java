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


package net.fluidnexus.FluidNexusAndroid.services;

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
import android.net.wifi.WifiManager;
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

import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import net.fluidnexus.FluidNexusAndroid.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexusAndroid.Logger;
import net.fluidnexus.FluidNexusAndroid.MainActivity;
import net.fluidnexus.FluidNexusAndroid.R;

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
    private NexusServiceThread nexusServiceThread = null;

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();
    public static final int MSG_REGISTER_CLIENT = 0x10;
    public static final int MSG_UNREGISTER_CLIENT = 0x11;
    public static final int MSG_NEW_MESSAGE_RECEIVED = 0x20;
    public static final int MSG_BLUETOOTH_SCAN_FREQUENCY = 0x30;
    public static final int MSG_BLUETOOTH_BONDED_ONLY_FLAG = 0x31;
    public static final int MSG_ZEROCONF_SCAN_FREQUENCY = 0x40;
    public static final int MSG_BLUETOOTH_ENABLED = 0x50;
    public static final int MSG_ZEROCONF_ENABLED = 0x60;
    public static final int MSG_NEXUS_ENABLED= 0x70;
    public static final int MSG_SEND_BLACKLISTED = 0x80;
    public static final int MSG_DATABASE_OPEN = 0x90;

    // Intent filters
    private IntentFilter networkConnectivityFilter = null;

    private WifiManager wifiManager = null;
    private int bluetoothEnabled = 0;
    private int zeroconfEnabled = 0;
    private int nexusEnabled = 0;
    private boolean sendBlacklist = false;
    private int bluetoothScanFrequency = 300;
    private int zeroconfScanFrequency = 300;
    private int nexusScanFrequency = 300;

    // masks for notification info
    private static final int NONE_FLAG = 0;
    private static final int BLUETOOTH_FLAG = 1<<0;
    private static final int ZEROCONF_FLAG = 1<<1;
    private static final int NEXUS_FLAG = 1<<2;
    private int notificationFlags = 0;


    // Target we publish for clients to send messages to
    final Messenger messenger = new Messenger(new IncomingHandler());

    // Thread Handler message constants
    private final int CONNECT_THREAD_FINISHED = 0x30;
    private final int UPDATE_HASHES = 0x31;

    // UUID
    private static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");


    private NotificationManager nm = null;
    private Notification notification = null;
    private int NOTIFICATION = R.string.service_started;
    
    // Timers
    // TODO
    // implement timers :-)
    private Timer timer;


    /**
     * BroadcastReceiver for network connectivity changes
     */
    private final BroadcastReceiver networkConnectivityReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int result = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
                if (result == WifiManager.WIFI_STATE_DISABLING) {
                    stopZeroconfServiceThread();
                } else if (result == WifiManager.WIFI_STATE_ENABLED) {
                    startZeroconfServiceThread();
                }

            }
        }
    };

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
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.setScanFrequency(msg.arg1);
                        bluetoothScanFrequency = msg.arg1;
                    }
                    break;
                case MSG_ZEROCONF_SCAN_FREQUENCY:
                    log.debug("Changing zeroconf scan frequency to: " + msg.arg1);
                    if (zeroconfServiceThread != null) {
                        zeroconfServiceThread.setScanFrequency(msg.arg1);
                        zeroconfScanFrequency = msg.arg1;
                    }
                    break;
                case MSG_BLUETOOTH_ENABLED:
                    if (msg.arg1 != bluetoothEnabled) {
                        // If the received value is not what we currently have, then we need to start or stop the service
                        if ((msg.arg1 == 1) && (bluetoothServiceThread == null)) {
                            bluetoothServiceThread = new BluetoothServiceThread(getApplicationContext(), clients, sendBlacklist);
                            bluetoothServiceThread.setScanFrequency(msg.arg2);
                            bluetoothScanFrequency = msg.arg2;
                            log.info("Starting our bluetooth service thread for discovered and paired devices...");
                            bluetoothServiceThread.start();
                            notificationFlags |= BLUETOOTH_FLAG;
                            updateNotification();
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
                case MSG_BLUETOOTH_BONDED_ONLY_FLAG:
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.setBondedOnly(msg.arg1 != 0);
                    }

                    break;
                case MSG_SEND_BLACKLISTED:
                    sendBlacklist = (msg.arg1 != 0);
                    break;
                case MSG_ZEROCONF_ENABLED:
                    if (msg.arg1 != zeroconfEnabled) {
                        if ((msg.arg1 == 1) && (zeroconfServiceThread == null)) {

                            wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

                            if (wifiManager.getWifiState() == wifiManager.WIFI_STATE_ENABLED) {
                                zeroconfServiceThread = new ZeroconfServiceThread(getApplicationContext(), clients, sendBlacklist);
                                zeroconfServiceThread.setScanFrequency(msg.arg2);
                                zeroconfScanFrequency = msg.arg2;
                                log.info("Starting our zeroconf service thread...");
                                zeroconfServiceThread.start();
                                notificationFlags |= ZEROCONF_FLAG;
                                updateNotification();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.toast_wifi_not_enabled, Toast.LENGTH_SHORT).show();
                                log.warn("Wifi not enabled; zeroconf service cannot start.");
                            }
                        } 

                    }
                    zeroconfEnabled = msg.arg1;
                    break;
                case MSG_NEXUS_ENABLED:
                    if (msg.arg1 != nexusEnabled) {
                        if ((msg.arg1 == 1) && (nexusServiceThread == null)) {
                            Bundle b = msg.getData();
                            String key = b.getString("key");
                            String secret = b.getString("secret");
                            String token = b.getString("token");
                            String token_secret = b.getString("token_secret");
                            nexusServiceThread = new NexusServiceThread(getApplicationContext(), clients, key, secret, token, token_secret);
                            if (msg.arg2 == 0) {
                                nexusServiceThread.setScanFrequency(300);
                                nexusScanFrequency = 300;
                            } else {
                                nexusServiceThread.setScanFrequency(msg.arg2);
                                nexusScanFrequency = msg.arg2;

                            }
                            log.info("Starting our nexus service thread...");
                            nexusServiceThread.start();
                            notificationFlags |= NEXUS_FLAG;
                            updateNotification();
                        }

                        nexusEnabled = msg.arg1;
                    }


                    break;
                case MSG_DATABASE_OPEN:
                    Bundle b = msg.getData();
                    String passphrase = b.getString("passphrase");

                    try {
                        messagesProviderHelper.open(passphrase);
                        passphrase = null;
                        System.gc();
                    } catch (Exception e) {
                        log.error("Unable to open database with given passphrase; this shouldn't happen: " + e);
                    }

                    break;
                case MainActivity.MSG_NEW_MESSAGE_CREATED:
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.updateHashes(sendBlacklist);
                        bluetoothServiceThread.updateData();
                    }

                    if (zeroconfServiceThread != null) {
                        zeroconfServiceThread.updateHashes(sendBlacklist);
                        zeroconfServiceThread.updateData();
                    }

                    break;
                case MainActivity.MSG_MESSAGE_DELETED:
                    if (bluetoothServiceThread != null) {
                        bluetoothServiceThread.updateHashes(sendBlacklist);
                        bluetoothServiceThread.updateData();
                    }

                    if (zeroconfServiceThread != null) {
                        zeroconfServiceThread.updateHashes(sendBlacklist);
                        zeroconfServiceThread.updateData();
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
        SQLiteDatabase.loadLibs(this);
        if (messagesProviderHelper == null) {
            messagesProviderHelper = MessagesProviderHelper.getInstance(getApplicationContext());
        }


        // Setup intent filters and receivers for network connectivity changes
        networkConnectivityFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        //btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getApplicationContext().registerReceiver(networkConnectivityReceiver, networkConnectivityFilter);

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

        if (zeroconfServiceThread != null) {
            zeroconfServiceThread.unregisterService();
            zeroconfServiceThread.cancel();
        }

        if (nexusServiceThread != null) {
            nexusServiceThread.cancel();
        }

    }

    /**
     * Helper method for starting zeroconf service thread
     */
    private void startZeroconfServiceThread() {
        if (zeroconfEnabled == 1) {
            zeroconfServiceThread = new ZeroconfServiceThread(getApplicationContext(), clients, sendBlacklist);
            zeroconfServiceThread.setScanFrequency(zeroconfScanFrequency);
            log.info("Starting our zeroconf service thread...");
            zeroconfServiceThread.start();
            notificationFlags |= ZEROCONF_FLAG;
            updateNotification();
        }
    }

    /**
     * Helper method for stopping zeroconf service thread
     */
    private void stopZeroconfServiceThread() {
        if (zeroconfServiceThread != null) {
            zeroconfServiceThread.unregisterService();
            zeroconfServiceThread.cancel();
            log.debug("Stopping zeroconf service thread");
        }
    }


    /**
     * Show a notification while the service is running
     */
    private void showNotification() {

        //CharSequence text = getText(R.string.service_started);

        // Set icon, scrolling text, and timestamp
        notification = new Notification(R.drawable.fluid_nexus_icon, getNotificationText(), System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        // The PendingIntent to launch the activity of the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        // Set the info for the view that show in the notification panel
        notification.setLatestEventInfo(this, getText(R.string.service_label), getNotificationText(), contentIntent);

        nm.notify(NOTIFICATION, notification);
    }

    /**
     * Update the notification
     */
    private void updateNotification() {
        // The PendingIntent to launch the activity of the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        // Set the info for the view that show in the notification panel
        notification.setLatestEventInfo(this, getText(R.string.service_label), getNotificationText(), contentIntent);

        nm.notify(NOTIFICATION, notification);

    }

    /**
     * Get the current notification text
     * @return notificationText the text for the notification
     */
    private String getNotificationText() {
        String notificationText = "";

        if (0 != (notificationFlags & BLUETOOTH_FLAG)) {
            notificationText = getText(R.string.notification_bluetooth).toString();
        }

        if (0 != (notificationFlags & ZEROCONF_FLAG)) {
            if (notificationText.length() > 0) {
                notificationText = notificationText + ", " + getText(R.string.notification_zeroconf).toString();
            } else {
                notificationText = (String) getText(R.string.notification_zeroconf).toString();

            }
        }

        if (0 != (notificationFlags & NEXUS_FLAG)) {
            if (notificationText.length() > 0) {
                notificationText = notificationText + ", " + getText(R.string.notification_nexus).toString();
            } else {
                notificationText = (String) getText(R.string.notification_nexus).toString();

            }
        }

        if (notificationText.length() == 0) {
            notificationText = getText(R.string.notification_starting).toString();
        }

        return notificationText;

    }
}
