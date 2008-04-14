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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

class FluidNexusBtSimulatorDiscoveryTask extends Thread implements Runnable {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    Resources r;
    Context ctx;

    public FluidNexusBtSimulatorDiscoveryTask(Context context, Resources resources) {
        r = resources;
        ctx = context;
    }

    public void run() {
        log.info("Starting remote device discovery");
        Intent discovery = new Intent(r.getText(R.string.intent_discovery_started).toString());
        ctx.broadcastIntent(discovery);
 
        try{
            this.sleep(20000);
        } catch (InterruptedException e) {
            log.error("Thread interrupted.");
        }

        log.info("Found a remote device");
        discovery = new Intent(r.getText(R.string.intent_device_found).toString());
        discovery.putExtra("ADDRESS", "00:01:FF:79:12:08");
        discovery.putExtra("CLASS", 2);
        discovery.putExtra("RSSI", 1);
        ctx.broadcastIntent(discovery);

        try{
            this.sleep(12000);
        } catch (InterruptedException e) {
            log.error("Thread interrupted.");
        }

        log.info("Found a remote device");
        discovery = new Intent(r.getText(R.string.intent_device_found).toString());
        discovery.putExtra("ADDRESS", "23:45:A2:F8:90:1B");
        discovery.putExtra("CLASS", 2);
        discovery.putExtra("RSSI", 1);
        ctx.broadcastIntent(discovery);

        log.info("Finishing discovery process");
        discovery = new Intent(r.getText(R.string.intent_discovery_completed).toString());
        ctx.broadcastIntent(discovery);

    }
}

class FluidNexusBtSocketDiscoveryTask extends Thread implements Runnable {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    Resources r;
    Context ctx;
    private Socket clientSocket;
    PrintWriter out;
    BufferedReader in;

    public FluidNexusBtSocketDiscoveryTask(Context context, Resources resources) {
        r = resources;
        ctx = context;
    }

    public void run() {
        log.info("Starting remote device discovery");
        Intent discovery = new Intent(r.getText(R.string.intent_discovery_started).toString());
        ctx.broadcastIntent(discovery);

        clientSocket = null;
        out = null;
        in = null;

        String address = "10.0.2.2";
        String port = "7030";

        try {
            clientSocket = new Socket(address, Integer.parseInt(port));
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
            System.exit(1);
        }

        //out.println("This is from inside android");
        try {
                out.write("00");
                out.flush();

                char[] numDevices = new char[2];
                in.read(numDevices, 0, 2);
                String numString = new String(numDevices);
                log.info(new String(numDevices));

                for (int index = 0; index < Integer.parseInt(numString); ++index) {
                    char[] deviceName = new char[17];
                    in.read(deviceName, 0, 17);
                    log.info(new String(deviceName));

                    char[] deviceClass = new char[1];
                    in.read(deviceClass, 0, 1);
                    log.info(new String(deviceClass));
                    
                    discovery = new Intent(r.getText(R.string.intent_device_found).toString());
                    discovery.putExtra("ADDRESS", new String(deviceName));
                    discovery.putExtra("CLASS", Integer.parseInt(new String(deviceClass)));
                    discovery.putExtra("RSSI", 1);
                    ctx.broadcastIntent(discovery);

                }

                log.info("Finishing discovery process");
                discovery = new Intent(r.getText(R.string.intent_discovery_completed).toString());
                ctx.broadcastIntent(discovery);
                clientSocket.close();
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
        }
    }
}

public class FluidNexusBtSimulator {
    
    private boolean simulating;
    private Handler serviceHandler = new Handler();

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private Socket clientSocket;
    PrintWriter out;
    BufferedReader in;

    ArrayList<Vector> services = null;
    Resources r;
    Context ctx;

    public FluidNexusBtSimulator(Context context, Resources resources) {
        log.info("Starting Bluetooth Simulator");

        simulating = true;
        r = resources;
        ctx = context;
    }

    public FluidNexusBtSimulator(boolean areSimulating, Context context, Resources resources) {
        simulating = areSimulating;
        r = resources;
        ctx = context;

        if (simulating) {
            log.info("Starting Bluetooth Simulator");
        } else {
            log.info("Starting Bluetooth (using proxy on local machine)");
        }
    }


    public boolean startDiscovery() {
        log.info("Starting remote device discovery");

        if (simulating) {
            log.info("starting simulated discovery task");
            FluidNexusBtSimulatorDiscoveryTask task = new FluidNexusBtSimulatorDiscoveryTask(ctx, r);
            Thread thr = new Thread(task);
            thr.start();
        } else {
            log.info("starting socket discovery task");
            FluidNexusBtSocketDiscoveryTask task = new FluidNexusBtSocketDiscoveryTask(ctx, r);
            Thread thr = new Thread(task);
            thr.start();

        }       
        return true;
    }

    public void sendData(String title, String data, String time, String hash) {
        if (simulating) {
            sendDataSimulating(title, data, time, hash);            
        } else {
            sendDataSocket(title, data, time, hash);
        }
    }

    public void sendDataSimulating(String title, String data, String time, String hash) {
        try {
            // Simulating the time it would take to send a message
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            log.error("thread interrupted");
        }
    }


