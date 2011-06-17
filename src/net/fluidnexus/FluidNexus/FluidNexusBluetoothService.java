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
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

/*
 * TODO
 * * Create cursors for getting hashes and items from database
 * * Create intent in activity to notify service when a new item has been created
 * * Update activity to allow for deletion
 */

public class FluidNexusBluetoothService extends Service {
    // Logging
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    // For database access
    private FluidNexusDbAdapter dbHelper;
    private Cursor hashesCursor;
    private Cursor dataCursor;

    private BluetoothAdapter bluetoothAdapter;

    // Keeping track of items from the database
    private ArrayList<String> currentHashes = new ArrayList<String>();
    private ArrayList<Vector> currentData = new ArrayList<Vector>();
    private Vector<String> currentItem = new Vector<String>();

    private ArrayList<Vector> devices = new ArrayList<Vector>();
    private Vector<String> device = new Vector<String>();

    private IntentFilter btFoundFilter;
    private IntentFilter sdpFilter;

    private BluetoothServiceThread serviceThread = null;


    // Thread Handler message constants
    private final int CONNECTED_THREAD_FINISHED = 0x30;

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
                devices.add(device);
                
                // Print this info to the log, for now
                log.info(foundDevice.getName() + " " + foundDevice.getAddress());
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // Clear out our device list
                devices.clear();
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
        return null;
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

        String action = "android.bleutooth.device.action.UUID";
        IntentFilter sdpFilter = new IntentFilter(action);
        this.registerReceiver(sdpReceiver, sdpFilter);

        // setup database object
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();



        // Show a notification regarding the service
        showNotification();

        if (serviceThread == null) {
            serviceThread = new BluetoothServiceThread();
            log.debug("Starting our bluetooth service thread...");
            serviceThread.start();
        }
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
        private ConnectThread connectThread;

        private BluetoothSocket socket;
        private ArrayList<HashMap> connectedThreadMap = new ArrayList<HashMap>();

        /**
         * Constructor for the thread
         */
        public BluetoothServiceThread() {
            setName("FluidNexusBluetoothServiceThread");
        }

        /**
         * Handler that receives information from the threads
         */
        private final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTED_THREAD_FINISHED:

