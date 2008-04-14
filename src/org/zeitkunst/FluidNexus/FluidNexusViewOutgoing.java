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

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.Menu.Item;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Log;

public class FluidNexusViewOutgoing extends ListActivity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private FluidNexusDbAdapter dbHelper;  
    private Cursor dbCursor;
    
    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();

        setContentView(R.layout.message_list);
        setTitle(R.string.message_view_outgoing_title);
        fillListView();
        
    }

    private void fillListView() {
        // TODO
        // Get the CursorAdapter to work like it does on the Series 60, where we only show an excerpt of the message
        dbCursor = dbHelper.outgoing();
        startManagingCursor(dbCursor);

        TextView tv;
        tv = (TextView) findViewById(R.id.message_list_item);
        
        //String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE, FluidNexusDbAdapter.KEY_DATA};
        String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE};
        //int[] to = new int[] {R.id.message_list_item, R.id.message_list_data};
        int[] to = new int[] {R.id.message_list_item};
        SimpleCursorAdapter messages = new SimpleCursorAdapter(this, R.layout.message_list_item, dbCursor, from, to);
        setListAdapter(messages);
    }

}
