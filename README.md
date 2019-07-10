# Welcome to Ecstasy! #

This is the public repository for the Ecstasy ([xtclang.org](http://xtclang.org/)) project.

No logo. No fancy graphics. We're just not ready for that stuff yet. Trust us.

## What is Ecstasy?

Ecstasy is the programming language designed for serverless cloud applications and connected devices.

The Ecstasy project is composed of a programming language specification, a developer toolchain, a core runtime library, and a portable (IR-based) runtime specification.

Learn more about the language at [https://xtclang.blogspot.com/](https://xtclang.blogspot.com/2016/11/welcome-to-ecstasy-language-first.html) (Twitter @xtclang)

Learn more about the project at [https://ecstasylang.blogspot.com/](https://ecstasylang.blogspot.com/)

## Status:

- **PROOF-OF-CONCEPT ONLY!!!** (please add flashing lights, warning bells, etc.)

**Warning:** This project is not ready for production use. It will be several **years** before this project is ready for production use.

Our goal is to clearly communicate the status of this project, the target of this project, and -- quite importantly! -- who should not use this project.

Based on our internal tracking process, what we are releasing is a super-early version 0.1, and most certainly **not** a version 1.0. Most developers should not invest their time in a project until it nears an actual 1.0 release, because such a project is not ready for production use, and most developers are paid to work on things that are intended for production use.

What actually defines a 1.0 release? For the Ecstasy project, the requirements are clear:

* A language specification, a runtime specification, and user documentation for the language, the runtime library, and the toolchain.
* A native runtime implementation, with (minimally) either a JIT or an AOT native compiler. (_The current plan of record is to convert Ecstasy IR to LLVM IR, and to support both AOT and adaptive JIT._)
* A natural (Ecstasy) runtime library.
* A natural (Ecstasy) implementation of the toolchain, including the Ecstasy compiler, the Ecstasy assembler, and the Ecstasy linker.

There are many additional "nice to haves" that we hope to complete before we finish the 1.0 release, but there is a reason why the other items are called "nice to haves" instead of "requirements".

We will only "get one chance to make a good first impression", and we are determined not to waste it. We will not ask developers to waste their time attempting to use an incomplete project. At the same time, our doors are open as widely as possible to those who desire to learn, contribute to the project, and propel us towards completion.

## License

The license for all source code (defined as ./bin/, ./resource/, ./src, ./tests, and ./xsrc) is Apache 2.0, unless explicitly noted. We chose Apache 2.0 for its compatibility with almost every reasonable use, and its compatibility with almost every license, reasonable or otherwise.

The license for documentation (defined as ./doc/, and the embedded markdown API documentation and/or derivative forms thereof) is Creative Commons CC-BY-4.0, unless explicitly noted.

To help ensure clean IP (which will help us keep this project free and open source), pull requests require a signed contributor agreement to be submitted in advance. We use the Apache contributor model agreements (modified to identify this specific project), which are located under the ./license directory. Contributors are required to sign and submit an Ecstasy Project Individual Contributor License Agreement (ICLA), or be a named employee on an Ecstasy Project Corporate Contributor License Agreement (CCLA), both derived directly from the Apache agreements of the same name.

The Ecstasy name is a trademark owned and administered by The Ecstasy Project. Unlicensed use of the Ecstasy trademark is prohibited and will constitute infringement.

All content of the project not covered by the above terms is probably an accident that we need to be made aware of, and remains (c) The Ecstasy Project, all rights reserved.

## Layout

The Ecstasy (.x) source files for the runtime library are located under xvm/xsrc/system/. Ecstasy sources compile into modules, with a .xtc extension.

Java code for the prototype is located under xvm/src/org/xvm/. This code will be deprecated and removed as it is incrementally replaced by an all-Ecstasy toolchain.

A few simple Ecstasy source tests are located under xvm/xsrc/tests. The following instructions assume that your local repository is located at ~/Development, and that you are trying to compile and run "misc.x". To compile and run the Ecstasy "misc.x" test in JetBrains IntelliJ IDEA, create a "Run/Debug Configuration" as follows:

* Main class=org.xvm.runtime.TestConnector
* VM options=-Xms256m -Xmx1g -ea
* Program arguments=TestMisc.xqiz.it ./tests/testTemp/misc.x
* Working directory=~/Development/xvm/xsrc/

## Questions?

To submit a contributor agreement, sign up for very hard work, fork over a giant pile of cash, or in case of emergency: "info _at_ xtclang _dot_ org", but please understand if we cannot respond to every question. Thank you.
