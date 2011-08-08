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

import net.fluidnexus.FluidNexusAndroid.Logger;

/**
 * This thread runs all of the device/service discovery and starts threads for socket communication
 */
public class BluetoothServiceThread extends ServiceThread {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // The bluetooth adapter
    private BluetoothAdapter bluetoothAdapter;

    // Intent filters
    private IntentFilter btFoundFilter;
    private IntentFilter sdpFilter;

    // Device lists/info
    private HashSet<BluetoothDevice> pairedDevices = null;
    private HashSet<BluetoothDevice> allDevicesBT = new HashSet<BluetoothDevice>();
    private HashSet<BluetoothDevice> fnDevicesBT = new HashSet<BluetoothDevice>();
    private HashSet<BluetoothDevice> connectedDevices = new HashSet<BluetoothDevice>();
    private Vector<String> device = new Vector<String>();

    // State of the system
    private int state;

    // whether to look for bonded devices only
    private boolean bondedOnly = false;

    // whether or not to send blacklisted messages
    private boolean sendBlacklist = false;

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

    // UUID
    private static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");

    // Threads
    private BluetoothServerThread serverThread = null;

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

    /**
     * Constructor for the thread that does discovery
     */
    public BluetoothServiceThread(Context ctx, ArrayList<Messenger> givenClients, boolean givenSendBlacklist) {
        
        super(ctx, givenClients);        

        sendBlacklist = givenSendBlacklist;

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Setup intent filters and receivers for discovery
        btFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(btDiscoveryReceiver, btFoundFilter);

        setName("BluetoothServiceThread");

        updateHashes(sendBlacklist);
        //updateData();

        // Start the server thread

        if (serverThread == null) {

            serverThread = new BluetoothServerThread(ctx, threadHandler, clients);
            serverThread.setHashes(currentHashes);
            serverThread.setData(currentData);
            serverThread.start();
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
     * set whether or not we do bonded only
     */
    public void setBondedOnly(boolean flag) {
        bondedOnly = flag;
    }

    /**
     * Handler that receives information from the threads
     */
    public final Handler threadHandler = new Handler() {
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
                    updateHashes(sendBlacklist);
                case SCAN_FREQUENCY_CHANGED:
                    // TODO
                    // This is somehow getting called with an argument of 0!
                    // This is certainly a bug, but I can't track it down, as I never explicitly send any messages from the thread
                    // So, check if we get a zero, and if so, don't change the value
                    if (msg.arg1 != 0) {
                        log.debug("Changing scan frequency to: " + msg.arg1);
                        setScanFrequency(msg.arg1);
                    }

                    break;
                default:
                    break;

            }
        }
    };


    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current service state
     */
    @Override
    public synchronized void setServiceState(int newState) {
        //log.debug("Changing state from " + state + " to " + newState);
        state = newState;
    }

    /**
     * Get the current state value
     */
    public synchronized int getServiceState() {
        return state;
    }

    /**
     * Begin the thread, and thus the service main loop
     */
    @Override
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
                    break;
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
        BluetoothClientThread connectThread = new BluetoothClientThread(context, device, threadHandler, clients);
        connectThread.setHashes(currentHashes);
        connectThread.setData(currentData);
        connectedDevices.add(device);            
        connectThread.start();
    }


    /**
     * Run the actions to do with moving from the initial state
     */
    private void doStateNone() {
        // If we're at the beginning state, then start the discovery process

        if (bondedOnly) {
            addPairedDevices();
            setServiceState(STATE_DISCOVERY_FINISHED);
        } else {
            try {
                doDiscovery();
            } catch (Exception e) {
                log.error("some sort of exception: " + e);
            }
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
        
        updateHashes(sendBlacklist);
        updateData();
        for (BluetoothDevice currentDevice : difference) {
            log.debug("Trying to connect to " + currentDevice.getName() + " with address " + currentDevice.getAddress());

            connect(currentDevice);
        }

        setServiceState(STATE_WAIT_FOR_CONNECTIONS);

    }

    private void waitService() {
        try {
            log.debug("Service thread sleeping for " + getScanFrequency() + " seconds...");
            this.sleep(getScanFrequency() * 1000);
        } catch (InterruptedException e) {
            log.error("Thread sleeping interrupted: " + e);
        }

        setServiceState(STATE_NONE);
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
     * Cancel the thread
     */
    public void cancel() {

    }
}
