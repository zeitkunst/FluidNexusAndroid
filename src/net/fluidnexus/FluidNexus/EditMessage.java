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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

public class EditMessage extends Activity {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private EditText titleEditText;
    private EditText messageEditText;
    private MessagesDbAdapter dbAdapter;  
    private long id = -1;

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

    int originalType = 0;
    String originalTitle = null;
    String originalMessage = null;
    String originalAttachmentPath = null;
    String originalAttachmentOriginalFilename = null;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);


        // Create database instance
        dbAdapter = new MessagesDbAdapter(this);
        dbAdapter.open();

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_edit_title);
        Bundle extras = getIntent().getExtras();
        
        titleEditText = (EditText) findViewById(R.id.title_edit);
        messageEditText = (EditText) findViewById(R.id.message_edit);

        Button addAttachmentButton = (Button) findViewById(R.id.add_attachment_button);
        Button saveButton = (Button) findViewById(R.id.save_message_button);
        Button discardButton = (Button) findViewById(R.id.discard_message_button);
        Button removeAttachmentButton = (Button) findViewById(R.id.remove_attachment_button);
        attachmentLabel = (TextView) findViewById(R.id.attachment_label);
        attachmentLabel.setVisibility(View.GONE);

        Spinner attachmentSpinner = (Spinner) findViewById(R.id.add_attachment_spinner);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.add_attachment_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        attachmentSpinner.setAdapter(adapter);

        if (extras != null) {
            id = extras.getInt(MessagesDbAdapter.KEY_ID);
            originalTitle = extras.getString(MessagesDbAdapter.KEY_TITLE);
            originalMessage = extras.getString(MessagesDbAdapter.KEY_CONTENT); 
            originalAttachmentPath = extras.getString(MessagesDbAdapter.KEY_ATTACHMENT_PATH); 
            originalAttachmentOriginalFilename = extras.getString(MessagesDbAdapter.KEY_ATTACHMENT_ORIGINAL_FILENAME); 
            originalType = extras.getInt(MessagesDbAdapter.KEY_TYPE); 
            attachmentType = originalType;
            
            if (originalTitle != null) {
                titleEditText.setText(originalTitle);
            }

            if (originalMessage != null) {
                messageEditText.setText(originalMessage);
            }

            if (originalAttachmentPath != null) {
                if (!(originalAttachmentPath.equals(""))) {
                    attachmentLabel.setVisibility(View.VISIBLE);
                    attachmentLabel.setText(originalAttachmentPath);
                    attachmentPath = originalAttachmentPath;
                } else {
                    originalAttachmentPath = null;
                }
            }
            
            attachmentSpinner.setSelection(originalType);

        } else {
            log.error("EditMessage: Unable to get any extras...this should never happen :-)");
        }



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
                    //showDialog(DIALOG_REALLY_DISCARD);
                } else {
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == SELECT_IMAGE) || (requestCode == SELECT_AUDIO) || (requestCode == SELECT_VIDEO)) {
            if (resultCode == Activity.RESULT_OK) {
                attachmentUri = data.getData();
                attachmentPath = getRealPathFromURI(attachmentUri, resultCode);
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
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        // do something on back.
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        if (checkIfTextChanged()) {
            showDialog(DIALOG_SAVE);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    /**
     * Save the state of our edited message in the database
     */
    private void saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        ContentValues values = new ContentValues();
        values.put(MessagesDbAdapter.KEY_TITLE, title);
        values.put(MessagesDbAdapter.KEY_CONTENT, message);
        values.put(MessagesDbAdapter.KEY_MESSAGE_HASH, MessagesDbAdapter.makeMD5(title + message));

        if (attachmentPath != null) {
            File file = new File(attachmentPath);
            values.put(MessagesDbAdapter.KEY_ATTACHMENT_PATH, attachmentPath);
            values.put(MessagesDbAdapter.KEY_ATTACHMENT_ORIGINAL_FILENAME, file.getName());
        }
        
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

        if (attachmentPath != originalAttachmentPath) {
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

}
