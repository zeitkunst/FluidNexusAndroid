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
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

/*
 * TODO
 * * create menu for saving message
 * * capture back button so that we can warn user if they haven't saved a changed message
 */
public class AddOutgoing extends Activity {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private EditText titleEditText;
    private EditText messageEditText;
    private MessagesDbAdapter dbAdapter;  

    // Activity result codes
    private static final int SELECT_TEXT = 0;
    private static final int SELECT_AUDIO = 1;
    private static final int SELECT_IMAGE = 2;
    private static final int SELECT_VIDEO = 3;

    private static final int DIALOG_SAVE = 0;

    private int attachmentType = SELECT_TEXT;
    private Uri attachmentUri = null;
    private String attachmentPath = null;
    private TextView attachmentLabel = null;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        dbAdapter = new MessagesDbAdapter(this);
        dbAdapter.open();

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_add_outgoing_title);
        
        titleEditText = (EditText) findViewById(R.id.title_edit);
        messageEditText = (EditText) findViewById(R.id.message_edit);

        Button addAttachmentButton = (Button) findViewById(R.id.add_attachment_button);
        Button removeAttachmentButton = (Button) findViewById(R.id.remove_attachment_button);
        attachmentLabel = (TextView) findViewById(R.id.attachment_label);
        attachmentLabel.setVisibility(View.GONE);

        Spinner attachmentSpinner = (Spinner) findViewById(R.id.add_attachment_spinner);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.add_attachment_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        attachmentSpinner.setAdapter(adapter);

        attachmentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView adapter, View v, int i, long l) {
                // TODO
                // Make this less brittle...
                if (i == 0) {
                    attachmentType = SELECT_TEXT;
                } else if (i == 1) {
                    attachmentType = SELECT_AUDIO;

                } else if (i == 2) {
                    attachmentType = SELECT_IMAGE;
                } else if (i == 3) {
                    attachmentType = SELECT_VIDEO;

                }

            }

            @Override
            public void onNothingSelected(AdapterView arg0) {

            }
        });            

        addAttachmentButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (attachmentType != SELECT_TEXT) {
                    switch (attachmentType) {
                        case SELECT_AUDIO:
                            startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.INTERNAL_CONTENT_URI), attachmentType);
                            break;
                        case SELECT_IMAGE:
                            startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI), attachmentType);
                            break;
                        case SELECT_VIDEO:
                            startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.INTERNAL_CONTENT_URI), attachmentType);
                            break;
                        default:
                            break;
                    }
                }

            }
        });

        removeAttachmentButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                attachmentLabel.setVisibility(View.GONE);
                attachmentUri = null;
                attachmentPath = null;
            }
        });
       
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == SELECT_IMAGE) || (requestCode == SELECT_AUDIO) || (requestCode == SELECT_VIDEO)) {
            if (resultCode == Activity.RESULT_OK) {
                attachmentUri = data.getData();
                log.debug("Attachment Uri: " + attachmentUri.toString());
                attachmentPath = getRealPathFromURI(attachmentUri, resultCode);
                log.debug("Attachment Path: " + attachmentPath);
                attachmentLabel.setVisibility(View.VISIBLE);
                attachmentLabel.setText(attachmentPath);
            }
        }
    }

    /**
     * Get an actual path from a content URI
     */
    private String getRealPathFromURI(Uri contentUri, int type) {
        String[] proj = new String[1];
        String id;
        if (type == SELECT_AUDIO) {
            proj[0] = MediaStore.Audio.Media.DATA;
            id = MediaStore.Audio.Media.DATA;
        } else if (type == SELECT_IMAGE) {
            proj[0] = MediaStore.Images.Media.DATA;
            id = MediaStore.Images.Media.DATA;
        } else if (type == SELECT_VIDEO) {
            proj[0] = MediaStore.Video.Media.DATA;
            id = MediaStore.Video.Media.DATA;
        } else {
            proj[0] = MediaStore.Images.Media.DATA;
            id = MediaStore.Images.Media.DATA;
        }

        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(id);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }

    @Override
    public void onBackPressed() {
        // do something on back.
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        if (!(title.equals("")) || !(message.equals(""))) {
            showDialog(DIALOG_SAVE);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }


    /**
     * Method of creating dialogs for this activity
     * @param id ID of the dialog to create
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_SAVE:
                dialog = saveDialog();
                break;
            default:
                dialog = null;
        }

        return dialog;
    }


    /**
     * Method to create our save dialog
     */
    private AlertDialog saveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do you want to save this message?")
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    saveState();
                    setResult(RESULT_OK);
                    finish();
                }
            })
            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    setResult(RESULT_OK);
                    finish();

                }    
            });
        return builder.create();
    }

    /**
     * Save our state to the database
     */
    private void saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();


        if (attachmentPath == null) {
            dbAdapter.add_new(0, title, message);
        } else {
            File file = new File(attachmentPath);
            dbAdapter.add_new(attachmentType, title, message, attachmentPath, file.getName());
        }

    }

}
