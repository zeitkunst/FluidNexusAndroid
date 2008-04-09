/*
 * Taken from:
 * http://groups.google.com/group/android-developers/tree/browse_frm/thread/fd7cb81eb02af048/4e81db23deb7391d?rnum=1&_done=%2Fgroup%2Fandroid-developers%2Fbrowse_frm%2Fthread%2Ffd7cb81eb02af048%2F%3F#doc_4e81db23deb7391d
 */

package org.zeitkunst.FluidNexus;

import android.util.Log;

public class FluidNexusLogger {
    private String name;
    private final static String ANDROID_LOG_TAG ="FluidNexus";

    private FluidNexusLogger( String name ) {
        this.name = name;
    }

    private String makeMsg( String msg ) {
        //return this.name + " - " + msg;
        return msg;
    }

    public static FluidNexusLogger getLogger( String name ) {
        return new FluidNexusLogger( name);
    }

    public void debug( String msg ) {
        Log.d( ANDROID_LOG_TAG, makeMsg( msg ) );
    }

    public void debug( String msg, Throwable t ) {
        Log.d( ANDROID_LOG_TAG, makeMsg( msg ), t );
    } 

    public void verbose( String msg ) {
        Log.v( ANDROID_LOG_TAG, makeMsg( msg ) );
    }

    public void verbose( String msg, Throwable t ) {
        Log.v( ANDROID_LOG_TAG, makeMsg( msg ), t );
    } 

    public void info( String msg ) {
        Log.i( ANDROID_LOG_TAG, makeMsg( msg ) );
    }

    public void info( String msg, Throwable t ) {
        Log.i( ANDROID_LOG_TAG, makeMsg( msg ), t );
    } 

    public void warn( String msg ) {
        Log.w( ANDROID_LOG_TAG, makeMsg( msg ) );
    }

    public void warn( String msg, Throwable t ) {
        Log.w( ANDROID_LOG_TAG, makeMsg( msg ), t );
    } 

    public void error( String msg ) {
        Log.e( ANDROID_LOG_TAG, makeMsg( msg ) );
    }

    public void error( String msg, Throwable t ) {
        Log.e( ANDROID_LOG_TAG, makeMsg( msg ), t );
    } 

}
