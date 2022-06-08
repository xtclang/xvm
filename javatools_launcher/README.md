This sub-project is used to create native launchers that can be included in the XDK to execute various
commands.

***WARNING: DO NOT DELETE THE BUILD DIRECTORY!*** _The project's `./build/` directory is not disposable; it is actually stored in `git`, with
the executable artifacts also stored in `git`, etc. More details can be found below._ 

***WARNING: THIS PROJECT DOES NOT ADHERE TO NORMAL GRADLE BUILD RULES!*** 

The prototype runtime is implemented in Java, which makes it challenging for developers who are not
used to Java command line execution. Our goal with the launcher was to simplify the use of the Java
tools from the command line, by making them as simple to use as normal command-line tools are in any
half-decent operating system. This allows us to hide the Java-specific command line options and the
complications of invoking the JVM.

The solution is a single native executable (one per hardware/OS combination, e.g. `macos_launcher`)
that can then be copied to as many different command names as necessary (e.g. `xtc` for the
compiler, and `xec` for the runtime). When executed, each command executable (which is an identical
copy of the original (or on Linux or UNIX, a symbolic link to the executable is supported as well)
uses the OS to determine its own name (e.g. "xtc" or "xec"), and passes that name to a Java
utility inside of the `javatools.jar` file, along with all of the command line parameters.

For example:

    xtc HelloWorld.x
   
Will generate an execution of the program "java" with the arguments:

    [ 0] = "java"
    [ 1] = "-Xms256m"
    [ 2] = "-Xmx1024m"
    [ 3] = "-ea"
    [ 4] = "-jar"
    [ 5] = "../javatools/javatools.jar"
    [ 6] = "xtc"
    [ 7] = "-L"
    [ 8] = "../lib/"
    [ 9] = "-L"
    [10] = "../javatools/javatools_bridge.xtc"
    [11] = "HelloWorld.x"

Which in turn results in an execution of that "main class" in the specified JAR with the arguments:

    [0] = "xtc"
    [1] = "-L"
    [2] = "../lib/"
    [3] = "-L"
    [4] = "../javatools/javatools_bridge.xtc"
    [5] = "HelloWorld.x"

Each OS works slightly differently, so the OS-specific functionality is (mostly) encapsuted into the
source files `os_macos.c`, `os_linux.c`, and `os_windows.c`. The build for each OS must be performed
on that OS (because we do not have a dependable cross-compiler readily available). The resulting
executable files are stored as artifacts in GIT; for example `macos_launcher`, `linux_launcher`, and
`windows_launcher.exe`.

The build for the native module is the GNU `make` utility, with the `makefile` located in the
`src/main/c` directory.

To execute the `makefile` on macOS, open a terminal, `cd` to the `src/main/c`
directory, and execute the following command(s): 

    make OS_NAME=macos
    codesign -s "XQIZIT INCORPORATED" ../../../build/exe/macos_launcher

(On `macos`, the result is a universal binary that includes code for both ARM and Intel Macs.) 

or on Linux (**TODO!!!**):

    make OS_NAME=linux

or on Windows:

    make OS_NAME=windows

(Note: The code signing step can only be performed if you have the necessary certificates. For
security reasons, the `xqiz.it` signature is only applied by the xqiz.it development team after
reviewing any code changes and performing a clean build from local sources.) 

Details:

* Only re-build these files _if and when_ you **need** to. If the `.c` sources have not changed, and
  if the XDK directory structure has not changed, and if the parameter requirements for the Java
  command line tools have not changed, and if a launcher already exists for your OS in the
  `build/exe` directory, and -- _of course!_ -- if things are working, then do **not** rebuild these.
* The compiler used is GCC. _Even on Windows._ (On Mac, to do the Intel and ARM cross-compile, the
  Clang compiler aka `cc` is used.)
* On macOS, the build uses the command line tools that are available as part of xCode. Alternatively,
  you could install those same tools with Homebrew, but (WARNING!) it prefixes many of the commands
  with "g" so that they do not collide with the same tools installed by xCode.
* On Linux, the build uses GCC. (_TODO: Build and check in a Linux executable._)
* On Windows, use Cygwin or MinGW to support GCC. (_TODO: Test Windows 11 Linux subsystem support._)
* When the make command executes successfully, then the resulting executable file is placed into
  `build/exe`.
* Each supported OS/hardware combination has an artifact in the `build/exe` directory, **and that
  artifact is added to `git` and stored in `git` and versioned in `git`.** This approach is _almost
  always_ the wrong thing to do (!!!), but since it's hard to cross-compile and since these files
  may be signed, we decided (for now) to store them in `git`.
* Suggestions for improving this project organization and process are welcome! 

(The `build/javatools` directory exists solely for testing the build result; it contains a **fake**
`javatools.jar` with a "main class" that echos the command line arguments. Read the README.md in
that directory for a detailed explanation.)

Lastly, one can override the default behavior of a launcher named `xtc`, for example, by creating a
corresponding `.cfg` file (e.g. `xtc.cfg`) in the same directory (e.g. `xdk/bin`), which contains
(up to) the following key/value pairs, defaulting to the following values:

    exec = java
    opts = -Xms256m -Xmx1024m -ea
    jar  = ../javatools/javatools.jar

### IntelliJ IDEA: Debugging

To create a debugging session in IntelliJ IDEA for a native launcher command, the JVM started by the
native launcher will need to be started in a debug mode, and then IDEA will need connect to that JVM,
which will allow you to debug from IDEA:
  
* Make a debug copy of (or link to) the native launcher that you need to debug. For example, to
  debug the `xec` command, copy the `xec` file  to `debug_xec`. (The prefix of "debug" or "debug_"
  is required for this purpose; it is stripped off later by the `org.xvm.tool.Launcher` class.)
  
* Create a config file for the new executable. Continuing the above example, that would be a file
  named `debug_xec.cfg`. Place the necessary JVM options into the configuration file, such as:
  
      opts=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -Xms256m -Xmx1G -ea   
  
* In IDEA, go to the "Run" menu, and select the "Edit configurations..." option. Press the `+`
  button on the dialog to create a new configuration from an existing template, and choose the
  "Remote" template. Name the configuration (e.g. `Debug Ecstasy Command`) and for the "Use
  module classpath:" option, select the `xvm.javatools.main` Gradle project.
  
* Now, from the terminal, issue the command that you wish to debug; for example:

      ~/xvm/xdk/bin/debug_xec HelloWorld.xtc 
      
* The command will block (because the JVM options specified `suspend=y`).

* In IDEA, use the "Select Run/Debug Configuration" drop-down in the tool-bar to select the name
  previously configured; for example, `Debug Ecstasy Command`. Then press the "Debug" button
  (usually `Shift-F9`). At this point, IDEA should connect to the command that you wish to debug.