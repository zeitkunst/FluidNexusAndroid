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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.Toast;

public class FluidNexusServer extends Service {
    private Socket clientSocket;
    private ServerSocket serverSocket;
    PrintWriter out;
    BufferedReader in;

    private NotificationManager nm;
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexusServer"); 

    public class FluidNexusServerBinder extends Binder {
        FluidNexusServer getService() {
            return FluidNexusServer.this;
        }
    }

    @Override
    protected void onCreate() {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        log.info("starting fluid nexus client");
        showNotification();
        connectSocket();
    }

    @Override
    protected void onDestroy() {

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
            System.exit(1);
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

    private final IBinder binder = new FluidNexusServerBinder();

    private void showNotification() {
        Intent contentIntent = new Intent(this, FluidNexusAndroid.class);
        Intent appIntent = new Intent(this, FluidNexusAndroid.class);

        CharSequence text = "this is a test.";

        nm.notify(2341234,
                new Notification(this,
                    R.drawable.fluid_nexus_icon_status,
                    text,
                    System.currentTimeMillis(),
                    "This is a label",
                    text,
                    contentIntent,
                    R.drawable.fluid_nexus_icon_status,
                    "Fluid Nexus",
                    appIntent));
    }
}
