![Blueberry](https://raw.github.com/Nurgak/Android-Bluetooth-Remote-Control/master/images/blueberry.png "Blueberry")

# Blueberry: Bluetooth Remote Control Application for Android

Blueberry is an [Android application](https://play.google.com/store/apps/details?id=com.bluetooth) that uses the phone's Bluetooth feature to connect to a Bluetooth enabled device. It is mainly intended to control mobile platforms such as an Arduino with a Bluetooth shield mounted on a platform with two motorized wheels. Additionally it uses the phone's camera, internet connection and sensors to interact with the mobile platform.

[![Video](https://raw.github.com/Nurgak/Android-Bluetooth-Remote-Control/master/images/video.png "Blueberry demonstration video")](http://youtu.be/ukssaDaPI5s)

## 1. Application

The application has 3 main parts: device select, activity selection and the activity itself. All Bluetooth communications happen at the application level, so that any activity in the application can send data to the Bluetooth device at any time.

### 1.1. Device Select

The device select activity searches for Bluetooth enabled devices and lets the user pair and connect to them, once a device has been paired it will show up in a _Paired devices_ list where the user can select it without having to wait for Bluetooth to find it again.

### 1.2. Activity Selection

Different activities are available once the connection has been established. They are in a list with a title and a small description of what they do. When an activity is quit by user this screen is shown again.

![Activity selection](https://raw.github.com/Nurgak/Android-Bluetooth-Remote-Control/master/images/screenshot_activities.png "Activity selection")

### 1.3. Activities

These are the different ways to interact with the Bluetooth device. As stated before this application's main purpose is to interact with Bluetooth enabled mobile platforms (having two wheels) such as an Arduino with a Bluetooth shield so there are two speeds, one for each wheel.

![Accelerometer Control](https://raw.github.com/Nurgak/Android-Bluetooth-Remote-Control/master/images/screenshot_accelerometer.png "Accelerometer Control")
![Send Data](https://raw.github.com/Nurgak/Android-Bluetooth-Remote-Control/master/images/screenshot_senddata.png "Send Data")

Currently there are 7 available activities:

* Accelerometer Control: Control your robot by tilting the phone

* Touch Control: Control robot's movements by touch

* Voice Control: Control robot with oral instructions

* Wi-Fi Control: Use a computer to remotely control from a browser

* Line Follower: Use the phone's camera to follow a black line

* Kill All Humans: Use face detection to run down humans

* Send Data: Send custom commands to robot

Additionnaly there are some activities not implemented yet, but here are the ideas. These are included in the code, but commented out so they do not show up the in activity selection:

* Arrow Control: Simplistic control with arrows

* Program: Save and replay a set of instructions

* Keypad: Send numbers from 1 to 9 for custom actions

* Transmit Data: Transmit data over Wi-Fi via a GET requests

* Sound: Make sounds by toggling the motor direction

* GPS Position: Send the robot anywhere on earth

* Motion Detection: Use the camera to sense movement

* Oscilloscope: Use the ADC as an oscilloscope

* Function Generator: Use the PWM as a square function generator

Finally an interesting activity would be an [API](http://en.wikipedia.org/wiki/Application_programming_interface "Application programming interface"): rather than program the Android it would be interesting to let the device send data to the Android and let it do certain tasks such as call a number, take a picture and send it via email, mms or upload it somewhere, send an e-mail... again these ideas are in the activity selection class code and commented out.

### 1.4. Protocol

The Bluetooth device receives instructions to be parsed and interpreted, still taking for the example a two-wheeled robot the instruction to go forwards is `s,50,50`, with `50,50` being the speed of the left and right wheels in percent from -100 to 100, 0 being the stopped position. Unfortunately this is only adapted to two-wheeled robots, but by editing the Android appication one can adapt it to behave differently such as send the turning angle and speed for a four-wheeled mobile platform.

See the [code I used in my robot](https://github.com/Nurgak/Android-Bluetooth-Remote-Control/tree/master/Arduino-Bluetooth) to understand how communication is handeled on the Bluetooth device end.

#### 1.4.1. Instruction Set

Set wheel speed, `left` and `right` are speeds values in percent from -100 to 100 (ex: `s,-100,100` will make the robot turn on itself):

    s,left,right

Reset robot. Use it in critical cases when the robot has to be stopped no matter what, such as lost communication, it will bypass the busy flag:

    r

Of course the instuction set can be expanded by programming the Bluetooth device to interpret them and the Android application to send them. The _send data_ activity was specifically made with this purpose: you may seny any data to your device which you priorly programmed to interpret it.

For example if you whish to read a light sensor connected to your Arduino you would make it send that information back, via serial, when it receives the `k` instruction (or any other character that isn't used for something else). It might help to view an [example code for Arduino](https://github.com/Nurgak/Android-Bluetooth-Remote-Control/tree/master/Arduino-Bluetooth).

### 1.5. Connection state

To ensure the Bluetooth device is in range data has to flow continously: if the user isn't sending out instructions the application will poll the device every 900 milliseconds. The robot has to reply to _each_ instruction, or the next one cannot be sent by the Android application (with the exception of the reset instuction `r`). If the robot does not reply within 3 secods a timeout thread will stop the activity and the user will be sent back to the Bluetooth device selection activity.

On the robot end this is implemented with an interrupt: it's called roughly every second and if instructions were exchanged within that time nothing happens, if not the robot resets itself automatically (sets speed to 0). Notice the 100 millisecond difference between application polling and device check.

## 2. Adding Your Own Activity
If you want to add an activity to this application you can download the source and compile it yourself. The main thing to remember is that new activities go under _com.bluetooth.activities_ subfolder and they have to extend the _BluetoothActivity_ class which is the wrapper for all activities.

To add your activity in the activity list you have to edit the _ActionListActivity_ class. Remember to update the manifest file too.

If you think your activity can benefit other users of this application your can ask to merge your code with the master branch, then it would be updated in the Google Play store as well and would officially be part of the application.

### 2.1. Guidelines

* Use the default Anrdoid styling and colors, look at the existing activities for formatting.

* When you have questions search on Google, do not contact me.

* The most basic steps to add new activity:
  1. Install Eclipse and Android SDK.
  2. Download the application files to your workspace and import the project to Eclipse.
  3. Create a new class in _com.bluetooth.activities_ or copy an already existing activity from the same folder.
  4. Edit the _ActionListActivity_ class and add your own activity to the list.
  5. Edit the manifest and add your activity.
  6. Make your layout file or copy an existing activity layout.

* A new activity class must extend the _BluetoothActivity_, the base functionalities are in there, it shouldn't be modified as other activities use it as well.

* All activities must keep the phone screen on, or the communication will be terminated as the phone enters stand-by mode.

Also something to be noted: if the phone exits the application (receives a call, open phone's settings menu...) it will go back at the device select screen.
