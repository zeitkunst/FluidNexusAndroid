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


package org.zeitkunst.FluidNexus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Vector;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentReceiver;
import android.database.Cursor;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.Toast;


public class FluidNexusClient extends Service {
    private FluidNexusDbAdapter dbHelper;
    private FluidNexusBtSimulator btSim;

    private Socket clientSocket;
    private ServerSocket serverSocket;
    PrintWriter out;
    BufferedReader in;

    private Handler serviceHandler = new Handler();

    private IntentReceiver iReceiver;
    private IntentFilter iFilter;

    Vector<String> addresses = new Vector<String>();
    ArrayList<Vector> currentServiceList;
    String currentAddress;

    private boolean discoveryCompleted = false;
    private boolean simulateBluetooth;
    int id;
    boolean serverAlreadyStarted = false;

    private NotificationManager nm;
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexusClient"); 

    /**
     * Dummy binder.
     *
     */
    public class FluidNexusClientBinder extends Binder {
        FluidNexusClient getService() {
            return FluidNexusClient.this;
        }
    }

    /**
     * This is the intent receiver for our simulated bluetooth events.
     *
     */
    private class DeviceIntentReceiver extends IntentReceiver {
        public void onReceiveIntent(Context context, Intent intent) {
            String action = intent.getAction();
            log.info(action);
            if (action.equals(getText(R.string.intent_discovery_started).toString())) {
                log.info("discovery started");
            } else if (action.equals(getText(R.string.intent_device_found).toString())) {
                Bundle extras = intent.getExtras();
                String address = extras.getString("ADDRESS");
                String classID = extras.getString("CLASS");
                addresses.addElement(address);

                log.info("device found");
                log.info(address);
            } else if (action.equals(getText(R.string.intent_discovery_completed).toString())) {
                log.info("discovery complete");
                showDiscoveryNotification("Found devices.");
                discoveryCompleted = true;

                checkServices();
            }
        }
    }

    /**
     * This is our intent receiver for the service discovery events.
     *
     */
    private class ServiceIntentReceiver extends IntentReceiver {
        public void onReceiveIntent(Context context, Intent intent) {
            String action = intent.getAction();
            log.info(action);
            if (action.equals(getText(R.string.intent_service_discovery_completed).toString())) {
                log.info("service discovery completed");
            }
        }
    }

    public class SendMessagesIntentReceiver extends IntentReceiver {
        public void onReceiveIntent(Context context, Intent intent) {
            String action = intent.getAction();
            log.info(action);
            if (action.equals(getText(R.string.intent_send_messages_completed).toString())) {
                log.info("send messages completed...running again in 5 min");
                Runnable runnable = new Runnable() {
                    public void run() {
                        btSim.startDiscovery();
                    }
                };
                serviceHandler.postDelayed(runnable, 5 * 60 * 1000);
            }
        }
    }


    @Override
    protected void onCreate() {
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();

        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        log.info("starting fluid nexus client");
        //addNewMessageTest();
        //sendIntentTest();

        // Regiser my receiver to device discovery actions
        iFilter = new IntentFilter(getText(R.string.intent_discovery_started).toString());
        iFilter.addAction((getText(R.string.intent_device_found).toString()));
        iFilter.addAction((getText(R.string.intent_discovery_completed).toString()));
        iReceiver = new DeviceIntentReceiver();
        registerReceiver(iReceiver, iFilter);


        // Regiser my receiver for service discovery actions
        iFilter = new IntentFilter(getText(R.string.intent_service_discovery_completed).toString());
        iReceiver = new ServiceIntentReceiver();
        registerReceiver(iReceiver, iFilter);

        // Regiser my receiver for service discovery actions
        iFilter = new IntentFilter(getText(R.string.intent_send_messages_completed).toString());
        iReceiver = new SendMessagesIntentReceiver();
        registerReceiver(iReceiver, iFilter);


        //connectSocket();
    }

    /**
     * Start our tasks if they haven't been started already
     *
     */
    @Override
    protected void onStart(int id, Bundle args) {
        this.id = id;

        // Check what type of simulation we're doing
        this.simulateBluetooth = args.getBoolean("SimulateBluetooth");        
        startClientConnectionProcess();
    }

