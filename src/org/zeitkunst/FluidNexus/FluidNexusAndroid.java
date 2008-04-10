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
import android.widget.Toast;
import android.util.Log;

public class FluidNexusAndroid extends ListActivity {
    private FluidNexusDbAdapter dbHelper;

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_SETTINGS = 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;

    private Cursor dbCursor;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        //setTheme(android.R.style.Theme_Light);
        setContentView(R.layout.message_list);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();
        fillListView(0);
        log.verbose("starting up...");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Item menuItem;
        boolean result = super.onCreateOptionsMenu(menu);
        menuItem = menu.add(0, MENU_ADD_ID, R.string.menu_add_message);
        menuItem.setIcon(R.drawable.menu_add);
        menuItem = menu.add(0, MENU_ALL_ID, R.string.menu_view_all);
        menuItem = menu.add(0, MENU_VIEW_ID, R.string.menu_view_outgoing);
        menuItem.setIcon(R.drawable.menu_view);
        menuItem = menu.add(0, MENU_DELETE_ID, R.string.menu_delete);
        menu.add(0, MENU_SETTINGS_ID, R.string.menu_settings);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(Item item) {
        switch (item.getId()) {
            case MENU_ADD_ID:
                addOutgoingMessage();
                return true;
            case MENU_ALL_ID:
                fillListView(0);
                return true;
            case MENU_VIEW_ID:
                fillListView(1);
                return true;
            case MENU_DELETE_ID:
                Toast.makeText(this, "Not implmented yet...", Toast.LENGTH_LONG);
                return true;
            case MENU_SETTINGS_ID:
                editSettings();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // We will need to be careful later here about the different uses of position and rowID
        super.onListItemClick(l, v, position, id);
        Cursor localCursor = dbCursor;
        localCursor.moveTo(position);

        Intent i = new Intent(this, FluidNexusViewMessage.class);
        i.putExtra(FluidNexusDbAdapter.KEY_ID, id);
        i.putExtra(FluidNexusDbAdapter.KEY_TITLE, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_TITLE)));
        i.putExtra(FluidNexusDbAdapter.KEY_DATA, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)));
        startSubActivity(i, ACTIVITY_VIEW_MESSAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, String     data, Bundle extras) {
        super.onActivityResult(requestCode, resultCode, data, extras);

        switch(requestCode) {
            case(ACTIVITY_VIEW_MESSAGE):
                break;
            case(ACTIVITY_ADD_OUTGOING):
                fillListView(0);
                break;
        }
    }

    private void editSettings() {
        Intent intent = new Intent(this, FluidNexusSettings.class);
        startSubActivity(intent, ACTIVITY_SETTINGS);
    }

    private void viewOutgoingMessages() {
        Intent intent = new Intent(this, FluidNexusViewOutgoing.class);
        startSubActivity(intent, ACTIVITY_VIEW_OUTGOING);
    }


    private void addOutgoingMessage() {
        Intent intent = new Intent(this, FluidNexusAddOutgoing.class);
        startSubActivity(intent, ACTIVITY_ADD_OUTGOING);
    }


    public void setViewText(TextView v, String text) {
        log.debug(text);
    }

    private void fillListView(int viewType) {
        // TODO
        // Get the CursorAdapter to work like it does on the Series 60, where we only show an excerpt of the message

        if (viewType == 0) {
            dbCursor = dbHelper.all();
        } else if (viewType == 1) {
            dbCursor = dbHelper.outgoing();
        }
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
