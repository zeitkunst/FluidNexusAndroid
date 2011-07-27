#!/bin/bash
jarsigner -keystore ~/.android.keystore -verbose bin/net.fluidnexus.FluidNexus-unsigned.apk android
/home/nknouf/Development/android-sdk-linux_x86/tools/zipalign -v 4 bin/net.fluidnexus.FluidNexus-unsigned.apk bin/net.fluidnexus.FluidNexus.apk
