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
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class WelcomeActivity extends Activity {
    // The pattern below was shamelessly cribbed from Gibberbot's AboutActivity.java
    
    // Titles for each page
    private int titles[] = {
        R.string.welcome_welcome_title,
        R.string.welcome_concept_title,
        R.string.welcome_security_title,
        R.string.welcome_passphrase_title
    };

    // Messages shown on each page
    private int messages[] = {
        R.string.welcome_welcome_message,
        R.string.about_text,
        R.string.disclaimer_dialog,
        R.string.welcome_passphrase_message
    };

    // Buttons shown on each page
    private Integer buttons[][] = {
        {null, R.string.button_next},
        {R.string.button_back, R.string.button_next},
        {R.string.button_back, R.string.button_next},
        {R.string.button_back, R.string.button_choose_passphrase},
    };

    // The current page index
    private int currentPage = -1;

    // Our button onClick listeners
    private View.OnClickListener listeners[][] = {
        {
            null,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nextPage();
                }
            }
        },

        {
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prevPage();
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nextPage();
                }
            }
        },

        {
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prevPage();
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nextPage();
                }
            }
        },


        {
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prevPage();
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                    // TODO
                    // Pull in code to pop up passphrase dialog
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (currentPage == -1) {
            setContentView(R.layout.welcome);
            nextPage();
        }
    }

    private void prevPage() {
        currentPage--;
        showPage(currentPage);
    }

    private void nextPage() {
        currentPage++;
        showPage(currentPage);
    }

    private void showPage(int currentPage) {
        ScrollView sv = (ScrollView) findViewById(R.id.welcome_scrollview);

        TextView title = (TextView) findViewById(R.id.welcome_title);
        title.setText(getString(titles[currentPage]));

        TextView message = (TextView) findViewById(R.id.welcome_text);
        message.setText(Html.fromHtml(getString(messages[currentPage])));
        
        // Reset scroll
        sv.scrollTo(0, 0);

        Button button1 = (Button) findViewById(R.id.welcome_button1);
        if (buttons[currentPage][0] != null) {
            button1.setText(getString(buttons[currentPage][0]));
            button1.setOnClickListener(listeners[currentPage][0]);
            button1.setVisibility(Button.VISIBLE);
        } else {
            button1.setVisibility(Button.INVISIBLE);
        }

        Button button2 = (Button) findViewById(R.id.welcome_button2);
        if (buttons[currentPage][1] != null) {
            button2.setText(getString(buttons[currentPage][1]));
            button2.setOnClickListener(listeners[currentPage][1]);
            button2.setVisibility(Button.VISIBLE);
        } else {
            button2.setVisibility(Button.INVISIBLE);
        }
    }
}
