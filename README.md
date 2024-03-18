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

Read more
at [https://xtclang.blogspot.com/](https://xtclang.blogspot.com/2016/11/welcome-to-ecstasy-language-first.html)

Follow us on Twitter [@xtclang](https://twitter.com/xtclang)

Find out more about [how you can contribute to Ecstasy](CONTRIBUTING.md).

And please respect our [code of conduct](CODE_OF_CONDUCT.md) and each other.

## Binary Installation

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
  ./gradlew build
```

## Development

### Recommended Git workflow

*A note about this section: this workflow is supported by pretty much every
common GUI in any common IDE, in one way or another. But in the interest of
not having to document several instances with slightly different naming convention,
or deliver a confusing tutorial, this section only describes the exact bare
bones command line git commands that can be used to implement our workflow,
which is also a common developer preference. All known IDEs just wrap these
commands in one way or another.*

#### Make sure "pull.rebase" is set to "true" in your git configuration

In order to maintain linear git history, and at any cost avoid merges being created
and persisted in the code base, please make sure that your git configuration will
run "pull" with "rebase" as its default option. Preferably globally, but at least
for the XVM repository.

```
git config --get pull.rebase
```

Output should be "true".

If it's not, execute

```
git config --global pull.rebase true
```

or from a directory inside the repository:

```
git config --local pull.rebase true
```

The latter will only change the pull semantics for the repository itself, and
the config may or may not be rewritten by future updates.

#### Always work in a branch. Do not work directly in master

XTC will very soon switch to only allowing putting code onto the master branch through
a pull request in a sub branch.

In order to minimize git merges, and to keep master clean, with a minimum of complexity,
the recommended workflow for submitting a pull request is as follows:

##### 1) Create a new branch for your change, and connect it to the upstream:

```
git checkout -B decriptive-branch-name
git push --set-upstream origin descriptive-branch-name
```

##### 2) Perform your changes, and commit them. We currently do not have any syntax requirements

on commit descriptions, but it's a good idea to describe the purpose of the commit.

```
git commit -m "Descriptive commit message, including a github issue reference, if one exists"
```

##### 3) Push your changes to the upstream and create a pull request, when you are ready for review

```
git push
```

##### Resolving conflicts, and keeping your branch up to date with master

Whenever you need to, and this is encouraged, you should rebase your local branch,
so that your changes get ripped out and re-transplanted on top of everything that has
been pushed to master, during the time you have been working on the branch.

Before you submit a pull request, you *need* to rebase it against master. We will
gradually add build pipeline logic for helping out with this, and other things, but
it's still strongly recommended that you understand the process.

To do a rebase, which has the effect that your branch will contain all of master,
with your commits moved to the end of history, execute the following commands:

```
git fetch 
git rebase origin/master
```

The fetch command ensures that the global state of the world, whose local copy is stored
in the ".git" directory of the repository, gets updated. Remember that git allows you to
work completely offline, should you chose to do so, after you have cloned a repository.
This means that, in order to get the latest changes from the rest of the world, and make
sure you are working in an up-to-date environment, you need to fetch that state from the
upstream.

If there are any conflicts, the rebase command above will halt and report conflict.
Should this be the case, change your code to resolve the conflicts, and verify that it
builds clean again. After it does, add the resolved commit and tell git to continue
with the rebase:

```
git add .
git rebase --continue
```

If you get entangled, you can always restart the rebase by reverting to the state
where you started:

```
git rebase --abort
```

After rebasing, it's a good idea to execute "git status", to see if there are heads
from both master and your local branch. Should this be the case, you need to resolve
the rebase commit order by force pushing the rebased version of you local branch
before creating the pull request for review:

```
git status
git push -f # if needed
```

##### Do not be afraid to mess around in your local branch

You should feel free to commit and push as much as you want in your local branch, if
your workflow so requires. However, before submitting the finished branch as a pull
request, please do an interactive rebase and collapse any broken commits that don't
build, or any small commits that just fix typos and things of a similar nature.

* _It is considered bad form to submit a pull request where there are unnecessary
  or intermediate commits, with vague descriptions._

* _It is considered bad form to submit a pull request where there are commits, which
  do not build and test cleanly._ This is important, because it enables things like
  automating git bisection to narrow down commits that may have introduced bugs,
  and it has various other benefits. The ideal state for master, should be that
  you can check it out at any change in its commit history, and that it will build
  and test clean on that head.

Most pull requests are small in scope, and should contain only one commit, when
they are put up for review. If there are distinct unrelated commits, that both contribute
to solving the issue you are working on, it's naturally fine to not squash those together,
as it's easier to read and shows clear separation of concerns.

If you need to get rid of temporary, broken, or non-buildable commits in your branch,
do an interactive rebase before you submit it for review. You can execute:

```
git rebase -i HEAD~n
```

to do this, where *n* is the number of commits you are interested in modifying.

* *According to the git philosophy, branches should be thought of as private, plentiful
  and ephemeral. They should be created at the drop of a hat, and the branch should be
  automatically or manually deleted after its changes have been merged to master.
  A branch should never be reused.*

The described approach is a good one to follow, since it moves any complicated source control
issues completely to the author of a branch, without affecting master, and potentially
breaking things for other developers. Having to modify the master branch, due to
unintended merge state or changes having made their way into it, is a massively more
complex problem than handling all conflicts and similar issues in the private local
branches.

## Status

Version 0.4. That's way _before_ version 1.0. In other words, Ecstasy is about as mature as 
Windows 3.1 was.

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
  [xvm/javatools](./javatools) directory. When the XDK is built, the resulting `.jar` file is
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

```
git clone https://github.com/xtclang/xvm.git
```

(There is excellent online documentation for git at
[git-scm.com](https://git-scm.com/book/en/v2/Git-Basics-Getting-a-Git-Repository).)

To build the entire project, you need to have [gradle](https://gradle.org/install/), or you use the
included Gradle Wrapper from within the `xvm` directory, which is the recommended method:

```
./gradlew build
```

Or on Windows:

```
C:\> gradlew.bat build
```

Note that Windows may require the `JAVA_TOOLS_OPTIONS` environment variable to be set to
`-Dfile.encoding=UTF-8` in the Environment Variables window that can be accessed from Control Panel.
This allows the Java compiler to automatically handle UTF-8 encoded files, and several of the Java
source files used in the Ecstasy toolchain contain UTF-8 characters. Also, to change the default
encoding used in Windows, go to the "Administrative" tab of the "Region" settings Window (also
accessed from Control Panel), click the "Change system locale..." button and check the box labeled
"Beta: Use UTF-8 for worldwide language support".

Instructions for getting started can be found in our [Contributing to Ecstasy](CONTRIBUTING.md)
document.

## Cleaning the build

You can clean everything in a build by running

    ./gradlew clean  

However, note that if you restart the build, a lot of intermediary outputs will be cached.
The XDK and XTC plugin use the Gradle build system intrinsics to only compile what has provably
mutated its outputs and inputs. This should be stable, and is by design. You should really never have to
clean your build to test any incremental change. This is even true if you modify the plugin implementation
in the XDK repo, as we are using included builds everywhere we should. The end goal is that any
change will only require rebuilding, and that rebuild should only build exactly what is necessary.

Should you, for any reason, need to clear the caches, and really start fresh, you can run the script

./bin/purge-all-build-state.sh

Or do the equivalent actions manually:

1) Close any open XTC projects in your IDEs, to avoid restarting them with a large state change under the hood.
   Optionally, also close your IDE processes.
2) Kill all Gradle daemons.
3) Delete the `$GRADLE_USER_HOME/cache` and `$GRADLE_USER_HOME/daemons` directories. *NOTE: this invalidates
   caches for all Gradle builds on your current system, and rebuilds a new Gradle version.*
4) Run `git clean -xfd` in your build root. Note that this may also delete any IDE configuration that resides
  in your build. You may want to preserve e.g. the `.idea` directory, and then you can do `git clean -xfd -e .idea`
  or perform a dry run `git clean -xfdn`, to see what will be deleted. Note that if you are at this level of
  purging stuff, it's likely a bad idea to hang on to your IDE state anyway.

## Debugging the build

The build should be debuggable through any IDE, for example IntelliJ, using its Gradle tooling API
hook. You can run any task in the project in debug mode from within the IDE, with breakpoints in
the build scripts and/or the underlying non-XTC code, for example in Javatools, to debug the
compiler, runner or disassembler.

### Augmenting the build output

XTC follow Gradle best practise, and you can run the build, or any task therein, with the standard
verbosity flags. For example, to run the build with more verbose output, use:

```
./gradlew build --info --stacktrace
```

The build also supports Gradle build scans, which can be generated with:

```
./gradlew build --scan --stacktrace
```

Note that build scans are published to the Gradle online build scan repository (as configured
through the `gradle-enterprise` settings plugin.), so make sure that you aren't logging any
secrets, and avoid publishing build scans in "--debug" mode, as it may be a potential security
hazard.

You can also combine the above flags, and use all other standard Gradle flags, like `--stacktrace`,
and so on.

### Tasks

To see the list of available tasks for the XDK build, use:

```
./gradlew tasks
```

#### Versioning and Publishing XDK artifacts

* Use `publishLocal`to publish an XDK build to the local Maven repository and a build specific repository directory.
* Use `publishRemote`to publish and XDK build to the xtclang organization package repo on GitHub (a GitHub token with
  permissions is required). This task will fail if you are trying to publish a SNAPSHOT release to a release repository,
  or if the XDK version you are building is a SNAPSHOT version. 
* Use `publishRemoteSnapshot` to publish a SNAPSHOT release to the release repository. Note that the code is identical,
  and build will behave correctly, dependning only whether the VERSION file contains a SNAPSHOT suffixed version or not.
  The reason that there are two different tasks, is to minimize accidental publishing of a SNAPSHOT as a release or
  vice versa.
* Use `publish` to run both of the above tasks.

*Note*: At the moment some publish tasks may have some raciness in execution, due to Gradle issues. Should you 
get some kind of error during the publishing task, it may be a good idea to clean, and then rerun that task 
with the Gradle flag `--no-parallel`.

The group and version of the current XDK build and the XTC Plugin are currently defined in 
the properties file "version.properties". Here, we define the version of the current XDK 
and XTC Plugin, as well as their group. The default behavior is to only define the XDK, since
at this point, the Plugin, while decoupled, tracks and maps to the XDK version pretty much 1-1.
This can be taken apart with different semantic versioning, should we need to. Nothing is assuming
the plugin has the same version or group as the XDK. It's just convenient for time being.

The file `gradle/libs.versions.toml` contains all internal and external by-artifact version 
dependencies to the XDK project. If you need to add a new plugin, library, or bundle, always define
its details in this version catalog, and nowhere else. The XDK build logic, will dynamically plugin
in values for the XDK and XTC Plugin artifacts that will be used only as references outside this file.

*TODO*: In the future we will also support tagging and publishing releases on GitHub, using JReleaser or a
similar framework.

Typically, the project version of anything that is unreleased should be "x.y.z-SNAPSHOT", and the first
action after tagging and uploading a release of the XDK, is usually changing the release version in 
"VERSION" in the xvm repository root, and (if the plugin is versioned separately, optionally in "plugin/VERSION") 
both by incrementing the micro version, and by adding a SNAPSHOT suffix. You  will likely find yourself 
working in branches that use SNAPSHOT versions until they have made it into a release train. The CI/CD 
pipeline can very likely handle this automatically.

## Bleeding Edge for Developers

If you would like to contribute to the Ecstasy Project, it might be an idea to use the
very latest version by invoking:

```
./gradlew installDist
```

This will create a self-contained XDK distribution under `xdk/build/install/xdk`, including 
launchers for the build host operating system. You can put this directory first on your
system path, or set `XDK_HOME` to point at it, so that any XTC launcher will redirect its
implementation to the libraries in the local build.

```
```

For more information about the XTC DSL, please see the README.md file in the "plugin" project.

### Releasing and Publishing

This is mostly relevant to the XDK development team with release management privileges. A version
of the workflow for adding XTC releases is described [here](https://www.baeldung.com/maven-snapshot-release-repository).

We plan to move to an automatic release model in the very near future, utilizing JRelease
(and JPackage to generate our binary launchers). As an XTC/XDK developer, you do not have
to understand all the details of the release model. The somewhat incomplete and rather 
manual release mode is current described here for completeness. It will soon be replaced
with something familiar.

### XDK Platform Releases

1) Take the current version of master and create a release branch. 
2) Set the VERSION in the release branch project root to reflect the version of the release.
Typically an ongoing development branch will be a "-SNAPSHOT" suffixed release, but not
an official XTC release, which just has a group:name:version number
3) Build, tag and add the release using the GitHub release plugin.

### XDK Platform Publishing 

We have verified credentials for artifacts with the group "org.xtclang" at the best known
community portals, and will start publishing there, as soon as we have an industrial
strength release model completed.

The current semi-manual process looks like this:

1) ./gradlew publish to build the artifacts and verify they work. This will publish the artifacts
to a local repositories and the XTC GitHub org repository. 
2) To publish the plugin to Gradle Plugin Portal: ./gradlew :plugin:publishPlugins (publish the plugin to gradlePortal)
3) To publish the XDK distro to Maven Central: (... TODO ... )

You can already refer to the XDK and the XTC Plugin as external artifacts for your favourite
XTC project, either by mnaually setting up a link to the XTC Org GitHub Maven Repository like this:

```
repositories {
   maven {
     url = https://maven.pkg.github.com/xtclang/xvm
     credentials {
        username = <your github user name>
        token = <a personal access token with read:package privileges on GitHub Maven Packages>
   }
}
```

or by simply publishing the XDK and XDK Plugin to your mavenLocal repository, and adding
that to the configuration of your XTC project, if it's not there already: 

```
repositories {
   mavenLocal()
}
```

## Questions?

To submit a contributor agreement, sign up for very hard work, fork over a giant
pile of cash, or in case of emergency: "info _at_ xtclang _dot_ org", but please
understand if we cannot respond to every e-mail. Thank you.

## Appendix: Gradle fundamentals

We have tried very hard to create an easy-to-use build system based on industry standards 
and expected behavior. These days, most software is based on the Maven/Gradle model, which 
provides repositories of semantically versioned artifacts, cached incremental builds and 
mature support for containerization.

The principle of least astonishment permeates the philosophy behind the entire build system.
This means that a modern developer, should be immediately familiar with how to build and run 
the XDK project, i.e. clone it from GitHub and execute "./gradlew build". It should also
import complaint free, and with dependency chains understood by any IDE that has support
for Gradle projects. "It should just work", out of the box, and should look familiar to any
developer with basic experience as a Gradle user. Nothing should require more than a single
command like to build or execute the system or anything built on top of it.

Implementing language support for an alien language on top of Gradle, however, is a fairly
complex undertaking, and requires deeper knowledge of the Gradle architecture. It is 
our firm belief, though, that the user should not have to drill down to these levels, unless he/she 
specifically wants to. As it is, any open source developer today still needs to grasp some basic 
fundamentals about artifacts and the Gradle build system. This is not just our assumption; it is 
actually industry-wide. 

We believe the following concepts are necessary to understand, in order to work with XDK 
projects or the XDK. None of them are at all specific to XTC:

* The concept of "gradlew" and "mvnw" (or "gradlew.bat" and "mvnw.bat" on Windows) wrappers, 
  and why it should ALWAYS be used instead of a "gradle" binary on the local system, for any 
  repository that ships it with its build.
* The concept of a versioned Maven artifact, and that its descriptor "group:artifactId:version"
  is its "global address", no matter how it is resolved on the lower abstraction layer.
* The concept of release vs snapshot artifact versions in the Maven model.
* The concept of local (mostly mavenLocal()) and remote artifact repositories, and how they are used 
  by a maven build.
* The concept of the Maven/Gradle build lifecycle, its fundamental tasks, and how they depend
  on each other ("clean", "assemble", "build" and "check"). 
* The concept of the Gradle/Maven cache, build daemons, and why "clean" is not what you think  
  of as "clean" in a C++ Makefile and why is it often better not to use it, in a cached, incrementally
  built Gradle project.
* The concept of Maven/Gradle source sets, like "main", "resources" and "test". 
* The concept of a Gradle build scan, and understanding how to inspect it and how to use it to 
  spot build issues.
* The standard flags that can be used to control Gradle debug log levels, --info, -q, --stacktrace
  and so on.
* The concept of goal of self-contained software, which specifies its complete dependencies
  as part of its source controlled configuration. 
  1) On the Maven model level, this means semantically versioned Maven artifacts. 
  2) On the software build and execution level, this also means specific versions of external
    pieces of software, for example Java, NodeJS or Yarn. This also means that we CAN and SHOULD
    always be able to containerize for development purposes.

Today, it is pretty safe to assume that most open source developers who has worked on any Gradle
or Maven based project has at least the most important parts of the above knowledge.
We have spent significant architectural effort to ensure that an adopter who wants to become an 
XTC or XDK user or developer does not need to acquire *any* knowledge that is
more domain specific than concepts listed above. None of these concepts are specific to the
XTC platform, but should be familiar to most software developers who have worked on projects
with Maven style build systems.

We will also work on IDE Language support as soon as we have enough cycles to do so, which
should make getting up to speed with XTC and even less complicated process.
