## Introduction

This is the Ecstasy Development Kit (XDK).

* The Ecstasy project is on GitHub at
  [https://github.com/xtclang/xvm](https://github.com/xtclang/xvm).

* The Ecstasy blog is at
  [https://xtclang.blogspot.com/](https://xtclang.blogspot.com/).

Ecstasy is a general purpose programming language. The XDK includes the
necessary tools to build and run Ecstasy applications.

## Configuring

From the terminal or command window, execute the appropriate script for
your operating system. The shell or command file is used to generate the
various command line tools and to add the `xdk/bin` directory to the path
(but just within the current terminal or command window):

* Mac: `. ~/xdk/bin/cfg_macos.sh`

* Windows: `"c:\program files\xdk\bin\cfg_windows.cmd"`

* Linux: (_coming soon_)

(The above instructions assume that the XDK was unzipped under the user
home directory on macOS or Linux, or unzipped under the "Program Files"
directory on Windows. If you installed to a different location, adjust
the command line accordingly.)

You can use the above shell or command file each time that you open a
command or terminal window to work with Ecstasy. To avoid this step, add
the `xdk/bin` directory to your operating system's permanent path.

## Compiling

The `xcc` command (pronounced "ecstasy") is used to compile `.x` source
code files (for an entire module, or even multiple modules) into an
`.xtc` file.

After configuring, change the directory to `xdk/examples` and compile the
"Hello World!" example, located in the HelloWorld.x source file:

    cd ~/xdk/examples
    xcc HelloWorld

Or on Windows:

    c:
    cd "\program files\xdk\examples"
    xcc HelloWorld

Successful compilation will produce a corresponding `.xtc` binary file.

For more information, run: `xcc -help`

## Executing

The `xec` command (pronounced "exec") is used to execute a compiled
`.xtc` file.

After compiling, execute the "Hello World!" example:

    xec HelloWorld

For more information, run: `xec -help`

## Miscellaneous command line information

The command line tools rely on Java to be already installed and available.
The required version is Java 17 (or later).

Each of the above tools supports the `-help` and `-verbose` options.

## XDK layout

* `./bin` - command line tools and the configuration scripts
* `./doc` - wiki documentation
* `./examples` - pre-packaged working example code
* `./lib` - the repository directory for all XDK-included modules
* `./javatools` - the Java implementations of the Ecstasy tool-chain,
  including the command-line compiler and runtime implementations

