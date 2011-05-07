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


package org.zeitkunst.FluidNexus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ScrollView;

public class FluidNexusHelp extends Activity {

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private TextView tv;

    @Override
    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setContentView(R.layout.help);
        setTitle(R.string.help_title);
        Bundle extras = getIntent().getExtras();
        
        Button okButton = (Button) findViewById(R.id.help_return_button);
        Button conceptButton = (Button) findViewById(R.id.help_concept_button);
        Button helpButton = (Button) findViewById(R.id.help_help_button);
        tv = (TextView) findViewById(R.id.help_text);
        ScrollView sv = (ScrollView) findViewById(R.id.help_scroll_view);

        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                /*setResult(RESULT_OK, null, bundle);*/
                setResult(RESULT_OK);
                finish();
            }
        });

        helpButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                tv.setText(R.string.help_help_text);
            }
        });

        conceptButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                tv.setText(R.string.help_concept_text);
            }
        });



    }


}
