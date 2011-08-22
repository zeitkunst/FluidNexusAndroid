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


package net.fluidnexus.FluidNexusAndroid;

import android.content.Context;
import android.database.Cursor;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.fluidnexus.FluidNexusAndroid.provider.MessagesProviderHelper;

public class MessagesListAdapter extends SimpleCursorAdapter {
    private Context context = null;
    private int layout;
    private Cursor localCursor = null;
    private LayoutInflater inflater = null;

    private static final int MESSAGE_VIEW_LENGTH = 300;

    public MessagesListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
        super(context, layout, c, from, to);
        this.context = context;
        this.layout = layout;
        this.localCursor = c;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        
        if (localCursor.moveToPosition(position)) {
            if (convertView == null) {
                convertView = inflater.inflate(layout, parent, false);
            }

            this.setMessageItemValues(convertView, localCursor);
        }
        
        return convertView;        
    }

    private void setMessageItemValues(View v, Cursor cursor) {
        int i = 0;
        TextView tv = null;
        ImageView iv = null;
        Float s_float = null;
        Long s = null;
        Time t = null;
        String formattedTime = null;

        // Set title 
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_TITLE);
        String title = cursor.getString(i);
        tv = (TextView) v.findViewById(R.id.message_list_item);
        tv.setText(title);

        // Set content
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_CONTENT);
        String fullMessage = cursor.getString(i);
        tv = (TextView) v.findViewById(R.id.message_list_data);
        int stringLen = fullMessage.length();
        if (stringLen < MESSAGE_VIEW_LENGTH) {
            tv.setText(fullMessage);
        } else {
            tv.setText(fullMessage.substring(0, MESSAGE_VIEW_LENGTH) + " ...");
        }

        // Set icons
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_MINE);
        iv = (ImageView) v.findViewById(R.id.message_list_item_icon);
        int mine = cursor.getInt(i);
        boolean publicMessage = cursor.getInt(cursor.getColumnIndex(MessagesProviderHelper.KEY_PUBLIC)) > 0;

        if (mine == 0) {
            if (publicMessage) {
                iv.setImageResource(R.drawable.menu_public_other);
            } else {
                iv.setImageResource(R.drawable.menu_all);
            }
        } else if (mine == 1) {
            if (publicMessage) {
                iv.setImageResource(R.drawable.menu_public);
            } else {
                iv.setImageResource(R.drawable.menu_outgoing);
            }
        }

        // set created time
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_TIME);
        s_float = cursor.getFloat(i);
        s = s_float.longValue() * 1000;
        t = new Time();
        t.set(s);
        tv = (TextView) v.findViewById(R.id.message_list_created_time);
        formattedTime = t.format(context.getString(R.string.message_list_created_time) + " %c");
        tv.setText(formattedTime);

        // set received time
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_RECEIVED_TIME);
        s_float = cursor.getFloat(i);
        s = s_float.longValue() * 1000;
        t = new Time();
        t.set(s);
        tv = (TextView) v.findViewById(R.id.message_list_received_time);
        formattedTime = t.format(context.getString(R.string.message_list_received_time) + " %c");
        tv.setText(formattedTime);

        // set attachment infos
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_ATTACHMENT_ORIGINAL_FILENAME);
        final String attachmentFilename = cursor.getString(i);
        final String attachmentPath = cursor.getString(cursor.getColumnIndex(MessagesProviderHelper.KEY_ATTACHMENT_PATH));
        tv = (TextView) v.findViewById(R.id.message_list_attachment);

        if (attachmentFilename.equals("")) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText("Has attachment: " + attachmentFilename);
        }

        // Set priority
        i = cursor.getColumnIndex(MessagesProviderHelper.KEY_PRIORITY);
        int priority = cursor.getInt(i);

        if (priority == MessagesProviderHelper.HIGH_PRIORITY) {
            v.setBackgroundResource(R.drawable.message_list_item_high_priority_gradient);
        } else {
            v.setBackgroundResource(R.drawable.message_list_item_gradient);
        }


    }
}
