This sub-project is used to create native launchers that can be included in the XDK to execute various
commands.

The prototype is implemented in Java, which is relatively challenging when it comes to making it work
like normal command-line tools.

The solution is to create a native executable that can be copied to as many different names as necessary
(such as "xtc" for the compiler, and "xec" for the runtime), and when run, each executable (which is an
identical copy of the original) uses the OS to find out its own name, and passes that name to a Java
utility in the prototype xvm.jar, along with all of the command line parameters.

For example:

   xtc HelloWorld.x
   
Will generate an execution of the program "java" with the arguments:

    [0] = "java"
    [1] = "-Xms256m"
    [2] = "-Xmx1024m"
    [3] = "-ea"
    [4] = "-jar"
    [5] = "../prototype/xvm.jar"
    [6] = "xtc"
    [7] = "HelloWorld.x"

Which in turn results in an execution of that "main class" in the specified JAR with the arguments:

    [0] = "xtc"
    [1] = "HelloWorld.x"

Each OS works slightly differently, so the OS-specific functionality is (mostly) encapsuted into the
source files `os_macos.c`, `os_linux.c`, and `os_windows.c`. The build for each OS must be performed
on that OS (in lieu of a dependable cross-compiler). The resulting executable files are stored as
artifacts in GIT; for example `macos_launcher`, `linux_launcher`, and `windows_launcher.exe`.

The build for the native module is:

    make

Details:

* On macos, the build uses the command line tools that are available as part of xCode.
* On Linux, the build uses GCC.
* On Windows, TBD.

(The `../prototype` directory exists for testing the build result; it contains a fake xvm.jar with
a "main class" that echos the input.)
