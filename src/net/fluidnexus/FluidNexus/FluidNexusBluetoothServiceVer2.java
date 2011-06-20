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


package net.fluidnexus.FluidNexus;

import java.lang.reflect.Method;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

/*
 * TODO
 * * Ensure multiple threads are opened for multiple hosts running the software
 * * See if it's possible to manually enter paired devices to speed up creation of the network
 * * Abstract the sending of data over to sockets so that it works with any modality (zeroconf, ad-hoc, etc.)
 * * Improve error handling dramatically
 */

public class FluidNexusBluetoothServiceVer2 extends Service {
    // Logging
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    // For database access
    private FluidNexusDbAdapter dbAdapter;
    private Cursor hashesCursor;
    private Cursor dataCursor;

    private BluetoothAdapter bluetoothAdapter;

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

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();
    public static final int MSG_REGISTER_CLIENT = 0x10;
    public static final int MSG_UNREGISTER_CLIENT = 0x11;
    public static final int MSG_NEW_MESSAGE_RECEIVED = 0x20;

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
    public static final int STATE_SERVICE_WAIT = 6; // we sleep for a bit before beginning the discovery process anew
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

                device.clear();
                device.add(foundDevice.getName());
                device.add(foundDevice.getAddress());
                allDevicesBT.add(foundDevice);
                
                // Print this info to the log, for now
                log.info(foundDevice.getName() + " " + foundDevice.getAddress());
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // Clear out our device list
                allDevicesBT.clear();
                fnDevicesBT.clear();
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
                    break;
                case FluidNexusAndroid.MSG_NEW_MESSAGE_CREATED:
                    if (serviceThread != null) {
                        serviceThread.updateHashes();
                        serviceThread.updateData();
                    }
                    log.debug("MSG_NEW_MESSAGE_CREATED received");
                    break;
                case FluidNexusAndroid.MSG_MESSAGE_DELETED:
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
        log.debug("Service creating...");
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
        dbAdapter = new FluidNexusDbAdapter(this);
        dbAdapter.open();



        // Show a notification regarding the service
        showNotification();

