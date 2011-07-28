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

import net.fluidnexus.FluidNexusAndroid.provider.MessagesProvider;
import net.fluidnexus.FluidNexusAndroid.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexusAndroid.Logger;
import net.fluidnexus.FluidNexusAndroid.MainActivity;
import net.fluidnexus.FluidNexusAndroid.R;

/**
 * This is a base class that all of our network modality-specific threads subclass
 */
public class ServiceThread extends Thread {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // Service context
    public Context context = null;

    // Handler for the starting service

    // State of the system
    public int state;

    // Keeping track of items from the database
    public MessagesProviderHelper messagesProviderHelper = null;
    public HashSet<String> currentHashes = new HashSet<String>();
    public ArrayList<Vector> currentData = new ArrayList<Vector>();
    private Cursor hashesCursor;
    private Cursor dataCursor;

    // Thread Handler message constants
    public final int CONNECT_THREAD_FINISHED = 0x30;
    public final int UPDATE_HASHES = 0x31;
    public final int SCAN_FREQUENCY_CHANGED = 0x32;

    // Set our scan frequency
    public int scanFrequency = 120;

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    public ArrayList<Messenger> clients = new ArrayList<Messenger>();

    /**
     * Constructor for the thread that does discovery
     */
    public ServiceThread(Context ctx, ArrayList<Messenger> givenClients) {
        
        context = ctx;
        clients = givenClients;
        messagesProviderHelper = new MessagesProviderHelper(context);

        setName("GenericServiceThread");

        updateHashes();
        updateData();
    }

    /**
     * Set the scan frequency
     * @param scanFrequency seconds to wait between scans
     */
    public void setScanFrequency(int newScanFrequency) {
        scanFrequency = newScanFrequency;
    }

    /**
     * Return the scan frequency
     */
    public int getScanFrequency() {
        return scanFrequency;
    }

    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current service state
     */
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
    public void run() {
    }

    /**
     * Update the hashes in our HashSet based on items from the database
     */
    public void updateHashes() {
        //hashesCursor = dbAdapter.services();
        //hashesCursor.moveToFirst();
        hashesCursor = messagesProviderHelper.hashes();

        currentHashes = new HashSet<String>();
        currentHashes.clear();

        while (hashesCursor.isAfterLast() == false) {
            // Get the hash from the cursor
            currentHashes.add(hashesCursor.getString(hashesCursor.getColumnIndex(MessagesProvider.KEY_MESSAGE_HASH)));
            hashesCursor.moveToNext();
        }
        hashesCursor.close();

    }

    /**
     * Update the data based on items from the database
     */
    public void updateData() {
        dataCursor = messagesProviderHelper.outgoing();
        dataCursor.moveToFirst();
        currentData.clear();

        String[] fields = new String[] {MessagesProvider.KEY_MESSAGE_HASH, MessagesProvider.KEY_TIME, MessagesProvider.KEY_TITLE, MessagesProvider.KEY_CONTENT};
        while (dataCursor.isAfterLast() == false) {
            // I'm still not sure why I have to instantiate a new vector each time here, rather than using the local vector from earlier
            // This is one of those things of java that just makes me want to pull my hair out...
            Vector<String> tempVector = new Vector<String>();
            for (int i = 0; i < fields.length; i++) {
                int index = dataCursor.getColumnIndex(fields[i]);
                tempVector.add(dataCursor.getString(index));
            }
            currentData.add(tempVector);
            dataCursor.moveToNext();
        }
        dataCursor.close();
    }

    /** 
     * Cancel the thread
     */
    public void cancel() {

    }
}

