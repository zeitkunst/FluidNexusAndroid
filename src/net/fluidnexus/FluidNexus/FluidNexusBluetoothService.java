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

    private int state;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_DISCOVERY = 1; // we're discovering things
    public static final int STATE_DISCOVERY_FINISHED = 2; // we're done discovering things and can now move on
    public static final int STATE_SERVICES = 3; // we're discovering services
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
     * Get a list of services, in UUID form, for a given device
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
     * Get a list of services, in UUID form, for a given device
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

                // If the discovery is finished, then start the services discovery
                if (state == STATE_DISCOVERY_FINISHED) {
                    setState(STATE_SERVICES);
                    doServiceDiscovery();
                }

                // If we're in services discovery, just continue
                if (state == STATE_SERVICES) {
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
}