        if (serviceThread == null) {
            serviceThread = new BluetoothServiceThread();
            log.debug("Starting our bluetooth service thread...");
            serviceThread.start();
        }
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
        log.debug("Service destroying...");
        nm.cancel(NOTIFICATION);
        if (serviceThread != null) {
            serviceThread.cancel();
        }
    }

    /**
     * Show a notification while the service is running
     */
    private void showNotification() {
        log.debug("Showing notification...");
        CharSequence text = getText(R.string.service_started);

        // Set icon, scrolling text, and timestamp
        Notification notification = new Notification(R.drawable.fluid_nexus_icon, text, System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        // The PendingIntent to launch the activity of the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, FluidNexusAndroid.class), 0);

        // Set the info for the view that show in the notification panel
        notification.setLatestEventInfo(this, getText(R.string.service_label), text, contentIntent);

        nm.notify(NOTIFICATION, notification);
    }

    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current bluetooth service state
     */
    private synchronized void setServiceState(int newState) {
        log.debug("Changing state from " + state + " to " + newState);
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


        /**
         * Constructor for the thread
         */
        public BluetoothServiceThread() {
            setName("FluidNexusBluetoothServiceThread");
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
                        doServiceDiscovery();
                        break;
                    case STATE_CONNECTING:
                        doConnectToDevices();
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

            /*
             * TODO
             * This would seem to stop any potential parallel connections from happing.
             */
            /*    
            // cancel any thread trying to connect
            if (state == STATE_CONNECTING) {
                if (connectThread != null) {
                    connectThread.cancel();
                    connectThread = null;
                }
            }
            */

            // State the thread that connects to the remove device only if we're not already connecting
            ConnectThread connectThread = new ConnectThread(device, threadHandler);
            connectedDevices.add(device);            
            connectThread.start();
        }

        /**
         * Update the hashes in our HashSet based on items from the database
         */
        public void updateHashes() {
            hashesCursor = dbAdapter.services();
            hashesCursor.moveToFirst();

            currentHashes = new HashSet<String>();
            currentHashes.clear();

            while (hashesCursor.isAfterLast() == false) {
                // TODO
                // change this to get the item based on the column name
                currentHashes.add(hashesCursor.getString(1).toUpperCase());
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
            dataCursor = dbAdapter.outgoing();
            dataCursor.moveToFirst();
            currentData.clear();

            String[] fields = new String[] {FluidNexusDbAdapter.KEY_HASH, FluidNexusDbAdapter.KEY_TIME, FluidNexusDbAdapter.KEY_TITLE, FluidNexusDbAdapter.KEY_DATA};
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
            // TESTING
            // Try and get hashes from the database
            // TODO
            // Only update this on a new intent from the activity


            try {
                doDiscovery();
            } catch (Exception e) {
                log.debug("some sort of exception: " + e);
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

            for (BluetoothDevice currentDevice : difference) {
                log.debug("Trying to connect to " + currentDevice.getName() + " with address " + currentDevice.getAddress());

                connect(currentDevice);
            }

            setServiceState(STATE_SERVICE_WAIT);

        }

        private void waitService() {
            // TODO
            // make this configurable?
            try {
                log.debug("Service thread sleeping...");
                this.sleep(5000);
            } catch (InterruptedException e) {
                log.error("Thread sleeping interrupted: " + e);
            }

            setServiceState(STATE_NONE);
        }

        /**
         * Run the actions to do with service discovery
         */
        /*
        private void doServiceDiscovery() {
            UUID tempUUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

            for (Vector currentDevice : allDevices) {
                String name = (String) currentDevice.get(0);
                String address = (String) currentDevice.get(1);
                log.debug("Working on device " + name + " with address " + address);
                BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(address);
                servicesFromDeviceAsync(btDevice);
            }
        }
        */

        /** 
         * Cancel the thread
         */
        public void cancel() {

        }
    }

    /**
     * Thread that actually sends data to a connected device
     * TODO
     * probably need to move some of the socket creation bits to another part of the class
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        //private final InputStream inputStream;
        //private final OutputStream outputStream;
        private DataInputStream inputStream = null;
        private DataOutputStream outputStream = null;

        private ArrayList<String> hashList = new ArrayList<String>();

        private final Handler threadHandler;

        private char connectedState = 0x00;
        private final char DISCONNECTED = 0x00;
        private final char HELO = 0x10;
        private final char HASH_LIST = 0x20;
        private final char HASH_LIST_CONTINUATION = 0x21;
        private final char HASH_REQUEST = 0x30;
        private final char SWITCH = 0x40;
        private final char SWITCH_DONE = 0x41;
        private final char DONE_DONE = 0xF0;
        private final char DONE_HASHES = 0xF1;
        private final char CLOSE_CONNECTION = 0xFF;



        public ConnectThread(BluetoothDevice remoteDevice, Handler givenHandler) {
            setName("FluidNexusConnectThread: " + remoteDevice.getAddress());
            device = remoteDevice;
            threadHandler = givenHandler;

            BluetoothSocket tmp = null;

            // Get our socket to the remove device
            try {
                tmp = device.createRfcommSocketToServiceRecord(FluidNexusUUID);
            } catch (IOException e) {
                log.error("create() failed: " + e);
                cleanupConnection();
            }

            // Save our socket
            socket = tmp;

            // Try to connect
            try {
                socket.connect();
            } catch (IOException e) {
                // Try to close the socket
                try {
                    socket.close();
                } catch (IOException e2) {
                    log.error("unable to close() socket during connection failure: " + e2);
                }
                cleanupConnection();
            }


            DataInputStream tmpIn = null;
            DataOutputStream tmpOut = null;

            setConnectedState(DISCONNECTED);

            try {
                tmpIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                tmpOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            } catch (IOException e) {
                log.error("Temp stream sockets not created");
                cleanupConnection();
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        private void sendNewMessageMsg() {
            for (int i = 0; i < clients.size(); i++) {
                try {
                    clients.get(i).send(Message.obtain(null, MSG_NEW_MESSAGE_RECEIVED));
                } catch (RemoteException e) {
                    // If we get here, the client is dead, and we should remove it from the list
                    log.debug("Removing client: " + clients.get(i));
                    clients.remove(i);
                }
            }
        }

        /**
         * Set the state of the connected thread
         * @param state Int that defines the connected thread state
         */
        private synchronized void setConnectedState(char newState) {
            //String tmpNewState = Integer.toHexString(Character.digit(newState, 16));
            //String tmpConnectedState = Integer.toHexString(Character.digit(connectedState, 16));
            log.debug("Changing connected thread state from " + connectedState + " to " + newState);
            connectedState = newState;
        }
    
        /**
         * Get the current state value
         */
        private synchronized char getConnectedState() {
            return connectedState;
        }

        /**
         * Setup our connection
         */
        private void sendHELO() {
            // Send the command to the server
            try {
                outputStream.writeChar(HELO);
                outputStream.flush();
            } catch (IOException e) {
                log.error("Exception during writing to outputStream: " + e);
                cleanupConnection();
            }

            // Read back the result
            try {
                char tmp = inputStream.readChar();
                if (tmp == HELO) {
                    log.debug("got HELO");
                    setConnectedState(HELO);
                } else {
                    log.debug("Received: " + tmp);
                }
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
                cleanupConnection();
            }

        }

        /**
         * Send done done command
         */
        private void sendDoneDone() {
            // Send the command to the server
            try {
                outputStream.writeChar(DONE_DONE);
                outputStream.flush();
            } catch (IOException e) {
                log.error("Exception during writing to outputStream: " + e);
                cleanupConnection();
            }

            // Read back the result
            try {
                char tmp = inputStream.readChar();
                if (tmp == DONE_DONE) {
                    log.debug("got DONE_DONE");
                    // TODO
                    // send message through handler to let parent service know it can reap us
                } else {
                    log.debug("Received: " + tmp);
                }
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
                cleanupConnection();
            }

            cleanupConnection();

        }


        /**
         * Send hash request to server
         */
        public void requestHashList() {
            // Send HASH_LIST command
            try {
                outputStream.writeChar(HASH_LIST);
                outputStream.flush();
            } catch (IOException e) {
                log.error("Exception during writing to outputStream: " + e);
                cleanupConnection();
            }

            // Read result (number of hashes we're expecting)
            int numHashes = -1;
            try {
                numHashes = (int) inputStream.readChar();
                log.debug("Expecting to receive number of hashes: " + numHashes);
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
                cleanupConnection();
            }

            // Read the hashes
            for (int i = 0; i < numHashes; i++) {
                byte[] hashBytes = new byte[32];
                try {
                    inputStream.readFully(hashBytes);
                } catch (IOException e) {
                    log.error("Exception during reading from inputStream: " + e);
                    cleanupConnection();
                }

                String hash = new String(hashBytes);                    
                hash = hash.toUpperCase();
                // Check if we don't already have the hash
                if (!(currentHashes.contains(hash))) {
                    String tmp = new String(hash);
                    hashList.add(tmp);
                    log.debug("received hash: " + tmp);
                }
            }
            setConnectedState(HASH_LIST);
        }

        /**
         * Request the data for each desired hash
         */
        private void requestHashes() {
            // Send HASH_REQUEST command
            try {
                // Go through each hash we have, request data
                for (String currentHash: hashList) {
                    outputStream.writeChar(HASH_REQUEST);
                    outputStream.flush();

                    byte[] send = currentHash.getBytes();
                    outputStream.write(send, 0, send.length);
                    outputStream.flush();
                    
                    int version = inputStream.readUnsignedByte();
                    log.debug("Version is: " + version);

                    int titleLength = inputStream.readInt();
                    log.debug("Title length is: " + titleLength);

                    int messageLength = inputStream.readInt();
                    log.debug("Message length is: " + messageLength);

                    byte[] timestamp = new byte[10];
                    inputStream.readFully(timestamp);
                    String tmp = new String(timestamp);
                    log.debug("Timestamp is: " + tmp);

                    byte[] titleBytes = new byte[titleLength];
                    inputStream.readFully(titleBytes);
                    String title = new String(titleBytes);
                    log.debug("Title is: " + title);

                    // TODO
                    // This is probably no good for very long messages...
                    byte[] messageBytes = new byte[messageLength];
                    inputStream.readFully(messageBytes);
                    String message = new String(messageBytes);
                    log.debug("Message is: " + message);

                    dbAdapter.add_received(0, title, message, "(123,123,123,123)");
                    currentHashes.add(currentHash.toUpperCase());
     
                }
                hashList.clear();
                sendNewMessageMsg();
                Message msg = threadHandler.obtainMessage(UPDATE_HASHES);
                threadHandler.sendMessage(msg);
                setConnectedState(SWITCH);

            } catch (IOException e) {
                log.error("Exception during writing to outputStream in requestHashes: " + e);
                cleanupConnection();
            }

        }

        /**
         * Send the switch command so that this side can start sending our local data
         */
        private void doSwitch() {
            // Send the command to the server
            try {
                outputStream.writeChar(SWITCH);
                outputStream.flush();
            } catch (IOException e) {
                log.error("Exception during writing to outputStream: " + e);
                cleanupConnection();
            }

            // Read back the result
            try {
                char tmp = inputStream.readChar();
                if (tmp == SWITCH) {
                    log.debug("got SWITCH");
                    setConnectedState(HELO);
                } else {
                    log.debug("Received: " + tmp);
                }
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
                cleanupConnection();
            }

            // Enter into our sending loop
            doSendingLoop();
        }

        /**
         * Enter into a loop that sends data to the server in the SWITCH condition.
         */
        private void doSendingLoop() {
            // Read back the result
            boolean done = false;
            while (!done) {
                try {
                    char command = inputStream.readChar();
                    switch (command) {
                        case HASH_LIST:
                            sendHashList();
                            break;
                        case HASH_REQUEST:
                            sendDataForHash();
                            break;
                        case SWITCH_DONE:
                            done = true;
                        default:
                            log.debug("Received unknown command: " + command);
                            done = true;
                    }
                } catch (IOException e) {
                    log.error("Exception during reading from inputStream: " + e);
                    cleanupConnection();
                }
            }

            setConnectedState(DONE_DONE);

        }

        /**
         * Send our local hash list
         */
        private void sendHashList() {
            
            int numHashes = currentHashes.size();

            if (numHashes > 16) {
                log.debug("Number of hashes is larger than 16; unimplemented right now :-(");
                setConnectedState(DONE_DONE);
                return;
            }

            try {
                outputStream.writeChar(numHashes);
    
                for (String currentHash: currentHashes) {
                    log.debug("Sending hash " + currentHash);
                    byte[] send = currentHash.getBytes();
                    outputStream.write(send, 0, send.length);
                    outputStream.flush();
                }
            } catch (IOException e) {
                log.error("Some sort of IO exception: " + e);
                cleanupConnection();
            }
        }

        /**
         * Send data for a particular hash
         */
        private void sendDataForHash() {
            byte[] hashBytes = new byte[32];
            try {
                inputStream.readFully(hashBytes);
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
                cleanupConnection();
            }
            String hash = new String(hashBytes);                    
            log.debug("Sending data for hash: " + hash);
            
            hash = hash.toUpperCase();
            Cursor localCursor = dbAdapter.returnItemBasedOnHash(hash);

            String title = localCursor.getString(localCursor.getColumnIndexOrThrow(FluidNexusDbAdapter.KEY_TITLE));
            String message = localCursor.getString(localCursor.getColumnIndexOrThrow(FluidNexusDbAdapter.KEY_DATA));
            Integer timestamp = localCursor.getInt(localCursor.getColumnIndexOrThrow(FluidNexusDbAdapter.KEY_TIME));
            String timestampString = timestamp.toString();
            localCursor.close();

            try {
                outputStream.writeByte(0x02);
                outputStream.flush();

                outputStream.writeInt(title.length());
                outputStream.flush();

                outputStream.writeInt(message.length());
                outputStream.flush();

                String timestampStringShorter = timestampString.substring(0, 10);                    
                byte[] send = timestampStringShorter.getBytes();
                outputStream.write(send, 0, send.length);
                outputStream.flush();

                byte[] titleBytes = title.getBytes();
                outputStream.write(titleBytes, 0, titleBytes.length);
                outputStream.flush();

                byte[] messageBytes = message.getBytes();
                outputStream.write(messageBytes, 0, messageBytes.length);
                outputStream.flush();

            } catch (IOException e) {
                log.error("Exception during writing to outputStream in requestHashes: " + e);
                cleanupConnection();
            }

        }

        public void run() {
            log.info("Begin ConnectedThread");
            
            while (getConnectedState() != CLOSE_CONNECTION) {
                switch(getConnectedState()) {
                    case DISCONNECTED:
                        sendHELO();
                    case HELO:
                        requestHashList();
                    case HASH_LIST:
                        requestHashes();
                    case SWITCH:
                        doSwitch();
                    case DONE_DONE:
                        sendDoneDone();
                    case 0xFF:
                        //log.debug("we should never have gotten here...");
                    default:
                        break;
                }            
            }

        }

        /**
         * Write to the connected device via an OutputStream
         * @param buffer Bytes to write
         */
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
            } catch (IOException e) {
                log.error("Exception during writing to outputStream: " + e);
                cleanupConnection();
            }
        }

        /**
         * Read from the connected device via an OutputStream
         * @param bytes Number of bytes to read
         * @return byte[] buffer of bytes read, or null otherwise
         */
        public byte[] read(int bytes) {
            byte[] buffer = new byte[bytes];
            try {
                inputStream.read(buffer, 0, bytes);
            } catch (IOException e) {
                log.error("Exception during reading from outputStream: " + e);
                cleanupConnection();
            }

            return buffer;
        }

        /**
         * Flush the output stream
         */
        public void flush() {
            try {
                outputStream.flush();
            } catch (IOException e) {
                log.error("Exception when trying to flush outputStream: " + e);
                cleanupConnection();
            }
        }

        /**
         * Cleanup the connection and exit out of main loop
         */
        public void cleanupConnection() {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("close() of ConnectedThread socket failed: " + e);
                cleanupConnection();
            }

            // TODO
            // seems like this could potentially be a race condition...
            log.info("Closing the socket and ending the thread");
            Message msg = threadHandler.obtainMessage(CONNECT_THREAD_FINISHED);
            Bundle bundle = new Bundle();
            bundle.putString("address", device.getAddress());
            msg.setData(bundle);
            threadHandler.sendMessage(msg);
            setConnectedState(CLOSE_CONNECTION);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("close() of ConnectedThread socket failed: " + e);
                cleanupConnection();
            }
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
     * Take a byte array and turn it into a hex string
     * @param bytes Array of bytes to convert
     * @note Taken from http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-le
     */
    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
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
