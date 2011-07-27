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
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import net.fluidnexus.FluidNexus.provider.MessagesProvider;

public class ViewMessage extends Activity {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private ImageView typeImageView;
    private TextView titleTextView;
    private TextView messageTextView;
    private String attachment_path;
    private String attachment_original_filename;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.message_view);
        setTitle(R.string.message_view_title);
        Bundle extras = getIntent().getExtras();
        
        titleTextView = (TextView) findViewById(R.id.view_message_title);
        messageTextView = (TextView) findViewById(R.id.view_message_data);
        typeImageView = (ImageView) findViewById(R.id.view_message_type);
        Button viewAttachmentButton = (Button) findViewById(R.id.view_message_attachment);
        ImageView attachmentIconView = (ImageView) findViewById(R.id.view_message_attachment_icon);



        if (extras != null) {
            String title = extras.getString(MessagesProvider.KEY_TITLE);
            String message = extras.getString(MessagesProvider.KEY_CONTENT); 
            attachment_path = extras.getString(MessagesProvider.KEY_ATTACHMENT_PATH);
            attachment_original_filename = extras.getString(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME); 
            Boolean mine = extras.getBoolean(MessagesProvider.KEY_MINE); 
            Boolean publicMessage = extras.getBoolean(MessagesProvider.KEY_PUBLIC); 

            if (mine != null) {
                if (mine == false) {
                    if (publicMessage) {
                        typeImageView.setImageResource(R.drawable.menu_public_other);
                    } else {
                        typeImageView.setImageResource(R.drawable.menu_all);
                    }
                } else if (mine == true) {
                    if (publicMessage) {

                        typeImageView.setImageResource(R.drawable.menu_public);
                    } else {
                        typeImageView.setImageResource(R.drawable.menu_outgoing);
                    }
                }
            }

            if (title != null) {
                titleTextView.setText(title);
            }

            if (message != null) {
                messageTextView.setText(message);

            }

            if (attachment_path.equals("")) {
                viewAttachmentButton.setVisibility(View.GONE);
                attachmentIconView.setVisibility(View.GONE);
            } else {
                viewAttachmentButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        Intent intent = new Intent();


                        String filenameArray[] = attachment_original_filename.split("\\.");
                        String extension = filenameArray[filenameArray.length-1];

                        Uri uri = Uri.parse("file:///" + attachment_path);
                        MimeTypeMap mtm = MimeTypeMap.getSingleton();
                        log.debug("Extension: " + extension);
                        String mimeTypeGuess = mtm.getMimeTypeFromExtension(extension.toLowerCase());
                        log.debug("mime-type: " + mimeTypeGuess);
                        intent.setDataAndType(uri, mimeTypeGuess);
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        startActivity(intent);
        
                    }
                });
                viewAttachmentButton.setText(R.string.open_attachment_button_text + " " + attachment_original_filename);

            }

        } else {
            log.error("Unable to get any extras...this should never happen :-)");
        }

    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Window window = getWindow();
        window.setFormat(PixelFormat.RGBA_8888);
    }


}
