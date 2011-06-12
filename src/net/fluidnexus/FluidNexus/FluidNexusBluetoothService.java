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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Vector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

public class FluidNexusBluetoothService {
    private static final String TAG = "FluidNexusBluetoothService";
    private static final String SERVICE_NAME = "FluidNexus";
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private final BluetoothAdapter bluetoothAdapter;

    private final Handler handler;

    private ArrayList<Vector> devices = new ArrayList<Vector>();
    private Vector<String> device = new Vector<String>();
    private IntentFilter btFoundFilter;
    private IntentFilter sdpFilter;

    private Context ctx;

    private BluetoothServiceThread serviceThread = null;

    // Threads
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    // UUID
    private static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");
    
    private int state;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_DISCOVERY = 1; // we're discovering things
    public static final int STATE_DISCOVERY_FINISHED = 2; // we're done discovering things and can now move on
    public static final int STATE_SERVICES = 3; // we're discovering services
    public static final int STATE_CONNECTING = 4; // we're connecting other devices
    public static final int STATE_CONNECTED = 5; // we're connected and sending data
    public static final int STATE_QUIT = 100; // we're done with everything

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
                setState(STATE_DISCOVERY_FINISHED);
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
            for (Parcelable uuid: uuidExtra) {
                log.debug("Found: " + uuid.toString());
            }
        }
    };

    /**
     * Constructor for the bluetooth service
     * @param context The originating activity context
     * @param handler A handler for messages sent back to the original activity
     */
    public FluidNexusBluetoothService(Context context, Handler givenHandler) {
        ctx = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        handler = givenHandler;

        btFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        btFoundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        ctx.registerReceiver(btDiscoveryReceiver, btFoundFilter);

        String action = "android.bleutooth.device.action.UUID";
        IntentFilter sdpFilter = new IntentFilter(action);
        ctx.registerReceiver(sdpReceiver, sdpFilter);

    }

    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current bluetooth service state
     */
    private synchronized void setState(int newState) {
        log.debug("Changing state from " + state + " to " + newState);
        state = newState;
        handler.obtainMessage(FluidNexusAndroid.MESSAGE_BT_STATE_CHANGED, state, -1).sendToTarget();
    }

    /**
     * Get the current state value
     */
    private synchronized int getState() {
        return state;
    }

    /**
     * Start the bluetooth service that enters into the main loop
     */
    public synchronized void start() {
        // for the moment, only go through a single pass


        if (serviceThread == null) {
            serviceThread = new BluetoothServiceThread();
            log.debug("Starting our bluetooth service thread...");
            serviceThread.start();
        }
    }

    /**
     * Stop everything
     */
    public synchronized void stop() {
        log.debug("Stopping FluidNexus bluetooth service threads...");

        if (serviceThread != null) {
            serviceThread.cancel();
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

        // cancel any thread sending data
        // TODO do we want to do this?
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // State the thread that connects to the remove device
        connectThread = new ConnectThread(device, true);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start a thread for sending data to remote FluidNexus device
     * @param socket BluetoothSocket on which the connection was made
     * @param device BluetoothDevice that has been connected
     * @param socketType type of socket being used
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) {
        log.debug("Connected with socketype: " + socketType);

        // Cancel thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(socket, socketType);
        connectedThread.start();

        setState(STATE_CONNECTED);
    }

    /**
     * Write to the connected thread in an unsynchronized manner
     * @param out Bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary thread object
        ConnectedThread r;

        // Synchronize a copy of this thread
        synchronized (this) {
            if (state != STATE_CONNECTED) return;

            r = connectedThread;
        }

        // Perform write
        r.write(out);
    }

    /**
     * Get a list of services, in UUID form, for a given device; taken from http://wiresareobsolete.com/wordpress/2010/11/android-bluetooth-rfcomm/
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
        private BluetoothSocket socket;
        /*
        private final InputStream inputStream;
        private final OutputStream outputStream;
        */

        /**
         * Constructor for the thread
         */
        public BluetoothServiceThread() {
        }

        /**
         * Begin the thread, and thus the service main loop
         */
        public void run() {
            while (state != STATE_QUIT) {
                // Go through our state machine
                if (state == STATE_NONE) {
                    // If we're at the beginning state, then start the discovery process
                    try {
                        doDiscovery();
                    } catch (Exception e) {
                        log.debug("some sort of exception: " + e);
                    }

                }

                // If we're discoverying things, just continue on our way
                if (state == STATE_DISCOVERY) {
                    continue;
                }

                // If the discovery is finished, start trying to connect to remote devices
                if (state == STATE_DISCOVERY_FINISHED) {
                    for (Vector currentDevice : devices) {
                        String name = (String) currentDevice.get(0);
                        String address = (String) currentDevice.get(1);
                        log.debug("Working on device " + name + " with address " + address);
                        BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(address);

                        connect(btDevice, true);
                    }

                    // After this is all done, start the cycle again
                    setState(STATE_NONE);
                }

                // If we're in services discovery, just continue
                if (state == STATE_SERVICES) {
                    continue;

                }

                if (state == STATE_CONNECTING) {
                    continue;
                }

                /*
                try{
                    this.sleep(35000);
                } catch (InterruptedException e) {
                    log.error("Thread interrupted.");
                }
                */
            }
        }

        /**
         * Run the actions to do with device discovery
         */
        private void doDiscovery() {
            setState(STATE_DISCOVERY);
            bluetoothAdapter.startDiscovery();
        }

        /**
         * Run the actions to do with service discovery
         */
        private void doServiceDiscovery() {
            UUID tempUUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

            //for (int i = 0; i < devices.size(); i++) {
            for (Vector currentDevice : devices) {
                String name = (String) currentDevice.get(0);
                String address = (String) currentDevice.get(1);
                log.debug("Working on device " + name + " with address " + address);
                BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(address);
                servicesFromDeviceAsync(btDevice);

                /*
                if (services == null) {
                    log.debug("services seems to be null...");
                } else {
                    for (ParcelUuid s: services) {
                        log.debug("Found service " + s.toString());
                    }
                }
                */

                /*
                BluetoothSocket tmp = null;
                try {
                    tmp = btDevice.createRfcommSocketToServiceRecord(tempUUID);
                } catch (IOException e) {
                    log.debug("Socket creation failed: " + e);
                }

                socket = tmp;
                try {
                    socket.connect();
                } catch (IOException e) {
                    log.debug("Socket connection failed: " + e);
                }
                */


            }
            /*
            */
            /*
            try {
                Class cl = Class.forName("android.bluetooth.BluetoothDevice");
                Class[] par = {};
                Method method = cl.getMethod("fetchUuidsWithSdp", par);
                Object[] args = {};
                method.invoke(device, args);
            } catch (final ClassNotFoundException e) {
                log.debug("Error: " + e);
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                log.debug("Error: " + e);
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.debug("Error: " + e);
                e.printStackTrace();
            } catch (java.lang.reflect.InvocationTargetException e) {
                log.debug("Error: " + e);
                e.printStackTrace();

            }
            */


        }
        /** 
         * Cancel the thread
         */
        public void cancel() {

        }
    }

    /**
     * This thread tries to connect to a remote device and send data
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private String socketType;

        public ConnectThread(BluetoothDevice remoteDevice, boolean secure) {
            device = remoteDevice;
            BluetoothSocket tmp = null;
            socketType = secure ? "Secure" : "Insecure";

            // get a socket for connection to the device
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(FluidNexusUUID);
                } else {
                    //tmp = device.createInsecureRfcommSocketToServiceRecord(FluidNexusUUID);

                }
            } catch (IOException e) {
                log.error("Socket Type: " + socketType + "create() failed: " + e);
            }

            socket = tmp;
        }

        public void run() {
            log.info("Begin connect thread");
            // TODO: cancel discovery
            
            try {
                socket.connect();
            } catch (IOException e) {
                // Try to close the socket
                try {
                    socket.close();
                } catch (IOException e2) {
                    log.error("unable to close() socket during connection failure: " + e2);
                }

                // TODO: enable connectionFailed
                return;
            }

            synchronized (FluidNexusBluetoothService.this) {
                connectThread = null;
            }

            connected(socket, device, socketType);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("close() of connect socket failed: " + e);
            }
        }
    }

    /**
     * Thread that actually sends data to a connected device
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket remoteSocket, String socketType) {
            log.debug("Created ConnectedThread: " + socketType);
            socket = remoteSocket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                log.error("Temp stream sockets no created");
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            log.info("Begin ConnectedThread");

            // TODO
            // Enable reading from the connected device
            String message = "Hello from the phone!";
            byte[] send = message.getBytes();
            write(send);
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

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("close() of ConnectedThread socket failed: " + e);
            }
        }
    }
}
