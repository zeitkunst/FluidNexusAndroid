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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

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

import net.fluidnexus.FluidNexus.provider.MessagesProvider;
import net.fluidnexus.FluidNexus.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexus.Logger;


/**
 * Thread that actually sends data to a connected device
 * TODO
 * probably need to move some of the socket creation bits to another part of the class
 */
public class ZeroconfServerThread extends ProtocolThread {
    private static Logger log = Logger.getLogger("FluidNexus"); 

    //private DataInputStream inputStream = null;
    //private DataOutputStream outputStream = null;

    private ArrayList<String> hashList = new ArrayList<String>();

    private char connectedState = 0x00;

    private final char STATE_START = 0x00;
    private final char STATE_WRITE_HELO = 0x10;
    private final char STATE_READ_HELO = 0x20;        
    private final char STATE_WRITE_HASHES = 0x30;
    private final char STATE_READ_MESSAGES = 0x40;
    private final char STATE_WRITE_SWITCH = 0x50;
    private final char STATE_READ_HASHES = 0x60;
    private final char STATE_WRITE_MESSAGES = 0x70;
    private final char STATE_READ_SWITCH = 0x80;
    private final char STATE_WRITE_DONE = 0x90;
    private final char STATE_READ_DONE = 0xA0;
    private final char STATE_QUIT = 0xF0;

    private final char HELO = 0x10;
    private final char HASHES = 0x20;
    private final char MESSAGES = 0x30;
    private final char SWITCH = 0x80;
    private final char DONE = 0xF0;

    private HashSet<String> hashesToSend = new HashSet<String>();

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();

    // Our zeroconf type
    private static final String zeroconfType = "_fluidnexus._tcp.local.";
    
    // jmdns infos
    private JmDNS jmdns = null;
    private ServiceInfo serviceInfo;
    private MulticastLock lock = null;
    private WifiManager wifiManager = null;

    public ZeroconfServerThread(Context ctx, Handler givenHandler, ArrayList<Messenger> givenClients) {

        super(ctx, givenHandler, givenClients);
        setName("ZeroconfServerThread");
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        lock = wifiManager.createMulticastLock("ZeroconfServerLock");
        lock.setReferenceCounted(true);
        
        //advertiseService();
    }

    /**
     * Advertise our service
     */
    public void advertiseService() {
        lock.acquire();
        log.debug("Advertising our service");
        serviceInfo = ServiceInfo.create(zeroconfType, "Fluid Nexus", 0, "Fluid Nexus Zeroconf server for android");
        try {
            jmdns = JmDNS.create();
            jmdns.registerService(serviceInfo);
        } catch (IOException e) {
            log.error("Unable to register zeroconf service.");
            e.printStackTrace();
        }
        lock.release();
    }

    @Override
    public void run() {
        while (true) {

        }
    }
}
