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

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MessagesDbHelper extends SQLiteOpenHelper {
    private static Logger log = Logger.getLogger("FluidNexus"); 

    /*
         message_type = Column('type', Integer, nullable = False, default = 0)
             title = Column('title', String, nullable = False)
                 content = Column('content', String, nullable = False)
                     message_hash = Column('hash', String(length = 64), nullable = False, unique = True)
                         time = Column('time', Float, default = float(0.0))
                             attachment_path = Column('attachment_path', String, default = "")
                                 attachment_original_filename = Column('attachment_original_filename',       String, default = "")
                                     mine = Column('mine', Boolean, default = 0)

                                     */
    private static final String DATABASE_CREATE =
        "create table Messages (_id integer primary key autoincrement, type integer, title text, content text, message_hash text, time float, attachment_path text, attachment_original_filename text, mine bit);";
    private static final String DATABASE_NAME = "FluidNexusDatabase.db";
    private static final String DATABASE_TABLE = "Messages";
    private static final int DATABASE_VERSION = 1;

    public MessagesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        log.info("Creating database...");
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        log.info("Upgrading database...");
    }

}