    public void startClientConnectionProcess() {
        if (this.simulateBluetooth) {
            if (!serverAlreadyStarted) {
                serverAlreadyStarted = true;

                // HACK
                // We should never have to pass the context this way, but in order
                // to create a simulated bluetooth "service", we have to pass
                // a context to allow our simulator to broadcast intents
                btSim = new FluidNexusBtSimulator(true, (Context) this, getResources());
                btSim.startDiscovery();
            }
        } else {
            if (!serverAlreadyStarted) {
                serverAlreadyStarted = true;
                // HACK
                // We should never have to pass the context this way, but in order
                // to create a simulated bluetooth "service", we have to pass
                // a context to allow our simulator to broadcast intents
                btSim = new FluidNexusBtSimulator(false, (Context) this, getResources());
                btSim.startDiscovery();
            }
        }

    }

    @Override
    protected void onDestroy() {

    }


    class FluidNexusClientTask extends Thread implements Runnable {
        public FluidNexusClientTask() {

        }

        public void run() {
            while (true) {
                startClientConnectionProcess();

                try {
                    Thread.sleep(3 * 60000);
                } catch (InterruptedException e) {
                    log.info("Interrupted exception");
                }
            }
        }
    }
    /**
     * This is the thread that starts the process of checking for services.
     *
     */
    private void checkServices() {
        FluidNexusServiceCheckTask task = new FluidNexusServiceCheckTask(addresses, btSim, dbHelper);
        Thread thr = new Thread(task);
        thr.start();
    }

    public void sendIntentTest() {
        Intent newMessage = new Intent(getText(R.string.intent_new_message).toString());

        broadcastIntent(newMessage);
    }

