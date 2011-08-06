#!/bin/bash
jarsigner -keystore ~/.android.keystore -verbose bin/net.fluidnexus.FluidNexusAndroid-unsigned.apk fluid_nexus
/home/nknouf/Development/android-sdk-linux_x86/tools/zipalign -v 4 bin/net.fluidnexus.FluidNexusAndroid-unsigned.apk bin/net.fluidnexus.FluidNexusAndroid.apk
cp bin/net.fluidnexus.FluidNexusAndroid.apk /home/nknouf/Documents/Research/Projects/FluidNexus/code/website/FluidNexusWebsite/FluidNexus/fluidnexus/static/downloads/
