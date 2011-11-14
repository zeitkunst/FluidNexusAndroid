Fluid Nexus for Android
=======================

by Nick Knouf

fluidnexus at fluidnexus dot net

http://fluidnexus.net

version 0.3.0

In the second decade of the twenty-first century, networks continue to be defined by their stable topology represented in an image or graph. Peer-to-peer technologies promised new arrangements absent centralized control, but they still rely on stationary devices. Mobile phones remain wedded to conventional network providers.

Instead, the combination of peer-to-peer with mobility enables a new concept of an information transfer infrastructure that relies on fluid, temporary, ad-hoc networks. People and devices are at once implicated as mobile nodes in this network (known in computer science as a sneakernet).

Fluid Nexus is a demonstration of how one might design software to bypass Internet intermediaries' control over the identification and circulation of messages. It is a piece of interrogative software art, of a piece with other attempts to rework network topology such as the Eternal Network used by mail artists or projects such as Dead Swap or netless. We draw partial inspiration from the potential activist re-purposing of digital technologies without being subsumed by the same goals.

While Fluid Nexus is designed for non-Internet-based communications, we have also developed the Nexus, a space on this site for "public" messages to be automatically uploaded by any Fluid Nexus user. The Nexus includes text, audio, images, and video capabilities, and the original sender has control whether the message will become public or not. The Nexus extends the reach of the Fluid Nexus non-network beyond those using the software on their phone or laptop/desktop.

SECURITY NOTE
=============

Messages sent or received are stored in a local, encrypted SQLCipher database.  Received attachments are currently stored unencrypted; of course any attachments you decide to include are stored unencrypted as well (unless you have installed some other form of filesystem encryption for Anddroid).

Data are sent over Bluetooth using the standard encryption facilities of the Bluetooth stack; other modalities are sent unencrypted.

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
* Implement wifi ad-hoc networking
