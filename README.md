# PiSocketEmulator
Raspberry Pi Emulation of Orvibo S20 Socket


Getting started
===============

To run this software, SSH into your Pi, go to the location of the jar file and run:

java -jar socketEmu.jar


This will start the application and generate a default properties file if there isn't one already in the same directory.


Properties
==========

networkInterface  = wlan0

deviceName        = PiSocket

passwoprd         = 888888

discoverable      = 0

timezone          = 0

state             = 0

gateway           = 192.168.1.254

dst               = 1

useServer         = false

outputPin1        = GPIO 9

outputPin2        = GPIO 8




Feedback
========

Please remember that this is beta software. It has been built and tested against the Orvibo S20 smart socket, with the IOS application. The AllOne mode features are limited to a number of on/off switches only.

To-Do:
======

* Implement Tables 1 & 3
* AllOne mode switch response
* Acknowledge password change messages
* Fix initial state issues
* Implement use of password
* Icon change?


Supporting development
======================

