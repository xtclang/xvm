# Welcome to Ecstasy! #

This is the private repository for the Ecstasy ([xtclang.org](http://xtclang.org/)) project.

All content of the project is (c) xqiz.it, all rights reserved. Contributors are required to sign and submit an Ecstasy Project Individual Contributor License Agreement (ICLA), or be a named employee on an Ecstasy Project Corporate Contributor License Agreement (CCLA), both derived directly from the Apache agreements of the same name. Once the project boot-straps the language runtime, the Ecstasy sources will be transitioned to a public repository and dual-licensed under both the Apache 2.0 license (for "open source" usage) and the GPL v3.0 license (for "free software" usage), allowing any person to select one of those two licenses under which to use or modify (etc.) the software.

Start here to browse source code: [https://bitbucket.org/xtclang/src/src/](https://bitbucket.org/xtclang/src/src/)

The Ecstasy (.x) source files for the runtime library are located under src/xsrc/system/. Ecstasy sources compile into modules, with a .xtc extension.

Java code for the prototype is located under src/src/org/xvm/.

Example code and various other "white-boards" are located under src/xsrc/examples/. Various Ecstasy source tests are located under src/xsrc/tests. To compile and run Ecstasy tests in IDEA, create an IDEA "Run/Debug Configuration" as follows:

* Main class=org.xvm.runtime.TestConnector
* VM options=-Xms256m -Xmx4g -ea -DGG=1 -DCP=1
* Program arguments=TestMisc.xqiz.it ./tests/testTemp/misc.x
* Working directory=/Users/cameron/Development/xvm/xsrc/

(The "GG" and "CP" definitions are used to enable internal debugging specialized for Gene Gleyzer and/or Cameron Purdy.)

Questions? Email [cameron@xqiz.it](mailto:cameron@xqiz.it) or [gene@xqiz.it](mailto:gene@xqiz.it)