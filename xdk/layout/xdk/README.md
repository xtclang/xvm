## Introduction

This is the Ecstasy Development Kit (XDK).

* The Ecstasy project is on GitHub at
  [https://github.com/xtclang/xvm](https://github.com/xtclang/xvm).

* The Ecstasy blog is at
  [https://xtclang.blogspot.com/](https://xtclang.blogspot.com/).

Ecstasy is a programming language. The XDK includes the necessary tools
to build and run Ecstasy applications.

## Configuring

From the terminal or command window, execute the appropriate script for
your operating system. The shell or command file is used to generate the
various command line tools and to add the `xdk/bin` directory to the path
(but just within the current terminal or command window):

* Mac: `. ~/xdk/bin/cfg_macos.sh`

* Windows: `"c:\program files\xdk\bin\cfg_windows.cmd"`

* Linux: (coming soon)

(The above examples assume that the XDK was unzipped under the user home
directory on macOS or Linux, and unzipped under the "Program Files"
directory on Windows. If you chose a different location, adjust the
command line accordingly.)

(The shell or command file is designed to be run each time you open a
command or terminal window. To avoid this requirement, add the `xdk/bin`
directory to your permanent path.)

## Compiling

The `xtc` command (pronounced "ecstasy") is used to compile `.x` source
code files (for an entire module, or even multiple modules) into an
`.xtc` file.

After configuring, change the directory to `xdk/examples` and compile the
"Hello World!" example:

    cd ~/xdk/examples
    xtc HelloWorld.x

Or on Windows:

    c:
    cd "\program files\xdk\examples"
    xtc HelloWorld.x

Successful compilation will produce a corresponding `.xtc` file.

## Executing

The `xec` command (pronounced "exec") is used to execute a compiled
`.xtc` file.

After compiling, execute the "Hello World!" example:

    xec HelloWorld.xtc

## Disassembling

The `xam` command (pronounced "exam") is used to peek at the compiled
information in an `.xtc` file.

After compiling, disassemble the result:

    xam HelloWorld.xtc

The disassembly information is printed to the screen.

## Miscellaneous command line information

Each of the above tools supports the `-help` and `-verbose` options.

Each command line tool launches a Java prototype implementation for that
tool; the implementations are contained in the `xdk/prototype` directory.
If the `java` executable is not found in the path, or if any similar
command line issue occurs launching the tool, then a configuration file
can be created for each/any/every of the commands to specify the correct
options. The name of the configuration file is the same as the command,
but with a `.cfg` extension. For example, the **implied** configuration
file for the `xtc` command on macOS has the name `xtc.cfg` and the
implicit settings of:

    exec=java
    opts=-Xms256m -Xmx1024m -ea
    proto=../prototype/
    lib=../lib/

So, for example, if the `java` executable is not in the path, but rather
is located at `~/jdk/jre/bin`, then create a configuration file in the
`xdk/bin` directory for each command (`xtc.cfg`, `xec.cfg`, `xam.cfg`)
that contains this one line:

    exec=~/jdk/jre/bin/java

## XDK layout

* `./bin` - command line tools and the configuration scripts
* `./doc` - wiki documentation
* `./examples` - pre-packaged working example code
* `./lib` - the repository directory for all XDK-included modules
* `./log` - command line tool errors are recorded here, and can be
  optionally sanitized and submitted as error reports to the Ecstasy
  project
* `./prototype` - the Java-based prototype implementations of the
  command line tools

