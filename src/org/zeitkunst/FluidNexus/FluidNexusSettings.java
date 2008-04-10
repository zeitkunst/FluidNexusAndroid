package org.zeitkunst.FluidNexus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.CheckBox;

public class FluidNexusSettings extends Activity {
    private static final String TAG = "FluidNexus";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.settings);
        setTitle(R.string.settings_title);
    }
}
