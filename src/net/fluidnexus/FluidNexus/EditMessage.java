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
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import net.fluidnexus.FluidNexus.provider.MessagesProvider;
import net.fluidnexus.FluidNexus.provider.MessagesProviderHelper;
public class EditMessage extends Activity {

    private MessagesProviderHelper messagesProviderHelper = null;

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private EditText titleEditText;
    private EditText messageEditText;
    private long id = -1;

    // Activity result codes
    private static final int SELECT_TEXT = 0;
    private static final int SELECT_AUDIO = 1;
    private static final int SELECT_IMAGE = 2;
    private static final int SELECT_VIDEO = 3;

    private static final int DIALOG_SAVE = 0;
    private static final int DIALOG_SAME = 1;
    private int DIALOG_RESULT = -1;

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

        messagesProviderHelper = new MessagesProviderHelper(this);

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_edit_title);
        Bundle extras = getIntent().getExtras();
        
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

        if (extras != null) {
            id = extras.getInt(MessagesProvider._ID);
            originalTitle = extras.getString(MessagesProvider.KEY_TITLE);
            originalMessage = extras.getString(MessagesProvider.KEY_CONTENT); 
            originalAttachmentPath = extras.getString(MessagesProvider.KEY_ATTACHMENT_PATH); 
            originalAttachmentOriginalFilename = extras.getString(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME); 
            originalType = extras.getInt(MessagesProvider.KEY_TYPE); 
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
                attachmentPath = "";
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
    private int saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        ContentValues values = new ContentValues();
        values.put(MessagesProvider.KEY_TITLE, title);
        values.put(MessagesProvider.KEY_CONTENT, message);
        values.put(MessagesProvider.KEY_MESSAGE_HASH, MessagesProviderHelper.makeSHA256(title + message));

        if (attachmentPath != null) {
            if (attachmentPath.equals("")) {
                values.put(MessagesProvider.KEY_ATTACHMENT_PATH, "");
                values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, "");

            } else {
                File file = new File(attachmentPath);
                values.put(MessagesProvider.KEY_ATTACHMENT_PATH, attachmentPath);
                values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, file.getName());
            }
        }

        return messagesProviderHelper.updateItemByID(id, values);
    }

    /**
     * Check if the text has changed
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
        builder.setMessage(R.string.really_save_dialog)
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    int result = saveState();
                    if (result != 0) {
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast t = Toast.makeText(getApplicationContext(), R.string.same_dialog, Toast.LENGTH_LONG);
                        t.setGravity(Gravity.CENTER, 0, 0);
                        t.show();
                    }
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
