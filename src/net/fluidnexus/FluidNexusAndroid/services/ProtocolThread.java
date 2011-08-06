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
import java.net.Socket;
import java.net.UnknownHostException;
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
import net.fluidnexus.FluidNexusAndroid.Protos;

public class ProtocolThread extends Thread {
    private static Logger log = Logger.getLogger("FluidNexus"); 
    public Socket socket = null;
    public DataInputStream inputStream = null;
    public DataOutputStream outputStream = null;

    private Cursor hashesCursor;
    private Cursor dataCursor;

    // UUID
    public static final UUID FluidNexusUUID = UUID.fromString("bd547e68-952b-11e0-a6c7-0023148b3104");

    private ArrayList<String> hashList = new ArrayList<String>();

    public final Handler threadHandler;

    private char connectedState = 0x00;

    public final char STATE_START = 0x00;
    public final char STATE_WRITE_HELO = 0x10;
    public final char STATE_READ_HELO = 0x20;        
    public final char STATE_WRITE_HASHES = 0x30;
    public final char STATE_READ_MESSAGES = 0x40;
    public final char STATE_WRITE_SWITCH = 0x50;
    public final char STATE_READ_HASHES = 0x60;
    public final char STATE_WRITE_MESSAGES = 0x70;
    public final char STATE_READ_SWITCH = 0x80;
    public final char STATE_WRITE_DONE = 0x90;
    public final char STATE_READ_DONE = 0xA0;
    public final char STATE_QUIT = 0xF0;

    public final char HELO = 0x10;
    public final char HASHES = 0x20;
    public final char MESSAGES = 0x30;
    public final char SWITCH = 0x80;
    public final char DONE = 0xF0;

    public final HashMap<Integer, String> stateMapping = new HashMap<Integer, String>();

    private HashSet<String> hashesToSend = new HashSet<String>();

    // Keeping track of items from the database
    private HashSet<String> currentHashes = new HashSet<String>();
    private ArrayList<Vector> currentData = new ArrayList<Vector>();

    // For database access
    public MessagesProviderHelper messagesProviderHelper = null;
    public Context context = null;

    // keeps track of connected clients
    // will likely always be only a single client, but what the hey
    public ArrayList<Messenger> clients = new ArrayList<Messenger>();
    public static final int MSG_REGISTER_CLIENT = 0x10;
    public static final int MSG_UNREGISTER_CLIENT = 0x11;
    public static final int MSG_NEW_MESSAGE_RECEIVED = 0x20;
    public static final int MSG_BLUETOOTH_SCAN_FREQUENCY = 0x30;

    // Thread Handler message constants
    public static final int CONNECT_THREAD_FINISHED = 0x30;
    public static final int UPDATE_HASHES = 0x31;

    public ProtocolThread(Context ctx, Handler givenHandler, ArrayList<Messenger> givenClients) {
        threadHandler = givenHandler;

        clients = givenClients;
        context = ctx;

        // setup database object
        messagesProviderHelper = new MessagesProviderHelper(context);

        //updateHashes();
        //updateData();
        
        setStateMapping();
        setConnectedState(STATE_START);
    }

    /**
     * Setup our state mapping for easy log viewing
     */
    public void setStateMapping() {
        stateMapping.put(0x00, "STATE_START");
        stateMapping.put(0x10, "STATE_WRITE_HELO");
        stateMapping.put(0x20, "STATE_READ_HELO");
        stateMapping.put(0x30, "STATE_WRITE_MESSAGES");
        stateMapping.put(0x40, "STATE_READ_MESSAGES");
        stateMapping.put(0x50, "STATE_WRITE_SWITCH");
        stateMapping.put(0x60, "STATE_READ_HASHES");
        stateMapping.put(0x70, "STATE_WRITE_MESSAGES");
        stateMapping.put(0x80, "STATE_READ_SWITCH");
        stateMapping.put(0x90, "STATE_WRITE_DONE");
        stateMapping.put(0xA0, "STATE_READ_DONE");
        stateMapping.put(0xF0, "STATE_QUIT");
    }

    /**
     * Set the input stream
     */
    public void setInputStream(DataInputStream is) {
        inputStream = is;
    }

    /**
     * Set the output stream
     */
    public void setOutputStream(DataOutputStream os) {
        outputStream = os;
    }

    /**
     * Set our hashes
     */
    public void setHashes(HashSet<String> givenHashes) {
        currentHashes = givenHashes;
    }

    /**
     * Set our data
     */
    public void setData(ArrayList<Vector> givenData) {
        currentData = givenData;
    }


