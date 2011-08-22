Fluid Nexus for Android
=======================

by Nick Knouf

fluidnexus at fluidnexus dot net

http://fluidnexus.net

version 0.2.4.1

Fluid Nexus is an application for mobile phones that is primarily designed to enable activists or relief workers to send messages and data amongst themselves independent of a centralized cellular network.  The idea is to provide a means of communication between people when the centralized network has been shut down, either by the government during a time of unrest, or by nature due to a massive disaster.  During such times the use of the centralized network for voice or SMS is not possible.  Yet, if we can use the fact that people still must move about the world, then we can use ideas from sneaker-nets to turn people into carriers of data.  Given enough people, we can create fluid, temporary, ad-hoc networks that pass messages one person at a time, spreading out as a contagion and eventually reaching members of the group.  This enables surreptitious communication via daily activity and relies on a fluid view of reality.  Additionally, Fluid Nexus can be used as a hyperlocal message board, loosely attached to physical locations.

SECURITY NOTE
=============

This version is incomplete and lacks many security features that are currently in development.

This version stores messages in an unencrypted database on the phone.  We plan to move this to SQLCipher in the near future.  Additionally, attachments (files sent with a message) are stored unencrypted on your SD card.  We are looking into possibilities for encryption of these files, but until then, you have been warned.

Data are sent over bluetooth without any transport-layer security.  We are looking into implementing SSL/TLS for each of the network modalities.  You have been warned.

BLUETOOTH NOTE
--------------

On Android, making bluetooth connections to visible (discoverable), but unpaired, devices requires pairing beforehand.  This is unfortunate, but can be used to your advantage to create a "whitelist" of paired devices before using the system in the field.  It might be good to agree upon a pairing code ahead of time (one different from "1234", "0000", or the like) to enable pairing in the field.

Once paired, the device does not have to be visible (discoverable) for the Android phone to connect to it.

LICENSING
=========

Fluid Nexus for Android is currently licensed under the GPLv3.

The google protobuf library is licensed under the BSD 2-Clause license.

The jmdns library is licensed under Apache V2.0 or LGPL and is based on the modified version by twitwi (https://github.com/twitwi/AndroidDnssdDemo).

SQLCipher licensed under a BSD-style license from Zetetic LLC (https://github.com/guardianproject/android-database-sqlcipher/blob/master/SQLCIPHER_LICENSE).  SQLite is in the public domain (http://www.sqlite.org/copyright.html).

TODO
====

* Implement wake locks so that the service can run even when the device is sleeping; create it so that we run an alarm, run the code, then "sleep" until the next alarm (thus getting rid of the thread sleep code).  Does this only happen when we're on battery power?
* Fix cursor leak issue (even though I'm positive we close all cursors when we need to...)
* Figure out good way to stop the threads safely
* Implement SQLCipher database encryption
* Implement SSL/TLS over network modalities
* Implement wifi ad-hoc networking
