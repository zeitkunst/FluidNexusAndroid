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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
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


        /*
         * TODO
         * Check to see if things have been edited, load dialog to warn
         */
        discardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                setResult(RESULT_OK);
                finish();
            }
        });
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

}