    /**
     * Update the hashes in our HashSet based on items from the database
     */
    public void updateHashes(boolean sendBlacklist) {
        //hashesCursor = dbAdapter.services();
        //hashesCursor.moveToFirst();

        if (sendBlacklist) {
            hashesCursor = messagesProviderHelper.hashes();
        } else {
            hashesCursor = messagesProviderHelper.hashesNoBlacklist();
        }

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
     * TODO
     * Probably shouldn't do this atomically like this...
     */
    public void updateData() {
        dataCursor = messagesProviderHelper.outgoing();
        dataCursor.moveToFirst();
        currentData.clear();

        String[] fields = new String[] {MessagesProvider.KEY_MESSAGE_HASH, MessagesProvider.KEY_TIME, MessagesProvider.KEY_RECEIVED_TIME, MessagesProvider.KEY_TITLE, MessagesProvider.KEY_CONTENT};
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

    private void setupStreams() {
        try {
            socket = new Socket("", 9999);
        } catch (UnknownHostException e) {
            log.error("Unknown host.");
        } catch (IOException e) {
            log.error("Unable to create new socket.");
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

        inputStream = tmpIn;
        outputStream = tmpOut;

        setConnectedState(STATE_WRITE_HELO);

    }


    private void sendNewMessageMsg() {
        for (int i = 0; i < clients.size(); i++) {
            try {
                clients.get(i).send(Message.obtain(null, MSG_NEW_MESSAGE_RECEIVED));
            } catch (RemoteException e) {
                // If we get here, the client is dead, and we should remove it from the list
                log.debug("Removing client: " + clients.get(i));
                clients.remove(i);
            }
        }
    }

    /**
     * Set the state of the connected thread
     * @param state char that defines the connected thread state
     */
    public synchronized void setConnectedState(char newState) {
        int iNew = (int) newState;
        int iConnected = (int) connectedState;
        //String tmpNewState = Integer.toString(iNew);
        String tmpNewState = stateMapping.get(iNew);
        //String tmpConnectedState = Integer.toString(iConnected);
        String tmpConnectedState = stateMapping.get(iConnected);
        log.debug("Changing connected thread state from " + tmpConnectedState + " to " + tmpNewState);
        connectedState = newState;
    }

    /**
     * Get the current state value
     */
    public synchronized char getConnectedState() {
        return connectedState;
    }

    /**
     * Write a specified command to the server
     * @param char command
     */
    public void writeCommand(char command) {
        // Send the command to the server
        try {
            outputStream.writeChar(command);
            outputStream.flush();
        } catch (IOException e) {
            log.error("Exception during writing to outputStream: " + e);
            cleanupConnection();
        }
    }

    /**
     * Read a command from the server.
     * @return char command
     */
    public char readCommand() {
        // Read back the result
        char tmp = 0;
        try {
            tmp = inputStream.readChar();
        } catch (IOException e) {
            log.error("Exception during reading from inputStream: " + e);
            cleanupConnection();
        }
        return tmp;
    }

    /**
     * Write our hashes in protobuf format
     */
    public void writeHashes() {
        Protos.FluidNexusHashes.Builder hashesBuilder = Protos.FluidNexusHashes.newBuilder();

        for (String currentHash: currentHashes) {
            hashesBuilder.addMessageHash(currentHash);
        }

        Protos.FluidNexusHashes hashes = hashesBuilder.build();

        byte[] toSend = hashes.toByteArray();
        try {
            writeCommand(HASHES);
            outputStream.writeInt(toSend.length);
            outputStream.flush();
            outputStream.write(toSend, 0, toSend.length);
            outputStream.flush();
        } catch (IOException e) {
            log.error("Error writing hashes to output stream: " + e);
            cleanupConnection();
        }

        setConnectedState(STATE_READ_MESSAGES);
    }

    /**
     * Read our messages sent in protobuf format
     */
    public void readMessages() {
        try {
            int messageSize = inputStream.readInt();
            byte[] messagesArray = new byte[messageSize];
            
            // TODO
            // Read this in in chunks, update progress bar in notification
            inputStream.readFully(messagesArray, 0, messageSize);

            Protos.FluidNexusMessages messages = Protos.FluidNexusMessages.parseFrom(messagesArray);

            int count = 0;
            for (Protos.FluidNexusMessage message : messages.getMessageList()) {

                if (message.hasMessageAttachmentOriginalFilename()) {
                    // TODO
                    // Assuming a location for the attachments here...this should be sent in an intent on service start
                    File dataDir = Environment.getExternalStorageDirectory();
                    File attachmentsDir = new File(dataDir.getAbsolutePath() + "/FluidNexusAttachments");
                    attachmentsDir.mkdirs();
                    
                    String message_hash = messagesProviderHelper.makeSHA256(message.getMessageTitle() + message.getMessageContent());

                    String filenameArray[] = message.getMessageAttachmentOriginalFilename().split("\\.");
                    String extension = filenameArray[filenameArray.length-1];
                    File destinationPath = new File(attachmentsDir + "/" + message_hash + "." + extension);
                    BufferedOutputStream f = null;
                    try {
                        f = new BufferedOutputStream(new FileOutputStream(destinationPath));
                        byte[] ba = message.getMessageAttachment().toByteArray();
                        f.write(ba);
                    } finally {
                        if (f != null) {
                            f.close();
                        }
                    }



                    messagesProviderHelper.add_received(0, message.getMessageTimestamp(), message.getMessageReceivedTimestamp(), message.getMessageTitle(), message.getMessageContent(), destinationPath.getAbsolutePath(), message.getMessageAttachmentOriginalFilename(), message.getMessagePublic(), message.getMessageTtl());
                } else {
                    messagesProviderHelper.add_received(0, message.getMessageTimestamp(), message.getMessageReceivedTimestamp(), message.getMessageTitle(), message.getMessageContent(), message.getMessagePublic(), message.getMessageTtl());
                }
                count += 1;
            }
            if (count > 0) {
                sendNewMessageMsg();
                Message msg = threadHandler.obtainMessage(UPDATE_HASHES);
                threadHandler.sendMessage(msg);
            }

            setConnectedState(STATE_WRITE_SWITCH);
        } catch (IOException e) {
            log.error("Error writing hashes to output stream: " + e);
            cleanupConnection();
        }

    }

    /**
     * Read hashes from the server
     */
    public void readHashes() {
        try {
            int hashesSize = inputStream.readInt();
            byte[] hashesArray = new byte[hashesSize];
            inputStream.readFully(hashesArray, 0, hashesSize);

            Protos.FluidNexusHashes hashes = Protos.FluidNexusHashes.parseFrom(hashesArray);

            // Create new hash set of all the items
            hashesToSend.clear();                
            hashesToSend = new HashSet<String>(currentHashes);

            // Get their hashes
            HashSet<String> theirHashes = new HashSet<String>();
            for (String hash : hashes.getMessageHashList()) {
                theirHashes.add(hash);
            }

            // Take the difference to know which hashes of ours to send
            hashesToSend.removeAll(theirHashes);

            setConnectedState(STATE_WRITE_MESSAGES);
        } catch (IOException e) {
            log.error("Error reading hashes from input stream: " + e);
            cleanupConnection();
        }

    }

    /**
     * Write our messages to the server based on the hashes that the server doesn't have
     */
    public void writeMessages() {
        try {

            Protos.FluidNexusMessages.Builder messagesBuilder = Protos.FluidNexusMessages.newBuilder();

            for (String currentHash: hashesToSend) {
                Protos.FluidNexusMessage.Builder messageBuilder = Protos.FluidNexusMessage.newBuilder();

                Cursor localCursor = messagesProviderHelper.returnItemBasedOnHash(currentHash);
    
                String title = localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_TITLE));
                String content = localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_CONTENT));
                Float timestamp = localCursor.getFloat(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_TIME));
                Float received_timestamp = localCursor.getFloat(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_RECEIVED_TIME));
                String attachmentPath = localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_ATTACHMENT_PATH));
                String attachmentOriginalFilename = localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME));
                localCursor.close();

                messageBuilder.setMessageTitle(title);
                messageBuilder.setMessageContent(content);
                messageBuilder.setMessageTimestamp(timestamp);
                messageBuilder.setMessageReceivedTimestamp(received_timestamp);

                if (!(attachmentPath.equals(""))) {
                    File file = new File(attachmentPath);
                    FileInputStream fin = new FileInputStream(file);
                    BufferedInputStream bin = new BufferedInputStream(fin);
                    int length = (int) file.length();

                    // TODO
                    // Is there a better way of doing this, other than reading everything in at once?
                    byte[] data = new byte[length];
                    bin.read(data, 0, length);
                    com.google.protobuf.ByteString bs = com.google.protobuf.ByteString.copyFrom(data);
                    messageBuilder.setMessageAttachmentOriginalFilename(attachmentOriginalFilename);
                    messageBuilder.setMessageAttachment(bs);
                }
                
                Protos.FluidNexusMessage message = messageBuilder.build();
                messagesBuilder.addMessage(message);
            }

            Protos.FluidNexusMessages messages = messagesBuilder.build();

            byte[] messagesSerialized = messages.toByteArray();
            int messagesSerializedLength = messagesSerialized.length;
            log.debug("Writing messages length of: " + messagesSerializedLength);
            writeCommand(MESSAGES);
            outputStream.writeInt(messagesSerializedLength);
            outputStream.flush();

            // TODO
            // Write this out via chunks, update progress bar in intent
            outputStream.write(messagesSerialized, 0, messagesSerializedLength);
            outputStream.flush();

            setConnectedState(STATE_READ_SWITCH);
        } catch (IOException e) {
            log.error("Error writing messages to output stream: " + e);
            cleanupConnection();
        }

    }

    /**
     * Our thread's run method
     */
    public void run() {

    }

    /**
     * Cleanup the connection and exit out of main loop
     */
    public void cleanupConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            log.error("close() of ConnectedThread socket failed: " + e);
        }

        log.info("Closing the socket and ending the thread");
        Message msg = threadHandler.obtainMessage(CONNECT_THREAD_FINISHED);
        Bundle bundle = new Bundle();
        //bundle.putString("address", device.getAddress());
        msg.setData(bundle);
        threadHandler.sendMessage(msg);
        setConnectedState(STATE_QUIT);
    }

    public void cancel() {
        try {
            socket.close();
        } catch (IOException e) {
            log.error("close() of ConnectedThread socket failed: " + e);
            cleanupConnection();
        }
    }
}

