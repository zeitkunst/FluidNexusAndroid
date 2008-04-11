package org.zeitkunst.FluidNexus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class FluidNexusHelp extends Activity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setContentView(R.layout.help);
        setTitle(R.string.help_title);
        Bundle extras = getIntent().getExtras();
        
        Button okButton = (Button) findViewById(R.id.help_ok_button);
        
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                setResult(RESULT_OK, null, bundle);
                finish();
            }
        });

    }


}
