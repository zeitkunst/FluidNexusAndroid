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
    private FluidNexusDbAdapter dbAdapter;  
    private long id = -1;

    String originalTitle = null;
    String originalMessage = null;

    private static final int DIALOG_REALLY_DISCARD = 0;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);


        // Create database instance
        dbAdapter = new FluidNexusDbAdapter(this);
        dbAdapter.open();

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_edit_title);
        Bundle extras = getIntent().getExtras();
        
        titleEditText = (EditText) findViewById(R.id.title_edit);
        messageEditText = (EditText) findViewById(R.id.message_edit);

        Button saveButton = (Button) findViewById(R.id.save_message_button);
        Button discardButton = (Button) findViewById(R.id.discard_message_button);


        if (extras != null) {
            id = extras.getInt(FluidNexusDbAdapter.KEY_ID);
            originalTitle = extras.getString(FluidNexusDbAdapter.KEY_TITLE);
            originalMessage = extras.getString(FluidNexusDbAdapter.KEY_DATA); 
            
            if (originalTitle != null) {
                titleEditText.setText(originalTitle);
            }

            if (originalMessage != null) {
                messageEditText.setText(originalMessage);

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
                boolean textChanged = checkIfTextChanged();
                if (textChanged) {
                    showDialog(DIALOG_REALLY_DISCARD);
                } else {
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    /**
     * Save the state of our edited message in the database
     */
    private void saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        ContentValues values = new ContentValues();
        values.put(FluidNexusDbAdapter.KEY_TITLE, title);
        values.put(FluidNexusDbAdapter.KEY_DATA, message);
        values.put(FluidNexusDbAdapter.KEY_HASH, FluidNexusDbAdapter.makeMD5(title + message));
        dbAdapter.updateItemByID(id, values);
    }

    /**
     * Check if the text has changed
     * TODO is this going to be inefficient for very large bodies of text?
     * There is a problem with using the TextWatcher, as it is called even for events not from the keyboard.  Not sure how to use this otherwise.
     * @return true if it has, false otherwise
     */
    private boolean checkIfTextChanged() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        if (!(title.equals(originalTitle))) {
            return true;
        }

        if (!(message.equals(originalMessage))) {
            return true;
        }

        return false;
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
     * Method to create our really discard dialog
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
