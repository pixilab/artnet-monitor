# PIXILAB ArtNet Monitor
 A simple ArtNet DMX-512 monitor written in Java. Shows a single window that displays
 the values of all DMX channels in a universe. Operates in single-universe or "omni"
 mode (indicating the universe numbers yielding data). Binds to the default NIC, so
 you may need to ensure that the default NIC matches the one ArtNet data is received
 on if you have multiple NICs on your machine (e.g., Ethernet and Wifi).
 
## Usage
If you just want to use the monitor, download dist/ArtNet-Monitor.jar and launch it. On a Mac, assuming Java 8 or later is installed, you can generally just doubleclick ArtNet-Monitor.jar. If your OS doesn't support this method of launching the file, open a terminal window, navigate to the directory containing the file, then type:

`java -jar ArtNet-Monitor.jar` 

## Credits
 Written by Mike Fahl, http://pixilab.se (because I couldn't find a decent
 ArtNet monitor that ran on Mac/Linux, and I prefer not having to schlep
 around a Windows boat anchor just for this).

 Based on the artnet4j fork taken from https://github.com/cansik/artnet4j.

