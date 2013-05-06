# Arduino Bluetooth

This sketch is intended to work with [Blueberry](https://play.google.com/store/apps/details?id=com.bluetooth), an Android application that uses its Bluetooth capabilities to communicate with Arduino.

## Protocol

The way Blueberry talks to Android is simple: Bluetooth is completely transparent to the Arduino, it's like a serial connection, only it happens over Bluetooth. Data is passed with `Serial.println()` function and received with `Serial.readBytesUntil()`. Blueberry sends data that is interpreted in this sketch.

* Set speed with `s,left,right`, `left` and `right` being the speed of respective wheels in percent from -100 to 100.

* Reset with `r`, this stops the robot.

* Infromation: `i`, this sends back whatever it was told to send back, but essentially it's purpose is to send the robot's name and version.

* ADC converstion with `a,pin`, `pin` is the analog pin from 0 to 7. Beware of this one, data will be sent as fast as possible, it must be handleded at that speed as well.

* A function that should remain hidden is the polling function: Blueberry polls every 900 milliseconds when no other data is transmitted and the robot checks that the link is live by checking data transfer every second. Blueberry simply sends a `0` and the device sends a `0` back to confrm its existence.

* If any other data is passed the sketch responds with `Command not recognised`.

The Arduino _has_ to respond to every query, otherwise a timeout thread will inform Blueberry that the communication died.

# Blueberry

Blueberry is open-source and well documented. Anybody can make their own activity in it. If your project needs something Blueberry cannot do you can make it yourself and integrate it very easely to the Android application.

# Links

* [Blueberry on Google Play](https://play.google.com/store/apps/details?id=com.bluetooth)

* [Blueberry source on GitHub](https://github.com/Nurgak/Android-Bluetooth-Remote-Control)

* [Bluetooth module on DealExtreme](http://dx.com/p/jy-mcu-arduino-bluetooth-wireless-serial-port-module-104299)

* [Bluetooth shield for Arduino](http://arduino.cc/en/Main/ArduinoBoardBluetooth)

* [Bluetooth module to integrate in a project](http://dx.com/p/wireless-bluetooth-rs232-ttl-transceiver-module-80711)