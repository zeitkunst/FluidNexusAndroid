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


package net.fluidnexus.FluidNexus;

import java.lang.CharSequence;


import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class FluidNexusEditMessage extends Activity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private EditText titleEditText;
    private EditText messageEditText;
    private FluidNexusDbAdapter dbHelper;  
    private long id = -1;

    private static final int DIALOG_REALLY_DISCARD = 0;

    // This keeps track of how many times the text has been changed
    // The text is always changed at least once for each EditText on the screen, so we just keep track of how many times it has changed; if it's over 2, then we know that the text needs to be saved
    // TODO
    // There's probably a better way of doing this...
    private int textChanged = 0;

    TextWatcher textWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            textChanged += 1;
            if (textChanged > 2) {
                textChanged = 3;
            }
            log.debug("textChanged: " + textChanged);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_add_outgoing_title);
        Bundle extras = getIntent().getExtras();
        
        titleEditText = (EditText) findViewById(R.id.title_edit);
        messageEditText = (EditText) findViewById(R.id.message_edit);
        titleEditText.addTextChangedListener(textWatcher);
        messageEditText.addTextChangedListener(textWatcher);

        Button saveButton = (Button) findViewById(R.id.save_message_button);
        Button discardButton = (Button) findViewById(R.id.discard_message_button);


        if (extras != null) {
            id = extras.getInt(FluidNexusDbAdapter.KEY_ID);
            String title = extras.getString(FluidNexusDbAdapter.KEY_TITLE);
            String message = extras.getString(FluidNexusDbAdapter.KEY_DATA); 
            
            if (title != null) {
                titleEditText.setText(title);
            }

            if (message != null) {
                messageEditText.setText(message);

            }
        } else {
            log.error("FluidNexusEditMessage: Unable to get any extras...this should never happen :-)");
        }

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState();
                setResult(RESULT_OK);
                finish();
            }
        });

        discardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (textChanged > 2) {
                    showDialog(DIALOG_REALLY_DISCARD);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        titleEditText.removeTextChangedListener(textWatcher);
        messageEditText.removeTextChangedListener(textWatcher);
    }


    private void saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        ContentValues values = new ContentValues();
        values.put(FluidNexusDbAdapter.KEY_TITLE, title);
        values.put(FluidNexusDbAdapter.KEY_DATA, message);
        values.put(FluidNexusDbAdapter.KEY_HASH, FluidNexusDbAdapter.makeMD5(title + message));
        dbHelper.updateItemByID(id, values);
    }


    /**
     * Method of creating dialogs for this activity
     * @param id ID of the dialog to create
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_REALLY_DISCARD:
                dialog = reallyDiscardDialog();
                break;
            default:
                dialog = null;
        }

        return dialog;
    }
    
    /**
     * Method to create our really delete dialog
     */
    private AlertDialog reallyDiscardDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("The message has changed; are you sure you don't want to save?")
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    setResult(RESULT_OK);
                    finish();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }

}
