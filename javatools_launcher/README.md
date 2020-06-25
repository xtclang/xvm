This sub-project is used to create native launchers that can be included in the XDK to execute various
commands.

The prototype runtime is implemented in Java, which makes it a challenge for developers who are not
used to Java command line execution. Our goal with the launcher was to simplify the use of the Java
tools from the command line, by making them as simple to use as normal command-line tools. This
allows us to hide the Java-specific command line options and the complications of invoking the JVM.

The solution is a native executable that can be replicated to as many different command names as
necessary (such as `xtc` for the compiler, and `xec` for the runtime). When executed, each command
executable (which is an identical copy of the original) uses the OS to find out its own name, and
passes that name to a Java utility inside of the `javatools.jar` file, along with all of the command
line parameters.

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
on that OS (in lieu of a dependable cross-compiler). The resulting executable files are stored as
artifacts in GIT; for example `macos_launcher`, `linux_launcher`, and `windows_launcher.exe`.

The build for the native module is:

    make

Details:

* The compiler used is GCC. Even on Windows and Mac.
* On macos, the build uses the command line tools that are available as part of xCode.
* On Linux, the build uses GCC.
* On Windows, TBD.

(The `../javatools` directory exists for testing the build result; it contains a fake javatools.jar
with a "main class" that echos the input.)

To override the default behavior of a launcher named `xtc`, for example, create a file in the same
directory named `xtc.cfg`, which contains up to three key/value pairs, which default to these values:

    exec = java
    opts = -Xms256m -Xmx1024m -ea
    jar  = ../javatools/javatools.jar

Note that the "build" directory is not disposable; it is actually stored in git, with the executable
artifacts. This is necessary, for the time being, because there is no simple way to create these
executable files, other than to configure build environments on each target OS.