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

public class FluidNexusDbHelper extends SQLiteOpenHelper {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final String DATABASE_CREATE =
        "create table FluidNexusData (_id integer primary key autoincrement, source varchar(32), time bigint, type integer, title varchar(40), data long varchar, hash varchar(32), cellID varchar(20), mine bit);";
    private static final String DATABASE_NAME = "FluidNexusDatabase";
    private static final String DATABASE_TABLE = "FluidNexusData";
    private static final int DATABASE_VERSION = 1;

    public FluidNexusDbHelper(Context context) {
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

