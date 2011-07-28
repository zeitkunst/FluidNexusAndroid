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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.database.Cursor;
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
public class BluetoothClientThread extends ProtocolThread {
    private static Logger log = Logger.getLogger("FluidNexus"); 
    private final BluetoothSocket socket;
    private final BluetoothDevice device;

    //private DataInputStream inputStream = null;
    //private DataOutputStream outputStream = null;

    private ArrayList<String> hashList = new ArrayList<String>();

    private final Handler threadHandler;

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

    public BluetoothClientThread(Context ctx, BluetoothDevice remoteDevice, Handler givenHandler, ArrayList<Messenger> givenClients) {
        super(ctx, givenHandler, givenClients);
        setName("ConnectThread: " + remoteDevice.getAddress());
        device = remoteDevice;
        threadHandler = givenHandler;

        BluetoothSocket tmp = null;
        
        clients = givenClients;

        context = ctx;

        setConnectedState(STATE_START);
        // Get our socket to the remove device
        try {
            tmp = device.createRfcommSocketToServiceRecord(FluidNexusUUID);
        } catch (IOException e) {
            log.error("create() failed: " + e);
            cleanupConnection();
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

        //inputStream = tmpIn;
        //outputStream = tmpOut;

        setInputStream(tmpIn);
        setOutputStream(tmpOut);

        setConnectedState(STATE_WRITE_HELO);
    }


    /**
     * Cleanup the connection and exit out of main loop
     */
    @Override
    public void cleanupConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            log.error("close() of ConnectedThread socket failed: " + e);
        }

        log.info("Closing the socket and ending the thread");
        Message msg = threadHandler.obtainMessage(ProtocolThread.CONNECT_THREAD_FINISHED);
        Bundle bundle = new Bundle();
        bundle.putString("address", device.getAddress());
        msg.setData(bundle);
        threadHandler.sendMessage(msg);
        setConnectedState(STATE_QUIT);
    }

    /**
     * Our thread's run method
     */
    @Override
    public void run() {
        log.info("Begin BluetoothClientThread");
        
        char command = 0x00;            
        while (super.getConnectedState() != STATE_QUIT) {
            switch(super.getConnectedState()) {
                case STATE_WRITE_HELO:
                    super.writeCommand(HELO);
                    super.setConnectedState(STATE_READ_HELO);
                    break;
                case STATE_READ_HELO:
                    command = super.readCommand();
                    if (command != HELO) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_WRITE_HASHES);
                    }
                    break;
                case STATE_WRITE_HASHES:
                    super.writeHashes();
                    break;
                case STATE_READ_MESSAGES:
                    command = super.readCommand();
                    if (command != MESSAGES) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.readMessages();
                    }

                    break;
                case STATE_WRITE_SWITCH:
                    super.writeCommand(SWITCH);
                    super.setConnectedState(STATE_READ_HASHES);
                    break;
                case STATE_READ_HASHES:
                    command = super.readCommand();
                    if (command != HASHES) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.readHashes();
                    }

                    break;
                case STATE_WRITE_MESSAGES:
                    super.writeMessages();
                    break;
                case STATE_READ_SWITCH:
                    command = super.readCommand();
                    if (command != SWITCH) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_WRITE_DONE);
                    }
                    break;
                case STATE_WRITE_DONE:
                    super.writeCommand(DONE);
                    super.setConnectedState(STATE_READ_DONE);
                    break;
                case STATE_READ_DONE:
                    command = super.readCommand();
                    if (command != DONE) {
                        log.error("Received unexpected command: " + command);
                        cleanupConnection();
                    } else {
                        super.setConnectedState(STATE_QUIT);
                    }

                    break;
                default:
                    cleanupConnection();
                    break;
            }            
        }

        // We're done here
        cleanupConnection();

    }

}

