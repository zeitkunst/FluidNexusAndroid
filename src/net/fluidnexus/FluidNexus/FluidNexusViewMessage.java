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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class FluidNexusViewMessage extends Activity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private TextView titleTextView;
    private TextView messageTextView;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setTheme(android.R.style.Theme_Dialog);
        setContentView(R.layout.message_view);
        setTitle(R.string.message_view_title);
        Bundle extras = getIntent().getExtras();
        
        titleTextView = (TextView) findViewById(R.id.view_message_title);
        messageTextView = (TextView) findViewById(R.id.view_message_data);
        Button backButton = (Button) findViewById(R.id.view_message_back);

        if (extras != null) {
            String title = extras.getString(FluidNexusDbAdapter.KEY_TITLE);
            String message = extras.getString(FluidNexusDbAdapter.KEY_DATA); 
            
            if (title != null) {
                titleTextView.setText(title);
            }

            if (message != null) {
                messageTextView.setText(message);

            }

        } else {
            log.error("Unable to get any extras...this should never happen :-)");
        }
        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                /*setResult(RESULT_OK, null, bundle);*/
                setResult(RESULT_OK);
                finish();
            }
        });

    }


}