    public void sendDataSocket(String title, String data, String time, String hash) {
        ArrayList<Vector> services = new ArrayList<Vector>();
        clientSocket = null;
        out = null;
        in = null;

        String bridgeAddress = "10.0.2.2";
        String port = "7030";

        try {
            clientSocket = new Socket(bridgeAddress, Integer.parseInt(port));
            log.info("Socket created");
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (UnknownHostException e) {
            log.error("I don't know about host" + bridgeAddress + ":" + port + ": " + e);
            return;
        } catch (SocketTimeoutException e) {
            log.error("Connection timeout: " + e);
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
            System.exit(1);
        }

        //out.println("This is from inside android");
        try {
                out.write("02");
                out.flush();

                String titleLength = String.format("%03d", title.length());
                String dataLength = String.format("%06d", data.length());

                out.write(titleLength);
                out.flush();
                out.write(dataLength);
                out.flush();
                out.write(title);
                out.flush();
                out.write(data);
                out.flush();
                out.write(time);
                out.flush();
                out.write(hash);
                out.flush();

                log.info("Finshing sending the data");
                clientSocket.close();
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
        }
    }

    public ArrayList<Vector> getServices(String address) {
        /*
         * [('00:02:EE:6B:86:09', None, u'SDP Server'), 
         * ('00:02:EE:6B:86:09', 1, u'Hands-Free Audio Gateway'), 
         * ('00:02:EE:6B:86:09', 10, u'OBEX File Transfer'), 
         * ('00:02:EE:6B:86:09', 11, u'SyncMLClient'), 
         * ('00:02:EE:6B:86:09', 12, u'Nokia OBEX PC Suite Services'), 
         * ('00:02:EE:6B:86:09', 9, u'OBEX Object Push'), 
         * ('00:02:EE:6B:86:09', 2, u'Dial-Up Networking'), 
         * ('00:02:EE:6B:86:09', 3, u'AppleAgent'), 
         */

        if (simulating) {
            log.info("starting simulated service discovery");
            services = getServicesSimulator(address);
        } else {
            log.info("starting socket service discovery");
            services = runServiceSearch(address);
        }

        //Intent serviceDiscovery = new Intent(r.getText(R.string.intent_service_discovery_completed).toString());
        //ctx.broadcastIntent(serviceDiscovery);

        return services;
    }

    private ArrayList<Vector> getServicesSimulator(String address) {
        Random rand = new Random();
        ArrayList<Vector> services;

        // Most of the time, generate service list with Fluid Nexus service
        if (rand.nextFloat() > 0.2) {
            services = generateStandardServices(true);
        } else {
            services = generateStandardServices(false);
        }

        return services;
    }

    private ArrayList<Vector> generateStandardServices(boolean includeFluidNexus) {
        ArrayList<Vector> services = new ArrayList<Vector>();

        Vector<String> service;

        service = new Vector<String>();
        service.add("None");
        service.add("SDP Server");
        services.add(service);

        service = new Vector<String>();
        service.add("1");
        service.add("Hands-Free Audio Gateway");
        services.add(service);

        service = new Vector<String>();
        service.add("10");
        service.add("OBEX File Transfer");
        services.add(service);

        service = new Vector<String>();
        service.add("11");
        service.add("SyncMLClient");
        services.add(service);

        service = new Vector<String>();
        service.add("12");
        service.add("Nokia OBEX PC Suite Services");
        services.add(service);

        service = new Vector<String>();
        service.add("9");
        service.add("OBEX Object Push");
        services.add(service);

        service = new Vector<String>();
        service.add("2");
        service.add("Dial-up Networking");
        services.add(service);

        if (includeFluidNexus) {
            service = new Vector<String>();
            service.add("4");
            service.add("FluidNexus");
            services.add(service);

            service = new Vector<String>();
            service.add("5");
            service.add(":00000000000000000000000000000000");
            services.add(service);

        }

        return services;
    }

    public ArrayList<Vector> runServiceSearch(String address) {
        ArrayList<Vector> services = new ArrayList<Vector>();
        clientSocket = null;
        out = null;
        in = null;

        String bridgeAddress = "10.0.2.2";
        String port = "7030";

        try {
            clientSocket = new Socket(bridgeAddress, Integer.parseInt(port));
            log.info("Socket created");
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (UnknownHostException e) {
            log.error("I don't know about host" + address + ":" + port + ": " + e);
            return services;
        } catch (SocketTimeoutException e) {
            log.error("Connection timeout: " + e);
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
            System.exit(1);
        }

        //out.println("This is from inside android");
        try {
                out.write("01");
                out.flush();

                out.write(address);
                out.flush();

                char[] numServices = new char[2];
                in.read(numServices, 0, 2);
                String numString = new String(numServices);
                log.info(new String(numServices));
                
                if (Integer.parseInt(numString) == 0) {
                    log.info("no matching services found");
                } else {
                    for (int index = 0; index < Integer.parseInt(numString); ++index) {
                        Vector<String> service = new Vector<String>();
                        char[] serviceName = new char[32];
                        in.read(serviceName, 0, 32);
                        service.add("0");
                        service.add(new String(serviceName));
                        services.add(service);
                        log.info(new String(serviceName));
                    }

                    // This adds in FluidNexus to the service list,
                    // as it was removed by the bridge before sending
                    // the hashes
                    Vector<String> service = new Vector<String>();
                    service.add("0");
                    service.add("FluidNexus");
                    services.add(service);
                }


                log.info("Finishing service discovery process");
                clientSocket.close();
        } catch (IOException e) {
            log.error("Some type of I/O exception: " + e);
        }

        return services;
    }


}

