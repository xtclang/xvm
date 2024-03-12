JPackage configuration for release flow.

Will likely b replaced by JReleaser later.

Icons: 
    * x.ico: MS Windows icon resource - 4 icons, 64x64, 32 bits/pixel, 32x32, 32 bits/pixel
    * png files: PNG image data files with sizes as described in their file names.


How jpackage installers work:
    On Linux, the default is /opt/application-name
    – On macOS, the default is /Applications/application-name
    – On Windows, the default is c:\Program Files\application-name; if the --win-per-user-install option is used, the default is C:\Users\user- name\AppData\Local\application-name
