package org.zeitkunst.FluidNexus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

public class FluidNexusSettings extends Activity {
    private static final String TAG = "FluidNexus";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.settings);
        setTitle(R.string.settings_title);

        Button cancelButton = (Button) findViewById(R.id.settings_cancel_button);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                setResult(RESULT_OK, null, bundle);
                finish();
            }
        });

    }
}
