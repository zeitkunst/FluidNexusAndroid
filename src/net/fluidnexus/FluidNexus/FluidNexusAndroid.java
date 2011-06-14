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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;


public class FluidNexusAndroid extends ListActivity {
    private FluidNexusDbAdapter dbAdapter;
    private Toast toast;
    
    private SharedPreferences prefs;
    private Editor prefsEditor;

    // just for testing
    private BroadcastReceiver iReceiver;
    private IntentFilter iFilter;

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_SETTINGS = 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;
    private static final int ACTIVITY_HELP = 5;
    private static final int REQUEST_ENABLE_BT = 6;

    private static int VIEW_MODE = 0;

    private long currentRowID = -1;

    private static final int DIALOG_REALLY_DELETE = 0;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;
    private static final int MENU_HELP_ID = Menu.FIRST + 5;

    // messages from bluetooth service
    public static final int MESSAGE_BT_STATE_CHANGED = 1;

    private boolean showMessages = true;

    private Cursor dbCursor;

    private BluetoothAdapter bluetoothAdapter = null;

    private class NewMessageIntentReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log.info(action);
            log.info("received new message intent.");
            fillListView(VIEW_MODE);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        log.verbose("unfreezing...");
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
        registerForContextMenu(getListView());
        dbAdapter = new FluidNexusDbAdapter(this);
        dbAdapter.open();
        fillListView(VIEW_MODE);
        log.verbose("starting up...");
      
        // Regiser my receiver to NEW_MESSAGE action
        iFilter = new IntentFilter(getText(R.string.intent_new_message).toString());
        iReceiver = new NewMessageIntentReceiver();
        registerReceiver(iReceiver, iFilter);

        // setup bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // if it's not available, let user know
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available; sending and receiving messages will not be possible", Toast.LENGTH_LONG).show();
        }

        setupPreferences();

        /*        
        log.info("Starting bluetooth service");        
        startService(new Intent(FluidNexusBluetoothService.class.getName()));
        */
    }

    /**
     * Method of creating dialogs for this activity
     * @param id ID of the dialog to create
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_REALLY_DELETE:
                dialog = reallyDeleteDialog();
                break;
            default:
                dialog = null;
        }

        return dialog;
    }
    
    /**
     * Method to create our really delete dialog
     */
    private AlertDialog reallyDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to delete this message?")
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    log.debug("Row ID is: " + currentRowID);
                    dbAdapter.deleteById(currentRowID);
                    currentRowID = -1;
                    fillListView(VIEW_MODE);
                    toast = Toast.makeText(getApplicationContext(), "Message deleted.", Toast.LENGTH_LONG);
                    toast.show();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }

    @Override 
    public void onStart() {
        super.onStart();
        log.info("In onStart");
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                toast = Toast.makeText(this, "Restart application to use bluetooth.", Toast.LENGTH_LONG);
                toast.show();
            } else {
                /*
                if (bluetoothService == null) {
                    setupFluidNexusBluetoothService();
                }
                */
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(iReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(iReceiver, iFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(iReceiver);
    }

    /*
     * Context menu code from:
     * http://stackoverflow.com/questions/6205808/how-to-handle-long-tap-on-listview-item
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        currentRowID = info.id;
        menu.setHeaderTitle(getString(R.string.menu_message_list_context_title));
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_list_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.delete_message:
                showDialog(DIALOG_REALLY_DELETE);
                return true;
            case R.id.edit_message:
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void setupPreferences() {
        prefs = getSharedPreferences("FluidNexusPreferences", 0);
        boolean firstRun = prefs.getBoolean("FirstRun", true);

        if (firstRun == true) {
            prefsEditor = prefs.edit();
            prefsEditor.putBoolean("FirstRun", false);

            prefsEditor.putBoolean("ShowMessages", true);
            prefsEditor.putBoolean("SimulateBluetooth", true);
            prefsEditor.commit();
            dbAdapter.initialPopulate();
        }
        
        this.showMessages = prefs.getBoolean("ShowMessages", true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ADD_ID, menu.NONE, R.string.menu_add_message).setIcon(R.drawable.menu_add).setAlphabeticShortcut('a');
        menu.add(0, MENU_ALL_ID, menu.NONE, R.string.menu_view_all).setIcon(R.drawable.menu_all).setAlphabeticShortcut('v');
        menu.add(0, MENU_VIEW_ID, menu.NONE, R.string.menu_view_outgoing).setIcon(R.drawable.menu_view).setAlphabeticShortcut('o');
        menu.add(0, MENU_DELETE_ID, menu.NONE, R.string.menu_delete).setIcon(R.drawable.menu_delete).setAlphabeticShortcut('x');
        menu.add(0, MENU_SETTINGS_ID, menu.NONE, R.string.menu_settings).setIcon(R.drawable.menu_settings).setAlphabeticShortcut('s');
        menu.add(0, MENU_HELP_ID, menu.NONE, R.string.menu_help).setIcon(R.drawable.menu_help).setAlphabeticShortcut('h');
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TextView tv;

        switch (item.getItemId()) {
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
                dbAdapter.deleteById(getListView().getSelectedItemId());
                fillListView(VIEW_MODE);

                return true;
            case MENU_SETTINGS_ID:
                editSettings();
                return true;
            case MENU_HELP_ID:
                Intent i = new Intent(this, FluidNexusHelp.class);
                /*startSubActivity(i, ACTIVITY_HELP);*/
                startActivityForResult(i, ACTIVITY_HELP);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // We will need to be careful later here about the different uses of position and rowID
        super.onListItemClick(l, v, position, id);
        log.info("At position: " + position);
        Cursor localCursor = dbCursor;
        localCursor.moveToPosition(position);

        Intent i = new Intent(this, FluidNexusViewMessage.class);
        i.putExtra(FluidNexusDbAdapter.KEY_ID, localCursor.getInt(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_ID)));
        i.putExtra(FluidNexusDbAdapter.KEY_TITLE, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_TITLE)));
        i.putExtra(FluidNexusDbAdapter.KEY_DATA, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)));
        startActivityForResult(i, ACTIVITY_VIEW_MESSAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case(ACTIVITY_VIEW_MESSAGE):
                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_ADD_OUTGOING):
                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_SETTINGS):
                /*
                 * TODO
                 * figure out why I don't get extras anymore...
                if (!extras.isEmpty()) {
                    boolean bluetoothChanged = extras.getBoolean("bluetoothChanged", false);
                    if (bluetoothChanged) {
                        toast = Toast.makeText(this, R.string.toast_bluetooth_settings_changed, Toast.LENGTH_LONG);
                        toast.show();

                    }
                }
                */
                break;
            case(REQUEST_ENABLE_BT):
                if (resultCode == ListActivity.RESULT_OK) {
                    // setup services here
                } else {
                    log.warn("Bluetooth not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled, leaving", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void editSettings() {
        Intent intent = new Intent(this, FluidNexusSettings.class);
        startActivityForResult(intent, ACTIVITY_SETTINGS);
    }

    private void addOutgoingMessage() {
        Intent intent = new Intent(this, FluidNexusAddOutgoing.class);
        startActivityForResult(intent, ACTIVITY_ADD_OUTGOING);
    }


    public void setViewText(TextView v, String text) {
        log.debug(text);
    }

    private void fillListView(int viewType) {
        if (!this.showMessages) {
            return;
        }

        if (viewType == 0) {
            dbCursor = dbAdapter.all();
        } else if (viewType == 1) {
            dbCursor = dbAdapter.outgoing();
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
