package org.zeitkunst.FluidNexus;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.Menu.Item;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import org.bluez.Adapter;
import org.bluez.Manager;

public class FluidNexusAndroid extends ListActivity {
    private FluidNexusDbAdapter dbHelper;
    private Toast toast;
    
    private FluidNexusBtSimulator btSim;

    private SharedPreferences prefs;
    private Editor prefsEditor;

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_SETTINGS = 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;
    private static final int ACTIVITY_HELP = 5;

    private static int VIEW_MODE = 0;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;
    private static final int MENU_HELP_ID = Menu.FIRST + 5;

    private Cursor dbCursor;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        // TODO
        // The following are supposed to make the window be somewhat blurry
        // but the compositing is bad, as it makes the background be simply
        // a checkerboard
        /*
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.alpha = 0.85f;
        getWindow().setAttributes(lp); 
        */

        setContentView(R.layout.message_list);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();
        fillListView(VIEW_MODE);
        log.verbose("starting up...");
       
        btSim = new FluidNexusBtSimulator();
        btSim.startDiscovery();

        toast = Toast.makeText(this, "Starting Bluetooth simulator", Toast.LENGTH_LONG);
        toast.show();

        startService(new Intent(FluidNexusAndroid.this, FluidNexusClient.class), null);

        setupPreferences();
    }

    private void setupPreferences() {
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        boolean firstRun = prefs.getBoolean("FirstRun", true);

        if (firstRun == true) {
            prefsEditor = prefs.edit();
            prefsEditor.putBoolean("FirstRun", false);

            prefsEditor.putBoolean("ShowMessages", true);
            prefsEditor.commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Item menuItem;
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ADD_ID, R.string.menu_add_message, R.drawable.menu_add);
        menu.add(0, MENU_ALL_ID, R.string.menu_view_all, R.drawable.menu_all);
        menu.add(0, MENU_VIEW_ID, R.string.menu_view_outgoing, R.drawable.menu_view);
        menu.add(0, MENU_DELETE_ID, R.string.menu_delete, R.drawable.menu_delete);
        menu.add(0, MENU_SETTINGS_ID, R.string.menu_settings, R.drawable.menu_settings);
        menu.add(0, MENU_HELP_ID, R.string.menu_help, R.drawable.menu_help).setAlphabeticShortcut('h');
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(Item item) {
        TextView tv;

        switch (item.getId()) {
            case MENU_ADD_ID:
                addOutgoingMessage();
                return true;
            case MENU_ALL_ID:
                VIEW_MODE = 0;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_all);

                return true;
            case MENU_VIEW_ID:
                VIEW_MODE = 1;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_outgoing);

                return true;
            case MENU_DELETE_ID:
                // TODO
                // Pop up a dialog confirming deletion, and then:
                // * If OK, delete
                // * If OK, deadvertise service
                // * If Cancel, do nothing
                dbHelper.deleteById(getListView().getSelectedItemId());
                fillListView(VIEW_MODE);

                return true;
            case MENU_SETTINGS_ID:
                editSettings();
                return true;
            case MENU_HELP_ID:
                Intent i = new Intent(this, FluidNexusHelp.class);
                startSubActivity(i, ACTIVITY_HELP);
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
                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_SETTINGS):
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
        
        String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE, FluidNexusDbAdapter.KEY_DATA, FluidNexusDbAdapter.KEY_MINE};
        //String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE};
        int[] to = new int[] {R.id.message_list_item, R.id.message_list_data, R.id.message_list_item_icon};
        //int[] to = new int[] {R.id.message_list_item};
        SimpleCursorAdapter messagesAdapter = new SimpleCursorAdapter(this, R.layout.message_list_item, dbCursor, from, to);
        ListView lv;
        lv = (ListView) getListView();
        lv.setSelection(0);

        messagesAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int i) {
                if (i == cursor.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)) {
                    String fullMessage = cursor.getString(i);
                    TextView tv = (TextView) view;
                    int stringLen = fullMessage.length();
                    if (stringLen < 20) {
                        tv.setText(fullMessage + " ...");
                    } else {
                        tv.setText(fullMessage.substring(0, 20) + " ...");
                    }

                    return true;
                }

                if (i == cursor.getColumnIndex(FluidNexusDbAdapter.KEY_MINE)) {
                    ImageView iv = (ImageView) view;
                    int mine = cursor.getInt(i);

                    if (mine == 0) {
                        iv.setImageResource(R.drawable.menu_all_list_icon);
                    } else if (mine == 1) {
                        iv.setImageResource(R.drawable.menu_view_list_icon);
                    }
                    
                    return true;
                }
                return false;
            }
        });

        // TODO
        // Setting headers doesn't work because it inserts it as an element of the list, even when I tell it not too.  FOO!
        //View headerView = getViewInflate().inflate(R.layout.message_list_header,null,false,null);
        //headerView.setClickable(false);
        //headerView.setFocusable(false);
        //headerView.setFocusableInTouchMode(false);
        //lv.addHeaderView(headerView,null,false);

        setListAdapter(messagesAdapter);

    }
}