    public void connectSocket() {
        clientSocket = null;
        out = null;
        in = null;

        String address = "10.0.2.2";
        String port = "7000";

        try {
            //clientSocket = new Socket(address, Integer.parseInt(port));
            serverSocket = new ServerSocket(Integer.parseInt(port));
            clientSocket = serverSocket.accept();
            log.info("Socket created");
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (UnknownHostException e) {
            log.error("I don't know about host" + address + ":" + port + ": " + e);
            return;
        } catch (SocketTimeoutException e) {
            log.error("Connection timeout: " + e);
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
        }

        //out.println("This is from inside android");
        try {
                String message = in.readLine();
                log.info(message);
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IBinder binder = new FluidNexusClientBinder();

    private void showDiscoveryNotification(String messageTitle) {
        Intent contentIntent = new Intent(this, FluidNexusAndroid.class);
        Intent appIntent = new Intent(this, FluidNexusAndroid.class);

        nm.notify(R.string.notification_bluetooth,
                new Notification(this,
                    R.drawable.menu_view_status,
                    getText(R.string.notification_fluid_nexus_discovery_complete),
                    System.currentTimeMillis(),
                    getText(R.string.notification_fluid_nexus_discovery_complete),
                    messageTitle,
                    contentIntent,
                    R.drawable.fluid_nexus_icon,
                    "Fluid Nexus",
                    appIntent));
    }

    private void showServicesNotification(String messageTitle) {
        Intent contentIntent = new Intent(this, FluidNexusAndroid.class);
        Intent appIntent = new Intent(this, FluidNexusAndroid.class);
        
        nm.cancel(R.string.notification_bluetooth);
        nm.notify(R.string.notification_bluetooth,
                new Notification(this,
                    R.drawable.menu_view_status,
                    getText(R.string.notification_fluid_nexus_services_complete),
                    System.currentTimeMillis(),
                    getText(R.string.notification_fluid_nexus_services_complete),
                    messageTitle,
                    contentIntent,
                    R.drawable.fluid_nexus_icon,
                    "Fluid Nexus",
                    appIntent));
    }

    private void showServicesSendOutgoingNotification(String messageTitle) {
        Intent contentIntent = new Intent(this, FluidNexusAndroid.class);
        Intent appIntent = new Intent(this, FluidNexusAndroid.class);

        nm.cancel(R.string.notification_bluetooth);
        nm.notify(R.string.notification_bluetooth,
                new Notification(this,
                    R.drawable.menu_view_status,
                    getText(R.string.notification_fluid_nexus_send_outgoing_complete),
                    System.currentTimeMillis(),
                    getText(R.string.notification_fluid_nexus_send_outgoing_complete),
                    messageTitle,
                    contentIntent,
                    R.drawable.fluid_nexus_icon,
                    "Fluid Nexus",
                    appIntent));
    }


    class FluidNexusServiceCheckTask extends Thread implements Runnable {
        private FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
        Vector<String> addresses;
        String currentAddress;
        ArrayList<Vector> currentServiceList;
        FluidNexusBtSimulator btSim;
        FluidNexusDbAdapter dbHelper;
    
        public FluidNexusServiceCheckTask(Vector<String> addresses, FluidNexusBtSimulator btSim, FluidNexusDbAdapter dbHelper) {
            this.addresses = addresses;
            this.btSim = btSim;
            this.dbHelper = dbHelper;
    
        }
    
        public void run() {
            // TODO
            // Split this off into threads...
            // BUT, perhaps we can't do this with real bluetooth devices
            log.info("checking services");
            for (int currentPhoneIndex = 0; currentPhoneIndex < this.addresses.size(); ++currentPhoneIndex) {
                this.currentAddress = addresses.get(currentPhoneIndex);
                log.info(this.currentAddress);
                this.currentServiceList = this.btSim.getServices(this.currentAddress);
    
                boolean found = false;
                for (int serviceIndex = 0; serviceIndex < this.currentServiceList.size(); ++serviceIndex) {
                    Vector service = this.currentServiceList.get(serviceIndex);
                    String currentService = (String) service.get(1);
                    log.info(currentService);
                    if (currentService.equals("FluidNexus")) {
                        showServicesNotification("Found Fluid Nexus.");
                        found = true;
                        break;
                    } else {
                        continue;
                    }
    
                }
    
                if (found) {
                    Vector<String> serverMessageHashes = getServerMessageHashes(currentServiceList);
                    Vector<String> ourMessageHashes = getOurMessageHashes();
    
                    for (int serverHashIndex = 0; serverHashIndex < serverMessageHashes.size(); ++serverHashIndex) {
                        if (ourMessageHashes.contains(serverMessageHashes.get(serverHashIndex))) {
                            ourMessageHashes.remove(serverMessageHashes.get(serverHashIndex));
                        }
                    }
    
                    for (int hashIndex = 0; hashIndex < ourMessageHashes.size(); ++hashIndex) {
                        log.info("sending a hash");
                        String hash = ourMessageHashes.get(hashIndex);
                        Cursor result = dbHelper.returnItemBasedOnHash(hash);
                        result.first();
    
                        int index = result.getColumnIndex(FluidNexusDbAdapter.KEY_TITLE);
                        String title = result.getString(index);
    
                        index = result.getColumnIndex(FluidNexusDbAdapter.KEY_DATA);
                        String data = result.getString(index);
    
                        index = result.getColumnIndex(FluidNexusDbAdapter.KEY_TIME);
                        String time = result.getString(index);
    
                        btSim.sendData(title, data, time, hash);
                        showServicesSendOutgoingNotification("Sent '" + title + "'");
                    }
                    Intent sendMessagesCompleted = new Intent(getText(R.string.intent_send_messages_completed).toString());
                    broadcastIntent(sendMessagesCompleted);

                }            
    
            }
    
        }
    
        private Vector<String> getServerMessageHashes(ArrayList<Vector> services) {
            Vector<String> serverMessageHashes = new Vector<String>();
    
            for (int serviceIndex = 0; serviceIndex < services.size(); ++serviceIndex) {
                Vector service = services.get(serviceIndex);
                String serviceName = (String) service.get(1);
    
                if (serviceName.charAt(0) == ':') {
                    log.info(serviceName);
                    serverMessageHashes.add(serviceName);
                }
            }
    
            return serverMessageHashes;
        }
    
    
        private Vector<String> getOurMessageHashes() {
            Vector<String> ourMessageHashes = new Vector<String>();
    
            Cursor outgoing = dbHelper.outgoing();
            
            int index = outgoing.getColumnIndex(FluidNexusDbAdapter.KEY_HASH);
            outgoing.first();
            for (int i = 0; i < outgoing.count(); ++i) {
                log.info(outgoing.getString(index));
                ourMessageHashes.add(outgoing.getString(index));
                
                if (i == outgoing.count()) {
                    break;
                } else {
                    outgoing.next();
                }
            }
            return ourMessageHashes;
        }
    }
}


