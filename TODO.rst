TODO
====

* Add check for message with same hash in add_received to fix possible race condition

* Figure out why we can't resolve a zeroconf service created on Android

* Figure out good way to stop FN service upon request

* Add error toast on problems with oauth

* Restore listview position on return to app; for some reason this isn't working properly in the code I have now

* Add option to save current view mode and restore on resume

* Add rate limiting options: hashes to send, hashes to read, messages to send,  hash to send bias (newest, oldest, random); BUT, this requires some way to keep track of what hashes have been sent to another device!

* Add option to prevent reading of messages over a certain size.

* Add broadcast receivers that listen to wifi disabled and bluetooth disabled intents to gracefully shutdown threads if they are running
