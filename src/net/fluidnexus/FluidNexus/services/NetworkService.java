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
 * * Abstract the sending of data over to sockets so that it works with any modality (zeroconf, ad-hoc, etc.)
 * * Improve error handling dramatically
 */

public class NetworkService extends Service {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // For database access
    private MessagesProviderHelper messagesProviderHelper = null;

    private Cursor hashesCursor;
    private Cursor dataCursor;

    private BluetoothAdapter bluetoothAdapter;

    private HashSet<BluetoothDevice> pairedDevices = null;

    // Keeping track of items from the database
    private HashSet<String> currentHashes = new HashSet<String>();
    private ArrayList<Vector> currentData = new ArrayList<Vector>();


    private HashSet<BluetoothDevice> allDevicesBT = new HashSet<BluetoothDevice>();
    private HashSet<BluetoothDevice> fnDevicesBT = new HashSet<BluetoothDevice>();
    private HashSet<BluetoothDevice> connectedDevices = new HashSet<BluetoothDevice>();
    private Vector<String> device = new Vector<String>();

    private IntentFilter btFoundFilter;
    private IntentFilter sdpFilter;

    private BluetoothServiceThread serviceThread = null;
    private BluetoothServiceThread serviceThreadPaired = null;

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();
    public static final int MSG_REGISTER_CLIENT = 0x10;
    public static final int MSG_UNREGISTER_CLIENT = 0x11;
    public static final int MSG_NEW_MESSAGE_RECEIVED = 0x20;
    public static final int MSG_BLUETOOTH_SCAN_FREQUENCY = 0x30;

    private int scanFrequency = 120;

    // Target we publish for clients to send messages to
    final Messenger messenger = new Messenger(new IncomingHandler());

    // Thread Handler message constants
    private final int CONNECT_THREAD_FINISHED = 0x30;
    private final int UPDATE_HASHES = 0x31;

    // UUID
    private static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");

    // State of the system
    private int state;

    // Potential states
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_DISCOVERY = 1; // we're discovering things
    public static final int STATE_DISCOVERY_FINISHED = 2; // we're done discovering things and can now move on
    public static final int STATE_SERVICES = 3; // we're discovering services
    public static final int STATE_CONNECTING = 4; // we're connecting other devices
    public static final int STATE_CONNECTED = 5; // we're connected and sending data
    public static final int STATE_WAIT_FOR_CONNECTIONS = 6; // we wait for all of the connection threads to finish
    public static final int STATE_SERVICE_WAIT = 7; // we sleep for a bit before beginning the discovery process anew
    public static final int STATE_QUIT = 100; // we're done with everything

    private NotificationManager nm;
    private int NOTIFICATION = R.string.service_started;
    
    // Timers
    // TODO
    // implement timers :-)
    private Timer timer;
    
    /**
     * BroadcastReceiver for the bluetooth discovery responses
     */
    private final BroadcastReceiver btDiscoveryReceiver = new                   BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // get bluetoothdevice object from intent
                BluetoothDevice foundDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (allDevicesBT.contains(foundDevice)) {
                    log.debug("Found device in paired list: " + foundDevice);
                } else {
                    device.clear();
                    device.add(foundDevice.getName());
                    device.add(foundDevice.getAddress());
                    allDevicesBT.add(foundDevice);
                    // Print this info to the log, for now
                    log.info(foundDevice.getName() + " " + foundDevice.getAddress());
                }

                
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // Clear out our device list
                allDevicesBT.clear();
                fnDevicesBT.clear();
                