                        break;
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
                        doDiscoveryFinished();
                        break;
                    case STATE_SERVICES:
                        // If we're in service discovery, just continue
                        // TODO
                        // still need this?
                        break;
                    case STATE_CONNECTING:
                        // If we're connecting, just continue
                        // TODO
                        // still need this?
                        break;
                    default:
                        break;
                }
            }
        }

        /**
         * Start a thread for connection to remove FluidNexus device
         * @param device BluetoothDevice to connect to
         * @param secure Socket security type
         */
        public synchronized void connect(BluetoothDevice device, boolean secure) {
            log.debug("Connecting to: " + device);
    
            // cancel any thread trying to connect
            if (state == STATE_CONNECTING) {
                if (connectThread != null) {
                    connectThread.cancel();
                    connectThread = null;
                }
            }
    
            // State the thread that connects to the remove device
            connectThread = new ConnectThread(device, true);
            HashMap threadMap = new HashMap();
            //threadMap.put(device.getAddress(), connectThread);
            
            connectThread.start();
            setServiceState(STATE_CONNECTING);
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
            hashesCursor = dbHelper.services();
            hashesCursor.moveToFirst();
            currentHashes.clear();

            while (hashesCursor.isAfterLast() == false) {
                currentHashes.add(hashesCursor.getString(1));
                hashesCursor.moveToNext();
            }
            hashesCursor.close();

            dataCursor = dbHelper.outgoing();
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
        private void doDiscoveryFinished() {
            for (Vector currentDevice : devices) {
                String name = (String) currentDevice.get(0);
                String address = (String) currentDevice.get(1);
                log.debug("Working on device " + name + " with address " + address);
                BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(address);

                connect(btDevice, true);
            }
        }

        /**
         * Run the actions to do with service discovery
         */
        private void doServiceDiscovery() {
            UUID tempUUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

            for (Vector currentDevice : devices) {
                String name = (String) currentDevice.get(0);
                String address = (String) currentDevice.get(1);
                log.debug("Working on device " + name + " with address " + address);
                BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(address);
                servicesFromDeviceAsync(btDevice);
            }
        }

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
        private String socketType;

        //private final InputStream inputStream;
        //private final OutputStream outputStream;
        private DataInputStream inputStream = null;
        private DataOutputStream outputStream = null;

        private ArrayList<String> hashList = new ArrayList<String>();

        private char connectedState = 0x00;
        private final char DISCONNECTED = 0x00;
        private final char HELO = 0x10;
        private final char HASH_LIST = 0x20;
        private final char HASH_LIST_CONTINUATION = 0x21;
        private final char HASH_REQUEST = 0x30;
        private final char SWITCH = 0x40;
        private final char DONE_DONE = 0xF0;
        private final char DONE_HASHES = 0xF1;
        private final char CLOSE_CONNECTION = 0xFF;



        //public ConnectedThread(BluetoothSocket remoteSocket, String socketType) {
        public ConnectThread(BluetoothDevice remoteDevice, boolean secure) {
            setName("FluidNexusConnectThread: " + remoteDevice.getAddress());
            device = remoteDevice;
            BluetoothSocket tmp = null;
            socketType = secure ? "Secure" : "Insecure";

            // Get our socket to the remove device
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(FluidNexusUUID);
                } else {
                    // TODO
                    // get rid of the secure, insecure distinction
                }
            } catch (IOException e) {
                log.error("Socket Type: " + socketType + "create() failed: " + e);
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
            }


            DataInputStream tmpIn = null;
            DataOutputStream tmpOut = null;

            setConnectedState(DISCONNECTED);

            try {
                tmpIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                tmpOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            } catch (IOException e) {
                log.error("Temp stream sockets not created");
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        /**
         * Set the state of the connected thread
         * @param state Int that defines the connected thread state
         */
        private synchronized void setConnectedState(char newState) {
            String tmpNewState = Integer.toHexString(Character.digit(newState, 16));
            String tmpConnectedState = Integer.toHexString(Character.digit(connectedState, 16));
            log.debug("Changing connected thread state from " + tmpConnectedState + " to " + tmpNewState);
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
            }

            // Read result (number of hashes we're expecting)
            int numHashes = -1;
            try {
                numHashes = (int) inputStream.readChar();
                log.debug("Expecting to receive number of hashes: " + numHashes);
            } catch (IOException e) {
                log.error("Exception during reading from inputStream: " + e);
            }

            // Read the hashes
            for (int i = 0; i < numHashes; i++) {
                byte[] hash = new byte[32];
                try {
                    inputStream.readFully(hash);
                } catch (IOException e) {
                    log.error("Exception during reading from inputStream: " + e);
                }
                String tmp = new String(hash);
                hashList.add(tmp);
                log.debug("received hash: " + tmp);
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

                    byte[] title = new byte[titleLength];
                    inputStream.readFully(title);
                    tmp = new String(title);
                    log.debug("Title is: " + tmp);

                    // TODO
                    // This is probably no good for very long messages...
                    byte[] message = new byte[messageLength];
                    inputStream.readFully(message);
                    tmp = new String(message);
                    log.debug("Message is: " + tmp);
     
                }

                setConnectedState(DONE_DONE);

            } catch (IOException e) {
                log.error("Exception during writing to outputStream in requestHashes: " + e);
                this.cancel();
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
                    case DONE_DONE:
                        sendDoneDone();
                    case 0xFF:
                        //log.debug("we should never have gotten here...");
                    default:
                        break;
                }            
            }

            /*
            // Do sending process over all data
            // TODO
            // Only send if we need to...this is just for testing
            
            // TODO
            // Need to figure out why only one item is being set at a time, even though we're looping through all of the values...
            for (Vector item: currentData) {
                // TODO
                // Enable reading from the connected device
                // This is just a test using version 01 of the protocol
                String version = "01";
                String hash = (String) item.get(0);
                String timestamp = (String) item.get(1);
                String title = (String) item.get(2);
                String message = (String) item.get(3);
                log.debug("Current title is: " + title);
    
                // VERSION
                byte[] send = version.getBytes();
                write(send);
    
                // TITLE LENGTH
                String titleLength = String.format("%03d", title.length());
                send = titleLength.getBytes();
                write(send);
    
                // MESSAGE LENGTH
                String messageLength = String.format("%06d", message.length());
                send = messageLength.getBytes();
                write(send);
    
                // TIMESTAMP 
                String timestampString = timestamp.toString();
                send = timestampString.getBytes();
                write(send);
    
                // HASH
                send = hash.getBytes();
                write(send);
    
                // TITLE
                send = title.getBytes();
                write(send);
    
                // MESSAGE
                send = message.getBytes();
                write(send);

                try {
                    this.sleep(200);
                } catch (InterruptedException e) {
                    log.error("Thread sleeping interrupted: " + e);
                }
            }
            */

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
            }

            log.info("Closing the socket and ending the thread");
            setConnectedState(CLOSE_CONNECTION);
           
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("close() of ConnectedThread socket failed: " + e);
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
