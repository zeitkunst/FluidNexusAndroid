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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

public class FluidNexusSettings extends Activity {
    private SharedPreferences prefs;
    private Editor prefsEditor;
    private CheckBox showMessages;
    private CheckBox simulateBluetooth;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.settings);
        setTitle(R.string.settings_title);

        Button saveButton = (Button) findViewById(R.id.settings_save_button);
        Button cancelButton = (Button) findViewById(R.id.settings_cancel_button);

        setupSettings();

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                boolean bluetoothChanged = saveSettings();
                Bundle bundle = new Bundle();
                bundle.putBoolean("bluetoothChanged", bluetoothChanged);
                /*setResult(RESULT_OK, null, bundle);*/
                setResult(RESULT_OK);
                finish();
            }
        });


        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                /*setResult(RESULT_OK, null, bundle);*/
                setResult(RESULT_OK);
                finish();
            }
        });

    }

    private void setupSettings() {
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        
        showMessages = (CheckBox) findViewById(R.id.show_messages_check);
        simulateBluetooth = (CheckBox) findViewById(R.id.simulate_bluetooth_check);
        
        showMessages.setChecked(prefs.getBoolean("ShowMessages", true));
        simulateBluetooth.setChecked(prefs.getBoolean("SimulateBluetooth", true));
    }

    private boolean saveSettings() {
        // TODO
        // * Go through this in a more streamlined fashion, probably 
        // using a Map to store preference and object type
        // * If simulate_bluetooth changes, pop up Toast to let user know
        // they need to restart their device/emulator
        // * If there are unsaved preferences when they hit cancel, 
        // pop up Dialog to let them save the preferences
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        prefsEditor = prefs.edit();

        showMessages = (CheckBox) findViewById(R.id.show_messages_check);
        simulateBluetooth = (CheckBox) findViewById(R.id.simulate_bluetooth_check);

        prefsEditor.putBoolean("ShowMessages", showMessages.isChecked());
        
        boolean previousBluetooth = prefs.getBoolean("SimulateBluetooth", true);
        boolean currentBluetooth = simulateBluetooth.isChecked();
        boolean bluetoothChanged;
        if (previousBluetooth == currentBluetooth) {
            bluetoothChanged = false;
        } else {
            bluetoothChanged = true;
        }
        prefsEditor.putBoolean("SimulateBluetooth", simulateBluetooth.isChecked());
        prefsEditor.commit();

        return bluetoothChanged;
    }

}