                addPairedDevices();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setServiceState(STATE_DISCOVERY_FINISHED);
            }
        }
    };

    /**
     * BroadcastReceiver for the results of SDP, in UUID form
     */
    private final BroadcastReceiver sdpReceiver = new                   BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice deviceExtra = intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Parcelable[] uuidExtra = intent.getParcelableArrayExtra("android.bluetooth.device.extra.UUID");
            log.debug("doing service discovery...");

            if (uuidExtra != null) {
                for (Parcelable uuid: uuidExtra) {
                    log.debug("Found: " + uuid.toString());
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
                    log.debug("Changing scan frequency to: " + msg.arg1);
                    scanFrequency = msg.arg1;
                    break;
                case MainActivity.MSG_NEW_MESSAGE_CREATED:
                    if (serviceThread != null) {
                        serviceThread.updateHashes();
                        serviceThread.updateData();
                    }
                    log.debug("MSG_NEW_MESSAGE_CREATED received");
                    break;
                case MainActivity.MSG_MESSAGE_DELETED:
                    log.debug("MSG_MESSAGE_DELETED received");
                    if (serviceThread != null) {
                        serviceThread.updateHashes();
                        serviceThread.updateData();
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

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;

        btFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(btDiscoveryReceiver, btFoundFilter);

        /*
        String action = "android.bleutooth.device.action.UUID";
        IntentFilter sdpFilter = new IntentFilter(action);
        this.registerReceiver(sdpReceiver, sdpFilter);
        */

        // setup database object
        messagesProviderHelper = new MessagesProviderHelper(this);

        // Show a notification regarding the service
        showNotification();

        if (serviceThread == null) {
            boolean paired = true;
            serviceThread = new BluetoothServiceThread(paired);
            log.debug("Starting our bluetooth service thread for discovered and paired devices...");
            serviceThread.start();
        }

        /*
        if (serviceThreadPaired == null) {
            boolean paired = true;
            serviceThreadPaired = new BluetoothServiceThread(paired);
            log.debug("Starting our bluetooth service thread for paired devices...");
            serviceThreadPaired.start();
        }
        */
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
        if (serviceThread != null) {
            serviceThread.cancel();
        }
    }

    /**
     * Add our paired devices to all devices
     */
    private void addPairedDevices() {
        Set<BluetoothDevice> tmp = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device: tmp) {
            allDevicesBT.add(device);
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

    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current bluetooth service state
     */
    private synchronized void setServiceState(int newState) {
        //log.debug("Changing state from " + state + " to " + newState);
        state = newState;
    }

    /**
     * Get the current state value
     */
    private synchronized int getServiceState() {
        return state;
    }



    /**
     * Get a list of services, in UUID form, for a given device; taken from http://wiresareobsolete.com/wordpress/2010/11/android-bluetooth-rfcomm/
     * @param device The BluetoothDevice we're inspecting
     */
    public ParcelUuid[] servicesFromDevice(BluetoothDevice device) {
        try {
            Class cl = Class.forName("android.bluetooth.BluetoothDevice");
            Class[] par = {};
            Method method = cl.getMethod("getUuids", par);
            Object[] args = {};
            ParcelUuid[] retval = (ParcelUuid[])method.invoke(device, args);
            return retval;
        } catch (Exception e) {
            log.error("Error getting services: " + e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get a list of services, in UUID form, for a given device; taken from http://wiresareobsolete.com/wordpress/2010/11/android-bluetooth-rfcomm/
     */
    public void servicesFromDeviceAsync(BluetoothDevice device) {
        try {
            Class cl = Class.forName("android.bluetooth.BluetoothDevice");
            Class[] par = {};
            Method method = cl.getMethod("fetchUuidsWithSdp", par);
            Object[] args = {};
            method.invoke(device, args);
        } catch (Exception e) {
            log.error("Error getting services: " + e);
            e.printStackTrace();
        }
    }


    /**
     * This thread runs all of the device/service discovery and socket communication
     */
    private class BluetoothServiceThread extends Thread {
        // Threads

        private BluetoothSocket socket;
        private boolean paired = false;

        /**
         * Constructor for the thread that does discovery
         */
        public BluetoothServiceThread(boolean examinePaired) {

            paired = examinePaired;

            setName("BluetoothServiceThread-Paired+Discovery");

            updateHashes();
            updateData();
        }

        /**
         * Handler that receives information from the threads
         */
        private final Handler threadHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECT_THREAD_FINISHED:
                        String doneAddress = msg.getData().getString("address");
                        log.debug("Done with device " + doneAddress);
                        for (BluetoothDevice connectedDevice: connectedDevices) {
                            String address = connectedDevice.getAddress();
                            if (address.equals(doneAddress)) {
                                connectedDevices.remove(connectedDevice);
                                log.debug("Removed device from list of connected devices");
                                break;
                            }
                        }
                        break;
                    case UPDATE_HASHES:
                        updateHashes();
                    default:
                        break;

                }
            }
        };

        /**
         * Begin the thread, and thus the service main loop
         */
        public void run() {

                while (getServiceState() != STATE_QUIT) {
                    switch (getServiceState()) {
                        case STATE_NONE:
                            doStateNone();
                            break;
                        case STATE_DISCOVERY:
                            // If we're discovering things, just continue
                            break;
                        case STATE_DISCOVERY_FINISHED:
                            // If there discovery is finished, start trying to connect
                            //doServiceDiscovery();
                            fnDevicesBT = allDevicesBT;
                            setServiceState(STATE_CONNECTING);
                            break;
                        case STATE_CONNECTING:
                            doConnectToDevices();
                            break;
                        case STATE_WAIT_FOR_CONNECTIONS:
                            if (connectedDevices.isEmpty()) {
                                setServiceState(STATE_SERVICE_WAIT);
                            }
                            break;
                        case STATE_SERVICE_WAIT:
                            waitService();
                        default:
                            break;
                    }
                }
        }

        /**
         * Start a thread for connection to remove FluidNexus device
         * @param device BluetoothDevice to connect to
         */
        public synchronized void connect(BluetoothDevice device) {
            log.debug("Connecting to: " + device);

            // State the thread that connects to the remove device only if we're not already connecting
            //ConnectThread connectThread = new ConnectThread(device, threadHandler);
            BluetoothClientThread connectThread = new BluetoothClientThread(getApplicationContext(), device, threadHandler, clients);
            connectThread.setHashes(currentHashes);
            connectThread.setData(currentData);
            connectedDevices.add(device);            
            connectThread.start();
        }

        /**
         * Update the hashes in our HashSet based on items from the database
         */
        public void updateHashes() {
            //hashesCursor = dbAdapter.services();
            //hashesCursor.moveToFirst();
            hashesCursor = messagesProviderHelper.hashes();

            currentHashes = new HashSet<String>();
            currentHashes.clear();

            while (hashesCursor.isAfterLast() == false) {
                // Get the hash from the cursor
                currentHashes.add(hashesCursor.getString(hashesCursor.getColumnIndex(MessagesProvider.KEY_MESSAGE_HASH)));
                hashesCursor.moveToNext();
            }
            hashesCursor.close();

        }

        /**
         * Update the data based on items from the database
         * TODO
         * Probably shouldn't do this atomically like this...
         */
        public void updateData() {
            dataCursor = messagesProviderHelper.outgoing();
            dataCursor.moveToFirst();
            currentData.clear();

            String[] fields = new String[] {MessagesProvider.KEY_MESSAGE_HASH, MessagesProvider.KEY_TIME, MessagesProvider.KEY_TITLE, MessagesProvider.KEY_CONTENT};
            while (dataCursor.isAfterLast() == false) {
                // I'm still not sure why I have to instantiate a new vector each time here, rather than using the local vector from earlier
                // This is one of those things of java that just makes me want to pull my hair out...
                Vector<String> tempVector = new Vector<String>();
                for (int i = 0; i < fields.length; i++) {
                    int index = dataCursor.getColumnIndex(fields[i]);
                    tempVector.add(dataCursor.getString(index));
                }
                currentData.add(tempVector);
                dataCursor.moveToNext();
            }
            dataCursor.close();
        }


        /**
         * Run the actions to do with moving from the initial state
         */
        private void doStateNone() {
            // If we're at the beginning state, then start the discovery process

            try {
                doDiscovery();
            } catch (Exception e) {
                log.error("some sort of exception: " + e);
            }
        }

        /**
         * Run the actions to do with device discovery
         */
        private void doDiscovery() {
            setServiceState(STATE_DISCOVERY);
            bluetoothAdapter.startDiscovery();
        }

        /**
         * Run the actions after we've done discovery
         */
        private void doServiceDiscovery() {
            
            for (BluetoothDevice currentDevice : allDevicesBT) {
                log.debug("Working on services discovery for " + currentDevice.getName() + " with address " + currentDevice.getAddress());
                ParcelUuid[] uuids = servicesFromDevice(currentDevice);

                if (uuids != null) {
                    for (Parcelable uuid: uuids) {
                        log.debug("Found: " + uuid.toString());
                        UUID tmpUUID = UUID.fromString(uuid.toString());
                        if (tmpUUID.equals(FluidNexusUUID)) {
                            log.debug("Fluid Nexus service found running on " + currentDevice.getName());
                            fnDevicesBT.add(currentDevice);
                        }
                    }
                }
            }
            setServiceState(STATE_CONNECTING);

        }

        private void doConnectToDevices() {
            // Find those new devices that we haven't already connected to
            HashSet<BluetoothDevice> difference = new HashSet<BluetoothDevice>(fnDevicesBT);
            difference.removeAll(connectedDevices);
            
            updateHashes();
            updateData();
            for (BluetoothDevice currentDevice : difference) {
                log.debug("Trying to connect to " + currentDevice.getName() + " with address " + currentDevice.getAddress());

                connect(currentDevice);
            }

            setServiceState(STATE_WAIT_FOR_CONNECTIONS);

        }

        private void waitService() {
            try {
                log.debug("Service thread sleeping for " + scanFrequency + " seconds...");
                this.sleep(scanFrequency * 1000);
            } catch (InterruptedException e) {
                log.error("Thread sleeping interrupted: " + e);
            }

            setServiceState(STATE_NONE);
        }

        /** 
         * Cancel the thread
         */
        public void cancel() {

        }
    }

    /*
     * Auxilliary methods that probably should be moved to a utility class somewhere
     */

    /**
     * Make a MD5 hash of the input string
     * @param inputString Input string to create an MD5 hash of
     */
    public static String makeMD5(String inputString) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(inputString.getBytes());

            String md5 = toHexString(messageDigest);
            return md5;
        } catch(NoSuchAlgorithmException e) {
            log.error("MD5" + e.getMessage());
            return null;
        }
    }

    /**
     * Make a SHA-256 hash of the input string
     * @param inputString Input string to create an MD5 hash of
     */
    public static String makeSHA256(String inputString) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(inputString.getBytes());

            String sha256 = toHexString(messageDigest);
            return sha256;
        } catch(NoSuchAlgorithmException e) {
            log.error("SHA-256" + e.getMessage());
            return null;
        }
    }


    /**
     * Take a byte array and turn it into a hex string
     * @param bytes Array of bytes to convert
     * @note Taken from http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-le
     */
    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v/16];
            hexChars[j*2 + 1] = hexArray[v%16];
        }
        return new String(hexChars);
    }

}
