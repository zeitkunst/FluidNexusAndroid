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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ScrollView;

public class Help extends Activity {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private TextView tv;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setContentView(R.layout.help);
        setTitle(R.string.help_title);
        Bundle extras = getIntent().getExtras();

        // From http://stackoverflow.com/questions/4371058/help-activity-in-android-app        
        WebView browser = (WebView) findViewById(R.id.help_webview);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        browser.loadUrl("file:///android_asset/index.html");

    }
}
