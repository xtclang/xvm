Build steps for an XDK:

* copy the `xdk/layout/xdk` directory to the XDK build's `xdk` directory 
* copy `xdk/tools/launcher/macos_launcher`, `xdk/tools/launcher/linux_launcher` and
  `xdk/tools/launcher/windows_launcher.exe` to the XDK build's `xdk/bin` directory 
* copy the build's `lib/xvm.jar` to the build's `xdk/prototype` directory
* (temp) copy the latest `xsrc/_native.xtc` to the build's `xdk/prototype/xvm.xtc` file
* (temp) copy the latest `xsrc/Ecstasy.xtc` to the build's `xdk/lib/Ecstasy.xtc` file
* (TODO) doc
* ZIP the resulting xdk directory; e.g. on macOS:
  `zip -r xdk.zip ./xdk -x *.DS_Store`
* (TODO) automate with Gradle