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
                saveSettings();
                Bundle bundle = new Bundle();
                setResult(RESULT_OK, null, bundle);
                finish();
            }
        });


        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                setResult(RESULT_OK, null, bundle);
                finish();
            }
        });

    }

    private void setupSettings() {
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        
        showMessages = (CheckBox) findViewById(R.id.show_messages_check);
        
        showMessages.setChecked(prefs.getBoolean("ShowMessages", true));
    }

    private void saveSettings() {
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        prefsEditor = prefs.edit();

        showMessages = (CheckBox) findViewById(R.id.show_messages_check);

        prefsEditor.putBoolean("ShowMessages", showMessages.isChecked());
        prefsEditor.commit();
    }

}
