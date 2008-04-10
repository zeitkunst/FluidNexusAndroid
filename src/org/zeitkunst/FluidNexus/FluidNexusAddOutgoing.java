package org.zeitkunst.FluidNexus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class FluidNexusAddOutgoing extends Activity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private EditText titleEditText;
    private EditText messageEditText;
    private FluidNexusDbAdapter dbHelper;  
    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        dbHelper = new FluidNexusDbAdapter(this);
        dbHelper.open();

        setContentView(R.layout.message_edit);
        setTitle(R.string.message_add_outgoing_title);
        
        titleEditText = (EditText) findViewById(R.id.title_edit);
        messageEditText = (EditText) findViewById(R.id.message_edit);

        Button saveButton = (Button) findViewById(R.id.save_message_button);
        Button discardButton = (Button) findViewById(R.id.discard_message_button);

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState();
                setResult(RESULT_OK);
                finish();
            }
        });


        discardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void saveState() {
        String title = titleEditText.getText().toString();
        String message = messageEditText.getText().toString();

        dbHelper.add_new(0, title, message, "(123,123,123,123)");
    }

}
