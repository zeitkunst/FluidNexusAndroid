package org.zeitkunst.FluidNexus;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

public class FluidNexusBtSimulator {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    public FluidNexusBtSimulator() {
        log.info("Starting Bluetooth Simulator");
    }

    public boolean startDiscovery() {
        log.info("Starting remote device discovery");

        return true;
    }

    public String[] listRemoteDevices() {
        log.info("Returning list of remote devices");

        String[] remoteDeviceList = {"Foo", "bar"};

        return remoteDeviceList;
        
    }
}

