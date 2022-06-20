# Welcome to Ecstasy! #

This is the public repository for the Ecstasy language ([xtclang.org](http://xtclang.org/)) and the
Ecstasy virtual machine (XVM) project.

## What is Ecstasy?

<table cellspacing="0" cellpadding="0" style="border-collapse: collapse; border: none;">
<tr style="border: none;"><td style="border: none;">

![Ecstasy](./doc/logo/x.jpg "The Ecstasy Project")

</td><td style="border: none;">

Ecstasy is a new, general-purpose, programming language, designed for modern cloud architectures,
and explicitly for the secure, serverless cloud. Actually, to be completely honest, it's the most
amazing programming language ever. No, really, it's that awesome.

</td></tr></table>

The Ecstasy project includes a development kit (XDK) that is produced out of this repository, a
programming language specification, a core set of runtime modules (libraries), a portable,
type-safe, and verifiable Intermediate Representation (IR), a proof-of-concept runtime (with an
adaptive LLVM-based optimizing compiler in development), and a tool-chain with both Java and Ecstasy
implementations being actively developed.

The Ecstasy language supports first class modules, including versioning and conditionality; first
class functions, including currying and partial application; type-safe object orientation,
including support for auto-narrowing types, type-safe covariance, mixins, and duck-typed interfaces;
complete type inference; first class immutable types; first class asynchronous services, including
both automatic `async/await`-style and promises-based (`@Future`) programming models; and first 
class software containers, including resource injection and transitively-closed, immutable type
systems. _And much, much more._
   
Read more at [https://xtclang.blogspot.com/](https://xtclang.blogspot.com/2016/11/welcome-to-ecstasy-language-first.html)

Follow us on Twitter [@xtclang](https://twitter.com/xtclang)

Find out more about [how you can contribute to Ecstasy](CONTRIBUTING.md).

And please respect our [code of conduct](CODE_OF_CONDUCT.md) and each other.

## Installation
                            
For **macOS** and **Linux**:

1. If you do not already have the `brew` command available, install [Homebrew](https://brew.sh/)
 
2. Add a "tap" to access the XDK CI builds, and install the latest XDK CI build: 
```
  brew tap xtclang/xvm && brew install xdk-latest
```

3. To upgrade to the latest XDK CI build at any time:  
```
  brew update && brew upgrade xdk-latest
```

For **Windows**:
      
* Visit [http://xtclang.org/xdk-latest.html](http://xtclang.org/xdk-latest.html) to download a
  Windows installer for the latest XDK build  

Manual local build for **any computer** (for advanced users):
      
* Install Java (version 17 or later) and Gradle

* Use `git` to obtain the XDK:
```
  git clone https://github.com/xtclang/xvm.git
```
      
* `cd` into the git repo (the directory will contain [these files](https://github.com/xtclang/xvm/))
  and execute the Gradle build:
```
  gradle build
```

## Status:

Version 0.4. That's way _before_ a 1.0. In other words, Ecstasy is about as mature as Windows 3.1
was.

**Warning:** The Ecstasy project is not yet certified for production use. This is a large and
extremely ambitious project, and _it may yet be several years before this project is certified for
production use_.

Our goal is to always honestly communicate the status of this project, and to respect those who
contribute and use the project by facilitating a healthy, active community, and a useful,
high-quality project. Whether you are looking to learn about language design and development,
compiler technology, or the applicability of language design to the serverless cloud, we have a
place for you here. Feel free to lurk. Feel free to fork the project. Feel free to contribute.
 
We only "_get one chance to make a good first impression_", and we are determined not to waste it.
We will not ask developers to waste their time attempting to use an incomplete project, so if you
are here for a work reason, it's probably still a bit too early for you to be using this for your
day job. On the other hand, if you are here to learn and/or contribute, then you are right on time!
Our doors are open.

## License

The license for source code is Apache 2.0, unless explicitly noted. We chose Apache 2.0 for its
compatibility with almost every reasonable use, and its compatibility with almost every license,
reasonable or otherwise.

The license for documentation (including any the embedded markdown API documentation and/or
derivative forms thereof) is Creative Commons CC-BY-4.0, unless explicitly noted.

To help ensure clean IP (which will help us keep this project free and open source), pull requests
for source code changes require a signed contributor agreement to be submitted in advance. We use
the Apache contributor model agreements (modified to identify this specific project), which can be
found in the [license](./license) directory. Contributors are required to sign and submit an Ecstasy
Project Individual Contributor License Agreement (ICLA), or be a named employee on an Ecstasy
Project Corporate Contributor License Agreement (CCLA), both derived directly from the Apache
agreements of the same name. (Sorry for the paper-work! We hate it, too!)

The Ecstasy name is a trademark owned and administered by The Ecstasy Project. Unlicensed use of the
Ecstasy trademark is prohibited and will constitute infringement.

All content of the project not covered by the above terms is probably an accident that we need to be
made aware of, and remains (c) The Ecstasy Project, all rights reserved.

## Layout

The project is organized as a number of subprojects, with the important ones to know about being:

* The Ecstasy core library is in the [xvm/lib_ecstasy](./lib_ecstasy) directory, and is conceptually
  like `stdlib` for C, or `rt.jar` for Java. When the XDK is built, the resulting module is located
  at `xdk/lib/ecstasy.xtc`. This module contains portions of the Ecstasy tool chain, including the
  lexer and parser. (Ecstasy source files use an `.x` extension, and are compiled into a single
  module file with an `.xtc` extension.)
  
* The Java tool chain (including an Ecstasy compiler and interpreter) is located in the 
  [xvm/javatools](./javatools) directory.  When the XDK is built, the resulting `.jar` file is
  located at `xdk/javatools/javatools.jar`.
  
* There is an Ecstasy library in [xvm/javatools_bridge](./javatools_bridge) that is used by the Java
  interpreter to boot-strap the runtime. When the XDK is built, the resulting module is located at 
  `xdk/javatools/javatools_bridge.xtc`.
  
* The wiki documentation is [online](https://github.com/xtclang/xvm/wiki). There is an
  [introduction to Ecstasy](https://github.com/xtclang/xvm/wiki/lang-intro) that is being written
  for new users. The wiki source code will (eventually) be found in the `xvm/wiki` project directory,
  and (as a distributable) in the `xdk/doc` directory of the built XDK. 
  
* Various other directories will have a `README.md` file that explains their purpose.

To download the entire project from the terminal, you will need
[git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) installed. From the terminal,
go to the directory where you want to create a local copy of the Ecstasy project, and: 

    git clone https://github.com/xtclang/xvm.git
    
(There is excellent online documentation for git at
[git-scm.com](https://git-scm.com/book/en/v2/Git-Basics-Getting-a-Git-Repository).)

To build the entire project, you need to have [gradle](https://gradle.org/install/), or you use the
included Gradle Wrapper from within the `xvm` directory:

    ./gradlew build

Or on Windows: 

    gradlew build

Note that Windows may require the `JAVA_TOOLS_OPTIONS` environment variable to be set to
`-Dfile.encoding=UTF-8` in the Environment Variables window that can be accessed from Control Panel.
This allows the Java compiler to automatically handle UTF-8 encoded files, and several of the Java
source files used in the Ecstasy toolchain contain UTF-8 characters. Also, to change the default
encoding used in Windows, go to the "Administrative" tab of the "Region" settings Window (also
accessed from Control Panel), click the "Change system locale..." button and check the box labeled
"Beta: Use UTF-8 for worldwide language support". 

Instructions for getting started can be found in our [Contributing to Ecstasy](CONTRIBUTING.md)
document.

## Questions?

To submit a contributor agreement, sign up for very hard work, fork over a giant
pile of cash, or in case of emergency: "info _at_ xtclang _dot_ org", but please
understand if we cannot respond to every email. Thank you.