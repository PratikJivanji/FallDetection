# E-health-e fall detection

A android app to detect when someone has fallen. Made as a group project for COMPX241 Software Engineering Design at the University of Waikato.

## Tech/frameworks used
- Java
- Android Studio

## Screenshots
<img src="/images/home.jpg" width="405" height="720"> <img src="/images/fallen.jpg" width="405" height="720">
<img src="/images/sending.jpg" width="405" height="720">

## How it works
To detect a fall the phone experience a short freefall period, of which the acceleration’s amplitude drops
below the lower threshold, indicated by the blue line in the diagram below. This is the period when the actual
fall occurs.
Following this, the readings show a ‘spike’ at the moment of impact, measured by the acceleration
amplitude exceeding the upper threshold. Next, there is a period of stillness, where the acceleration is
equal to that implied by gravity, demonstrated by the flat line at the end of the diagram. This indicates
that the phone is now still, and the user is in need of help.
![](/images/fallGraph.PNG)
![](/images/howAppWorks.PNG)
