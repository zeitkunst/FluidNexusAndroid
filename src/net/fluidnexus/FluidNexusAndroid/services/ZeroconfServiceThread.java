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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.content.Context;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import net.fluidnexus.FluidNexusAndroid.provider.MessagesProvider;
import net.fluidnexus.FluidNexusAndroid.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexusAndroid.Logger;

/**
 * This thread runs all of the device/service discovery and starts threads for socket communication
 */
public class ZeroconfServiceThread extends ServiceThread {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // Threads
    private ZeroconfServerThread serverThread = null;

    // State of the system
    private int state;

    // whether to send blacklisted messages
    private boolean sendBlacklist = false;

    // Potential states
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_RESOLVING = 1; // we're resolving things
    public static final int STATE_DISCOVERY_FINISHED = 2; // we're done discovering things and can now move on
    public static final int STATE_SERVICES = 3; // we're discovering services
    public static final int STATE_CONNECTING = 4; // we're connecting other devices
    public static final int STATE_CONNECTED = 5; // we're connected and sending data
    public static final int STATE_WAIT_FOR_CONNECTIONS = 6; // we wait for all of the connection threads to finish
    public static final int STATE_SERVICE_WAIT = 7; // we sleep for a bit before beginning the discovery process anew
    public static final int STATE_QUIT = 100; // we're done with everything

    // Connected devices
    private HashSet<String> connectedDevices = new HashSet<String>();

    // jmdns infos
    private JmDNS jmdns = null;
    private ServiceInfo serviceInfo = null;
    private ServiceListener serviceListener = null;
    private MulticastLock lock = null;
    private WifiManager wifiManager = null;

    // Our zeroconf type
    private static final String zeroconfServiceName = "Fluid Nexus";
    private static final String zeroconfType = "_fluidnexus._tcp.local.";
    private static final int zeroconfPort = 17894;

    /**
     * Handler that receives information from the threads
     */
    public final Handler threadHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT_THREAD_FINISHED:
                    String doneAddress = msg.getData().getString("host");
                    log.debug("Done with host: " + doneAddress);
                    connectedDevices.remove(doneAddress);
                    break;
                case UPDATE_HASHES:
                    updateHashes(sendBlacklist);
                case SCAN_FREQUENCY_CHANGED:
                    log.debug("Changing scan frequency to: " + msg.arg1);
                    setScanFrequency(msg.arg1);
                    break;
                default:
                    break;

            }
        }
    };

    /**
     * Constructor for the thread that does zeroconf work
     */
    public ZeroconfServiceThread(Context ctx, ArrayList<Messenger> givenClients, boolean givenSendBlacklist) {
        
        super(ctx, givenClients);
        
        sendBlacklist = givenSendBlacklist;

        // TODO
        // deal with what happens if wifi isn't enabled
        setName("ZeroconfServiceThread");
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        lock = wifiManager.createMulticastLock("ZeroconfServiceLock");
        lock.setReferenceCounted(true);

        updateHashes(sendBlacklist);
        updateData();


        // TODO
        // Disabling server thread for now until we can figure out the lack of resolution issue
        /*
        if (serverThread == null) {

            serverThread = new ZeroconfServerThread(ctx, threadHandler, clients);
            serverThread.setHashes(currentHashes);
            serverThread.setData(currentData);
            serverThread.start();
        }
        */

        setServiceState(STATE_NONE);
    }


    /**
     * Set the listener for mDNS results
     */
    private void setListener() {
        serviceListener = new ServiceListener() {
                @Override
                public void serviceResolved(ServiceEvent ev) {
                    String additions = "";
                    if (ev.getInfo().getInetAddresses() != null && ev.getInfo().getInetAddresses().length > 0) {
                        log.debug("Number of addresses: " + ev.getInfo().getInetAddresses().length);
                        additions = ev.getInfo().getInetAddresses()[0].getHostAddress();
                        log.debug("Service resolved: " + ev.getInfo().getQualifiedName() + " port: " + ev.getInfo().getPort() + "; additions: " + additions);
                        String host = additions;
                        int port = ev.getInfo().getPort();
                        // Connect to the device
                        if (!(ev.getInfo().getQualifiedName().equals(zeroconfServiceName + "." + zeroconfType))) {
                            connect(host, port);
                        }
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent ev) {
                    log.debug("Service removed: " + ev.getName());
                }

                @Override
                public void serviceAdded(ServiceEvent event) {
                    // required to force serviceResolved to be called again (after the first search)
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
                }
            };

    }

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
    @Override
    public synchronized int getServiceState() {
        return state;
    }

    /**
     * Start the listeners for zeroconf services
     */
    public void doStateNone() {
        lock.acquire();
        try {
            jmdns = JmDNS.create();

            if (serverThread != null) {
                serviceInfo = ServiceInfo.create(zeroconfType, zeroconfServiceName, zeroconfPort, "Fluid Nexus Zeroconf server for android");
                jmdns.registerService(serviceInfo);
            }
            
            setListener();
            jmdns.addServiceListener(zeroconfType, serviceListener);
            setServiceState(STATE_WAIT_FOR_CONNECTIONS);
        } catch (IOException e) {
            log.error("Some sort of problem trying to start our service listeners.");
            e.printStackTrace();
        }
    }

    /**
     * Start a thread for connection to remove FluidNexus device
     * @param host hostname to connect to
     * @param port port to use for connection
     */
    public synchronized void connect(String host, int port) {
        log.debug("Connecting to " + host + ":" + port);

        ZeroconfClientThread connectThread = new ZeroconfClientThread(context, threadHandler, clients, host, port, sendBlacklist);
        connectThread.setHashes(currentHashes);
        connectThread.setData(currentData);
        connectedDevices.add(host);            
        connectThread.start();
    }

    private void waitService() {
        jmdns.removeServiceListener(zeroconfType, serviceListener);
        serviceListener = null;
        lock.release();
        try {
            log.debug("Service thread sleeping for " + getScanFrequency() + " seconds...");
            this.sleep(getScanFrequency() * 1000);
        } catch (InterruptedException e) {
            log.error("Thread sleeping interrupted: " + e);
        }

        setServiceState(STATE_NONE);
    }

    /**
     * Unregister our service on message to quit
     */
    public void unregisterService() {
        if (jmdns != null) {
            if (getServiceState() != STATE_SERVICE_WAIT) {
                lock.acquire();
            }

            if (serviceListener != null) {
                jmdns.removeServiceListener(zeroconfType, serviceListener);
                serviceListener = null;
            }

            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                log.error("Unable to close jmdns.");
            }
    
            jmdns = null;
            lock.release();
        }
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
}
