***WARNING: DO NOT MOVE OR CHANGE THE CONTENTS HERE UNTIL YOU READ AND
UNDERSTAND THIS!***

The `javatools.jar` file located in this directory is **fake**. It is
**not** the `.jar` produced by the `:javatools` project.
 
This directory is for testing purposes only:

* The native launchers (e.g. one for macOS, one for Windows) are in the
  `../exe` directory.
* Each native launcher is hard-wired to assume that it is located in an
  `xdk/bin` directory, and it is hardwired to assume that there is a
  corresponding `xdk/lib` module directory, and it is hardwired to assume
  that it needs to execute the `xdk/javatools/javatools.jar` file using
  the `java` command.
* So the native launchers _assume_ that -- relative to their own location --
  the `.jar` is in the `../javatools` directory (_this_ directory).
* When someone builds or re-builds one of the native launchers, the result
  ends up in the `../exe` directory, and if they try to run it from that
  directory, it will automatically execute the `javatools.jar` file in
  _this_ directory.
* As a result, a **fake** `javatools.jar` file has been placed in this
  directory to facilitate the testing of the native launchers.        
       
For purposes of testing, the "main class" in this _fake_ `javatools.jar`
is:


    class Echo
        {
        public static void main(String[] args)
            {
            if (args == null)
                {
                System.out.println("no args");
                }
            else
                {
                int cArgs = args.length;
                System.out.println("" + cArgs + " args:");
                for (int i = 0; i < cArgs; ++i)
                    {
                    System.out.println("[" + i + "]=\"" + args[i] + '"');
                    }
                }
            }
        }

And the _fake_ `javatools.jar` manifest contains only:

    Main-Class: Echo

So, when someone runs `./macos_launcher a b c` from the `../exe` directory,
the result should look like this:

    [0]="macos_launcher"
    [1]="-L"
    [2]="/Users/guysteele/Development/xvm/javatools_launcher/build/exe/./../lib/"
    [3]="-L"
    [4]="/Users/guysteele/Development/xvm/javatools_launcher/build/exe/./../javatools/javatools_bridge.xtc"
    [5]="a"
    [6]="b"
    [7]="c"

 The result is that it is very easy to test whether the `make` of a native
 launcher succeeded.