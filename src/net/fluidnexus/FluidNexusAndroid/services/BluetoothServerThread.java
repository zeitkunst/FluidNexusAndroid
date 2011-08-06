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
import java.net.ServerSocket;
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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
 * Thread that actually sends data to a connected device
 */
public class BluetoothServerThread extends ProtocolThread {
    private static Logger log = Logger.getLogger("FluidNexus"); 

    //private DataInputStream inputStream = null;
    //private DataOutputStream outputStream = null;

    private ArrayList<String> hashList = new ArrayList<String>();

    private char connectedState = 0x00;
    private static final char STATE_WAIT_SERVER = 0xb0;

    private BluetoothServerSocket serverSocket = null;
    private BluetoothSocket socket = null;
    private BluetoothAdapter bluetoothAdapter = null;

    private HashSet<String> hashesToSend = new HashSet<String>();

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    ArrayList<Messenger> clients = new ArrayList<Messenger>();

    public BluetoothServerThread(Context ctx, Handler givenHandler, ArrayList<Messenger> givenClients) {

        super(ctx, givenHandler, givenClients);
        setName("BluetoothServerThread");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    /**
     * Do the client accept work
     */
    private void doClientAccept() {
        // Check for bluetooth adapter
        if (bluetoothAdapter == null) {
            super.setConnectedState(STATE_WAIT_SERVER);
        } else {
    
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Fluid Nexus", FluidNexusUUID);;
            } catch (IOException e) {
                log.error("Unable to create new server socket: " + e);
                cleanupConnection();
            }
    
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                log.error("Bluetooth server socket accept failed: " + e);
                cleanupConnection();
            } catch (NullPointerException e) {
                log.debug("Bluetooth server socket null pointer: " + e);
                cleanupConnection();
            }
    
            DataInputStream tmpIn = null;
            DataOutputStream tmpOut = null;
    
            try {
                tmpIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                tmpOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            } catch (IOException e) {
                log.error("Temp stream sockets not created");
                cleanupConnection();
            }
    
            setInputStream(tmpIn);
            setOutputStream(tmpOut);
            super.setConnectedState(STATE_READ_HELO);
        }
    }


    private void waitServer() {
        try {
            log.debug("Service thread sleeping for " + "300" + " seconds...");
            this.sleep(300 * 1000);
        } catch (InterruptedException e) {
            log.error("Thread sleeping interrupted: " + e);
        }

        super.setConnectedState(STATE_START);
    }

    /**
     * Cleanup the connection and exit out of main loop
     */
    @Override
    public void cleanupConnection() {
        try {

            if (serverSocket != null) {
                serverSocket.close();
            }

            if (socket != null) {
                socket.close();
            }

        } catch (IOException e) {
            log.error("close() of BluetoothServerThread socket failed: " + e);
        }

        setConnectedState(STATE_QUIT);
    }

    @Override
    public void run() {

        log.info("Begin Bluetooth server thread");
        
        char command = 0x00;            
        boolean done = false;
        while (!done) {
            switch(super.getConnectedState()) {
                case STATE_START:
                    doClientAccept();
                    break;
                case STATE_READ_HELO:
                    command = readCommand();
                    if (command != HELO) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_WRITE_HELO);
                    }
                    break;
                case STATE_WRITE_HELO:
                    writeCommand(HELO);
                    super.setConnectedState(STATE_READ_HASHES);
                    break;
                case STATE_READ_HASHES:
                    command = readCommand();
                    if (command != HASHES) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        readHashes();
                    }
                    break;
                case STATE_WRITE_MESSAGES:
                    writeMessages();
                    break;
                case STATE_READ_SWITCH:
                    command = readCommand();
                    if (command != SWITCH) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_WRITE_HASHES);
                    }
                    break;
                case STATE_WRITE_HASHES:
                    writeHashes();
                    break;
                case STATE_READ_MESSAGES:
                    command = readCommand();
                    if (command != MESSAGES) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        readMessages();
                    }
                    break;
                case STATE_WRITE_SWITCH:
                    writeCommand(SWITCH);
                    super.setConnectedState(STATE_READ_DONE);
                    break;
                case STATE_READ_DONE:
                    command = readCommand();
                    if (command != DONE) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_WRITE_DONE);
                    }

                    break;
                case STATE_WRITE_DONE:
                    writeCommand(DONE);
                    cleanupConnection();
                    break;
                case STATE_QUIT:
                    super.setConnectedState(STATE_START);
                    break;
                case STATE_WAIT_SERVER:
                    waitServer();
                    break;
                default:
                    done = true;
                    cleanupConnection();
                    break;
            }            
        }

        // We're done here
        cleanupConnection();
    }
}
