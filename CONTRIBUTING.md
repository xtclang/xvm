Contributing
============

Thank you for your interest in contributing to The Ecstasy Project. By participating in this project, you agree to abide by our [code of conduct](CODE_OF_CONDUCT.md).

Pull requests are welcome. Documentation changes do not require a contributor agreement.

Code submissions (new or modified code) do require a contributor agreement. To keep things simple for legal purposes, The Ecstasy Project uses the same contributor agreements as the Apache Software Foundation, but modified to refer to The Ecstasy Project. For information about the basis and purpose of our contributor agreements, see <https://www.apache.org/licenses/contributor-agreements.html>. Please sign and submit either an [individual contributor agreement](./license/icla.txt) ([PDF](./license/icla.pdf)) or (if contributing as an employee of a company) a [corporate contributor agreement](./license/ccla.txt) ([PDF](./license/ccla.pdf)) to <info@xtclang.org>.

_(Our apologies in advance for the hassle, but we need to be very careful about potential IP issues, based on the same fundamental concerns that Apache has. Thank you for your understanding.)_

## Contribution guidelines ##

* _Cleanliness is next to godliness._ It is very difficult to clean things up, and very easy to make a mess, so we ask that contributions always move us in the direction of cleaning things up, and we will have to respectfully decline contributions that fail to meet that measure.
* Fixes should include a test, and features should include a working example. We apologize in advance for not having followed this rule in the early stages of the project.
* Until an official style guide is published, please respect the styles already in use.
* We are attempting to follow the [Git Strict Flow and GitHub Project Guidelines](https://gist.github.com/rsp/057481db4dbd999bb7077f211f53f212)

## How to get started

We recognize that the present stage of the project is going to be fairly challenging for most sane developers to contribute to. We apologize for that, and ask for your patience -- or better yet, please help us to improve this!

We have an example published for getting started with Ecstasy, using the ubiquitous "Hello World" cliché: [Hello World](https://xtclang.blogspot.com/2019/08/hello-world.html).

Alternatively, you can follow these steps: 

From within your development directory (such as `~/Development/`) where you want to create the `./xvm/` project directory, run:

> `git clone https://github.com/xtclang/xvm.git`

We strongly suggest the use of the JetBrains IntelliJ IDEA IDE, even if we have no idea how they came up with that name. There is a syntax highlighting helper for IntelliJ at `./bin/Ecstasy.xml`; on macOS, there is also a shell script `./bin/xtc2IDEA.sh` that may put it in place for you. Otherwise, manually copy it into the `~/Library/Preferences/IntelliJIdea14/filetypes/` directory (replace '14' with your version) or the `~/Library/Preferences/IdeaIC2019.1/filetypes` directory (replace year and release number with your version).

IDEA also has built-in Git support, and that is included in the community edition.

You will need JDK 8 or later installed. You can download the JDK from: <https://aws.amazon.com/corretto/> or <https://developers.redhat.com/products/openjdk/download>. (Oracle JDK and OpenJDK are both supported, but the former has licensing issues and the latter is purposefully kept out-of-date with security updates to drive business to the former.)

Create an "xvm" project in IDEA for the `./xvm/` directory. Build the project, which compiles the Java sources. After the build completes, you should be able to run the shell-based test script from the `./xsrc` directory: `../bin/runAll.sh`; this will run various "tests" (we use the term loosely here, since we have not yet incorporated a CI model).

To run an individual test in IDEA, you will find that the Ecstasy source tests are located under `./xvm/xsrc/tests`. The following instructions assume that your local repository is located at `~/Development/xvm/`, and that you are trying to compile and run `misc.x`. To compile and run the Ecstasy `misc.x` test in JetBrains IntelliJ IDEA, create a "Run/Debug Configuration" as follows:

* Main class=`org.xvm.runtime.TestConnector`
* VM options=-`-ea`
* Program arguments=`TestMisc ./tests/manual/misc.x`
* Working directory=`~/Development/xvm/xsrc/`

This pattern is repeated for the various test files. Note that the arguments specify the fully qualified module name first (as found in the module declaration in the file), followed by the file name.

_This project is still in the proof-of-concept / prototype phase. The lack of polish in the process of "getting started" is one consequence of that phase; please feel free to help us to simplify and improve the process, the documentation, the test infrastructure, the examples, and so on._

## Attributions

All content on this page is licensed under a Creative Commons Attribution license.
