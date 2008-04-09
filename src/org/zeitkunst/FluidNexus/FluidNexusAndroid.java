package org.zeitkunst.FluidNexus;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.Menu.Item;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

public class FluidNexusAndroid extends ListActivity {
    private FluidNexusDbAdapter dbHelper;
    private static final String TAG = "FluidNexus";

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_SETTINGS = 3;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        //setTheme(android.R.style.Theme_Light);
        setContentView(R.layout.message_list);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();
        fillListView();
        log.verbose("starting up...");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ADD_ID, R.string.menu_add_message);
        menu.add(0, MENU_VIEW_ID, R.string.menu_view_outgoing);
        menu.add(0, MENU_SETTINGS_ID, R.string.menu_settings);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(Item item) {
        switch (item.getId()) {
            case MENU_ADD_ID:
                //this.showAlert("Fluid Nexus", "Not implemented yet", "OK", false); 
                return true;
            case MENU_VIEW_ID:
                return true;
            case MENU_SETTINGS_ID:
                editSettings();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void editSettings() {
        Intent intent = new Intent(this, FluidNexusSettings.class);
        startSubActivity(intent, ACTIVITY_SETTINGS);
    }

    public void setViewText(TextView v, String text) {
        log.debug(text);
    }

    private void fillListView() {
        Cursor c = dbHelper.all();
        startManagingCursor(c);

        TextView tv;
        tv = (TextView) findViewById(R.id.message_list_item);
        
        String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE, FluidNexusDbAdapter.KEY_DATA};
        int[] to = new int[] {R.id.message_list_item, R.id.message_list_data};
        SimpleCursorAdapter messages = new SimpleCursorAdapter(this, R.layout.message_list_item, c, from, to);
        setListAdapter(messages);
    }
}
