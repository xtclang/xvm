This directory contains the command line tools:

* `xcc` (`xcc.exe` on Windows) - the **Ecstasy compiler** is used to
  compile Ecstasy source (`.x`) into a module (`.xtc`)
* `xec` (`xec.exe` on Windows) - execute an Ecstasy module (`.xtc`)

If the commands are not already present in this directory, then
execute the shell/command file appropriate to the OS:

* macOS:  `. cfg_macosh.sh`
* Linux:  `. cfg_linux.sh`
* Windows:  `cfg_windows.cmd`

This command will create the command line tool executable files
for the specified operating system.

To override the configuration of a launcher file, for example `xcc`,
create a correspondingly named `xcc.cfg` file in this directory, and
specify any of the following key/value pairs, changing the values to
meet your requirements:

    exec = java
    opts = -ea
    jar  = ../javatools/javatools.jar

So, for example, if the `java` executable is not in the path, but rather
is located at `~/jdk/jre/bin`, then create a configuration file in the
`xdk/bin` directory for each command (`xcc.cfg`, `xec.cfg`)
that contains this one line:

    exec=~/jdk/jre/bin/java

Some operating systems may disallow the executable files that you
build, download, or unzip (etc.) from running without explicit
security permissions. If you encounter this:

* On macOS, in `System Preferences`, open the `Security & Privacy`
  settings. Select the `General` tab. If necessary unlock the lock
  icon in the bottom left of the `Security & Privacy` window by
  clicking on it and entering the appropriate password. Under the
  section `Allow apps downloaded from:`, select the
  `App Store and identified developers` option. If this does not
  resolve the problem, see [How to open apps from unidentified
  developers on Mac in macOS Catalina](https://www.imore.com/how-open-apps-anywhere-macos-catalina-and-mojave).

* On Windows, an anti-virus tool may warn of or block the use of
  these executable files if they are not signed. If necessary,
  anti-virus software instructions to exempt these specific files.       
  