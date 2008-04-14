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
import java.util.Random;

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

/**
 * FluidNexusServer
 *
 * This is our Service that receives incoming bluetooth connections.
 * 
 * For the purposes of the ADC competition and current state of the emulator, we have had to simulate Bluetooth in two ways.  One, we fire simulated Bluetooth NEW_MESSAGE intents, as if we had actually received messages over Bluetooth.  Two, we connect to a bridge that sits outside of the emulator, passing data from an actual Bluetooth device to this Service within the emulator.  The bridge is written in Python using the lightblue library.  To use the bridge, type "adb forward tcp:7010 tcp:7010" before starting the bridge.
 *
 */
public class FluidNexusServer extends Service {
    private FluidNexusDbAdapter dbHelper;
    private FluidNexusBtSimulator btSim;

    private Socket clientSocket;
    private ServerSocket serverSocket;
    PrintWriter out;
    BufferedReader in;

    private NotificationManager nm;
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexusServer"); 

    int id;
    boolean simulateBluetooth;
    boolean serverAlreadyStarted = false;

    /**
     * Dummy binder
     *
     */
    public class FluidNexusServerBinder extends Binder {
        FluidNexusServer getService() {
            return FluidNexusServer.this;
        }
    }

    @Override
    protected void onCreate() {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        log.info("starting fluid nexus server");

        // Our database cursor
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();
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

        if (simulateBluetooth) {
            if (!serverAlreadyStarted) {
                serverAlreadyStarted = true;
                FluidNexusSimulateServerTask task = new FluidNexusSimulateServerTask((Context) this, btSim, dbHelper);
                Thread thr = new Thread(task);
                thr.start();
            }
        } else {
            if (!serverAlreadyStarted) {
                serverAlreadyStarted = true;
                FluidNexusSocketServerTask task = new FluidNexusSocketServerTask((Context) this, btSim, dbHelper);
                Thread thr = new Thread(task);
                thr.start();
            }
        }
    }

    @Override
    protected void onDestroy() {

    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IBinder binder = new FluidNexusServerBinder();


    /**
     * The task that connects to our bridge outside of the emulator.
     *
     */
    class FluidNexusSocketServerTask extends Thread implements Runnable {
        private FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
        FluidNexusBtSimulator btSim;
        FluidNexusDbAdapter dbHelper;
        private NotificationManager nm;
        Context ctx;

        public FluidNexusSocketServerTask(Context ctx, FluidNexusBtSimulator btSim, FluidNexusDbAdapter dbHelper) {
            this.btSim = btSim;
            this.dbHelper = dbHelper;
            this.ctx = ctx;
            nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }


        public void run() {
            log.info("starting socket server");
            String address = "10.0.2.2";
            String port = "7010";

            try {
                serverSocket = new ServerSocket(Integer.parseInt(port));
            } catch (IOException e) {
                log.error("Some type of I/O exception: " + e);
                System.exit(1);
            }

            // Continually run our server socket task
            while (true) {
                serverSocketRun();
            }
        }

        public void serverSocketRun() {
            clientSocket = null;
            out = null;
            in = null;
    
    
            try {
                clientSocket = serverSocket.accept();
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (UnknownHostException e) {
                return;
            } catch (SocketTimeoutException e) {
                log.error("Connection timeout: " + e);
            } catch (IOException e) {
                log.error("Some type of I/O exception: " + e);
                System.exit(1);
            }
    
            try {
                    // The following is the sequence of reads we do for the
                    // current state of the FluidNexus protocol
                    // Depending on what we get for the version, we might
                    // decide to branch here to different read sequences.
                    char[] version = new char[2];
                    in.read(version, 0, 2);
                    log.info(new String(version));

                    char[] titleLength = new char[3];
                    in.read(titleLength, 0, 3);
                    log.info(new String(titleLength));

                    char[] messageLength = new char[6];
                    in.read(messageLength, 0, 6);
                    log.info(new String(messageLength));

                    char[] timestamp = new char[13];
                    in.read(timestamp, 0, 13);
                    log.info(new String(timestamp));

                    char[] source = new char[32];
                    in.read(source, 0, 32);
                    log.info(new String(source));

                    int length = Integer.parseInt(new String(titleLength));
                    char[] title = new char[length];
                    in.read(title, 0, length);
                    log.info(new String(title));

                    length = Integer.parseInt(new String(messageLength));
                    char[] message= new char[length];
                    in.read(message, 0, length);
                    log.info(new String(message));
                    
                    // TODO
                    // replace cell tower info later
                    // Dummy cell tower info for now
                    dbHelper.add_received(0, new String(title), new String(message), "(123,123,123,123)");
                    showNotification(new String(title)); 
                    sendNewMessageIntent();

            } catch (IOException e) {
                log.error("Some type of I/O exception: " + e);
            }
        }

        /**
         * Send intent to update our main activity.
         *
         */
        public void sendNewMessageIntent() {
            Intent newMessage = new Intent(getText(R.string.intent_new_message).toString());
    
            broadcastIntent(newMessage);
        }

        /**
         * Show notification that we've got a new message.
         *
         */
        private void showNotification(String messageTitle) {
            Intent contentIntent = new Intent(ctx, FluidNexusAndroid.class);
            Intent appIntent = new Intent(ctx, FluidNexusAndroid.class);
   
            nm.cancel(R.string.notification); 
            nm.notify(R.string.notification,
                    new Notification(ctx,
                        R.drawable.menu_all_status,
                        getText(R.string.notification_title_new_message).toString(),
                        System.currentTimeMillis(),
                        getText(R.string.notification_title_new_message).toString(),
                        messageTitle,
                        contentIntent,
                        R.drawable.fluid_nexus_icon,
                        "Fluid Nexus",
                        appIntent));
        }


    }

    /**
     * The task that fires simulated received messages over bluetooth.
     *
     */
    class FluidNexusSimulateServerTask extends Thread implements Runnable {
        private FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
        FluidNexusBtSimulator btSim;
        FluidNexusDbAdapter dbHelper;
        private NotificationManager nm;
        Context ctx;

        public FluidNexusSimulateServerTask(Context ctx, FluidNexusBtSimulator btSim, FluidNexusDbAdapter dbHelper) {
            this.btSim = btSim;
            this.dbHelper = dbHelper;
            this.ctx = ctx;
            nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
    
        public void run() {
            log.info("starting server thread");
            try {
                Random rand = new Random();
  
                // The following is a sequence of dummy events to illustrate
                // how we would receive new messages over bluetooth.
                // TODO
                // write better messages for testing purposes :-)
                Thread.sleep(20000);
                String title = "Server add test";
                dbHelper.add_received(0, title, "This is a new messaged added by the server.", "(123,123,123,123)");
                showNotification(title); 
                sendNewMessageIntent();

                long sleepTime = 0;
                sleepTime += rand.nextFloat() * (2 * 60000);
                Thread.sleep(sleepTime);
                title = "A second message";
                dbHelper.add_received(0, title, "Just adding something new here as a test.  This would be more interesting if it were actually being used..", "(123,123,123,123)");
                showNotification(title); 
                sendNewMessageIntent();

                sleepTime += rand.nextFloat() * (3 * 60000);
                Thread.sleep(sleepTime);
                title = "Third message";
                dbHelper.add_received(0, title, "Are we going to say anything interesting?  Or just put stupid info here?", "(123,123,123,123)");
                showNotification(title); 
                sendNewMessageIntent();
   
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        /**
         * Fire intent to let main activity know we've got a new message
         *
         */
        public void sendNewMessageIntent() {
            Intent newMessage = new Intent(getText(R.string.intent_new_message).toString());
    
            broadcastIntent(newMessage);
        }

        /**
         * Show notification that we've got a new message.
         *
         */
        private void showNotification(String messageTitle) {
            Intent contentIntent = new Intent(ctx, FluidNexusAndroid.class);
            Intent appIntent = new Intent(ctx, FluidNexusAndroid.class);
   
            nm.cancel(R.string.notification); 
            nm.notify(R.string.notification,
                    new Notification(ctx,
                        R.drawable.menu_all_status,
                        getText(R.string.notification_title_new_message).toString(),
                        System.currentTimeMillis(),
                        getText(R.string.notification_title_new_message).toString(),
                        messageTitle,
                        contentIntent,
                        R.drawable.fluid_nexus_icon,
                        "Fluid Nexus",
                        appIntent));
        }

    }


}



