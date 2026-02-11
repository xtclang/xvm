# Welcome to Ecstasy! #

This is the public repository for the Ecstasy language ([xtclang.org](http://xtclang.org/)) and the
Ecstasy virtual machine (XVM) project.

## Quick Start

**Want to try Ecstasy right now? Here's the fastest way:**

### Option 1: Homebrew (macOS/Linux) - Recommended
```bash
# Install Homebrew if you don't have it: https://brew.sh/
brew tap xtclang/xvm && brew install xdk-latest

# Create your first Ecstasy program
echo 'module HelloWorld { void run() { @Inject Console console; console.print("Hello, World!"); } }' > HelloWorld.x

# Compile and run it
xtc build HelloWorld.x  // NOTE: Legacy shortcut "xcc" is the same as "xtc build"
xtc run HelloWorld      // NOTE: Legacy shortcut "xec" is the same as "xtc build"
```

### Option 2: Docker - Works everywhere
```bash
# Create your first program
echo 'module HelloWorld { void run() { @Inject Console console; console.print("Hello, World!"); } }' > HelloWorld.x

# Compile and run using Docker
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xtc build /workspace/HelloWorld.x
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xtc run /workspace/HelloWorld
```

### Option 3: Build from source
```bash
git clone https://github.com/xtclang/xvm.git
cd xvm
./gradlew xdk:installDist
export PATH=$PWD/xdk/build/install/xdk/bin:$PATH

# Now you can use xtc commands
```

**Next Steps:**
- [Learn Ecstasy Language Basics](https://github.com/xtclang/xvm/wiki)
- [XDK Development Kit Guide](#installation-options) (below)
- [Docker Development Guide](docker/README.md)
- [GitHub Actions & CI Documentation](.github/GITHUB_WORKFLOWS.md)

---

## Documentation Navigation

This repository contains comprehensive documentation organized hierarchically:

### Core Documentation
- **[Main README](README.md)** (this file) - Platform overview, quickstart, and XDK installation
- **[Contributing Guide](CONTRIBUTING.md)** - How to contribute to the Ecstasy project
- **[Code of Conduct](CODE_OF_CONDUCT.md)** - Community guidelines and expectations

### Build & Development
- **[Building XTC From Source](doc/build.md)** - Build tasks, distributions, clean semantics, debugging, versioning, and publishing
- **[Git Workflow](doc/git-workflow.md)** - Rebase-only linear history, branch workflow, and GitHub branch protection
- **[Gradle Fundamentals](doc/assumed-gradle-knowledge.md)** - Essential Gradle concepts for developers new to Gradle

### Tools & CLI
- **[XTC CLI Reference](doc/xtc-cli.md)** - Command-line tools: `xtc init`, `xtc build`, `xtc run`, `xtc test`
- **[Docker Guide](docker/README.md)** - Container development, build instructions, and CI integration
- **[GitHub Actions](/.github/GITHUB_WORKFLOWS.md)** - CI/CD pipeline, workflows, and automation documentation

### Language Documentation
- **[Ecstasy Language Wiki](https://github.com/xtclang/xvm/wiki)** - Language specification, tutorials, and examples
- **[XTC Language Website](http://xtclang.org/)** - Official language website and resources
- **[Core Documentation](doc/DOCUMENTATION.md)** - Language specification files, BNF grammar, and VM instruction set

---

## What is Ecstasy?

<table style="border-collapse: collapse; border: none; border-spacing: 0; padding: 0;">
<tr style="border: none;"><td style="border: none; padding: 0;">

![Ecstasy](./doc/logo/x.jpg "The Ecstasy Project")

</td><td style="border: none; padding: 0;">

Ecstasy is an application programming language, designed to enable modular development and long-term
sustainability of secure, "serverless cloud" applications. Ecstasy leverages a reactive,
event-driven, service- and fiber-based execution model within a container-based architecture to
achieve energy-efficient, high-density, autonomically-managed deployments. In a nut-shell, Ecstasy
is designed to be secure, easy to deploy, easy to monitor, easy to manage, and easy to evolve.

</td></tr></table>

The Ecstasy project includes: a development kit (the Ecstasy development kit, the "xdk") that is
produced from this git repo; a programming language specification; a core set of runtime modules
(libraries); a portable, type-safe, and verifiable Intermediate Representation (IR); a
proof-of-concept interpreted runtime; a JIT compiler targeting the JVM (in development); and a
tool-chain with both Java and Ecstasy implementations being actively developed.

The Ecstasy language supports first class modules, including versioning and conditionality; first
class functions, including currying and partial application; type-safe object orientation,
including support for auto-narrowing types, type-safe covariance, mixins, and duck-typed interfaces;
type inference; first class deeply-immutable types; first class asynchronous services, including
both automatic `async/await`-style and promises-based (`@Future`) programming models; and first
class software containers, including resource injection and transitively-closed, immutable type
systems. _And much, much more._

Read more at: [https://github.com/xtclang/xvm/wiki](https://github.com/xtclang/xvm/wiki)

Find out more about [how you can contribute to Ecstasy](CONTRIBUTING.md).

And please respect our [code of conduct](CODE_OF_CONDUCT.md) and each other.

## Installation Options

### Package Managers (Recommended for Development)

**Homebrew (macOS/Linux) - CI Snapshots:**

Homebrew provides continuously updated `xdk-latest` builds from our CI pipeline:

```bash
# Install Homebrew if you have not already done so: https://brew.sh/
brew tap xtclang/xvm && brew install xdk-latest

# Upgrade to latest CI build (choose one):
brew update && brew upgrade xdk-latest  # Standard approach
brew reinstall xdk-latest               # Alternative: always gets latest
```

Note: Homebrew delivers CI snapshots for development. Each snapshot gets a unique timestamp-based
version (e.g., `0.4.4-SNAPSHOT.20250831181403`). You must run `brew update` first to refresh the
tap, then `brew upgrade xdk-latest` will detect the newer build. Alternatively, `brew reinstall`
always installs the latest snapshot. Stable releases will be available through other package managers.

#### How Snapshot Releases Work

Our CI system maintains a single, continuously updated snapshot release:

- **Release Name**: `XDK Snapshot Builds`
- **GitHub Tag**: `xdk-snapshots`
- **Download URL**: `https://github.com/xtclang/xvm/releases/download/xdk-snapshots/xdk-0.4.4-SNAPSHOT.zip`

**Automatic Overwrite Process:**
1. Every push to `master` triggers the CI pipeline
2. The existing `xdk-snapshots` release is **completely deleted**
3. A new release with the same tag is created with the latest build
4. The Homebrew formula gets dynamic versioning: `0.4.4-SNAPSHOT.{timestamp}`

This ensures:
- Only **one** snapshot release exists (never accumulates old releases)
- Download URL remains consistent for automation
- `brew update` works correctly due to timestamp-qualified versioning
- Always reflects the latest master commit

#### Snapshots vs. Stable Releases

**Current Status**: The XDK is currently in active development using snapshot versioning (e.g., `0.4.4-SNAPSHOT`).

**Snapshots in Maven Ecosystem**:
- **Snapshots** (`*-SNAPSHOT`) are development builds that can change frequently
- Maven/Gradle automatically checks for newer snapshot versions during builds
- Intended for active development, testing, and CI/CD pipelines
- Not suitable for production use due to changing behavior

**Stable Releases** (coming soon):
- **Fixed versions** (e.g., `0.5.0`, `1.0.0`) are immutable once published
- Provide stability guarantees and semantic versioning
- Cached permanently by build systems - no automatic updates
- Suitable for production applications

**Next Release Timeline**: We will publish the next non-snapshot version of the XDK as soon as all build infrastructure updates are complete. This will mark the transition from active development snapshots to stable, production-ready releases with proper semantic versioning.

#### GitHub Workflows and Automation

Our project uses comprehensive GitHub workflows for continuous integration, dependency management, and automated releases.

**For complete documentation of our CI/CD pipeline, GitHub Actions, custom actions, manual workflow controls, Dependabot configuration, and all automation details, see:**

**[.github/GITHUB_WORKFLOWS.md - XVM GitHub Workflows and Actions](.github/GITHUB_WORKFLOWS.md)**

This includes:
- **CI/CD Pipeline**: Multi-platform builds, testing, Docker images, and publishing
- **Dependabot**: Automated dependency updates for Gradle, GitHub Actions, and Docker
- **Manual Controls**: Workflow dispatch options and monitoring commands
- **Custom Actions**: Reusable automation components
- **Future Improvements**: Planned enhancements and simplifications

**Quick CI Overview**:
1. **Build & Test**: Multi-platform builds (Ubuntu + Windows) with comprehensive testing
2. **Snapshot Release**: Automated GitHub releases with XDK distribution packages
3. **Homebrew Integration**: Automatic updates to [xtclang/homebrew-xvm](https://github.com/xtclang/homebrew-xvm) tap
4. **Docker Images**: Multi-architecture container builds published to `ghcr.io/xtclang/xvm`

**Homebrew Configuration**:
- **Target Branch**: Controlled by `HOMEBREW_TAP_BRANCH` repository variable (currently: `lagergren/brew-tap`)
- **Auto-generated Formula**: Version, SHA256, and dependencies computed automatically from build

*Last updated: 2025-08-25*

**Windows:**

* Visit [http://xtclang.org/xdk-latest.html](http://xtclang.org/xdk-latest.html) for Windows installer

### Docker Container

Use the official XDK Docker image for development or CI:

```bash
# Run XDK commands in container
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xtc build /workspace/MyModule.x
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xtc run /workspace/MyModule

# Interactive development shell
docker run -it --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest bash
```

**Multi-platform Support**: The XDK Docker image supports both `linux/amd64` and `linux/arm64` architectures, running natively on macOS (Intel/Apple Silicon), Windows (via WSL2), and Linux systems.

**Development Container Support**: This project includes a devcontainer configuration at `.devcontainer/devcontainer.json` for VSCode development.

**For complete Docker documentation, build instructions, CI integration, and advanced usage, see:**
**[docker/README.md](docker/README.md)**

### XTC Command-Line Tools

The XDK provides the `xtc` unified command-line tool for working with Ecstasy projects:

| Command | Description |
|---------|-------------|
| `xtc init` | Create a new XTC project with standard structure |
| `xtc build` | Compile Ecstasy source files (legacy alias: `xcc`) |
| `xtc run` | Execute an Ecstasy module (legacy alias: `xec`) |
| `xtc test` | Run tests in an Ecstasy module using xunit |

For full CLI documentation, see the **[XTC CLI Reference](doc/xtc-cli.md)**.

### Building from Source

Prerequisites:
* **Bootstrap JVM**: Any Java 17+ to run the Gradle wrapper (just to bootstrap the build)
* **Target JDK**: Gradle toolchain automatically provisions the correct JDK version for building XTC
* **Gradle**: Not required to be pre-installed (project includes Gradle Wrapper)

```bash
git clone https://github.com/xtclang/xvm.git
cd xvm
./gradlew build
./gradlew xdk:installDist
export PATH=$PWD/xdk/build/install/xdk/bin:$PATH
```

**Note:** The Gradle build system uses a toolchain to automatically download and configure the correct Java version if it's not already installed. No manual Java installation is typically required.

For detailed build tasks, distribution options, environment configuration, debugging, and publishing, see **[Building XTC From Source](doc/build.md)**.

## Development

### XTC Plugin and Build System Testing

For comprehensive examples of using the XTC Gradle plugin and testing XTC applications, see the [**manualTests**](manualTests/build.gradle.kts) inline documentation. This project demonstrates:

- XTC Gradle plugin configuration and usage
- Build lifecycle best practices and caching
- Configuration cache compatibility
- Custom task creation and testing scenarios
- Debugging and troubleshooting XTC builds

The manualTests project serves as both integration tests and comprehensive documentation for the XTC build system.

### Git Workflow

We use a **rebase-only, linear history** workflow. Never merge from master to branches - always rebase. For the full workflow including branch setup, conflict resolution, commit cleanup, and GitHub branch protection, see the **[Git Workflow Guide](doc/git-workflow.md)**.

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
  interpreter to bootstrap the runtime. When the XDK is built, the resulting module is located at
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

To build the entire project, use the included Gradle Wrapper (recommended method):

```
./gradlew build
```

Or on Windows:

```
C:\> gradlew.bat build
```

**Note:** Always use the Gradle Wrapper (`./gradlew`) rather than a system-installed Gradle binary to ensure the correct Gradle version is used.

Note that Windows may require the `JAVA_TOOLS_OPTIONS` environment variable to be set to
`-Dfile.encoding=UTF-8` in the Environment Variables window that can be accessed from Control Panel.
This allows the Java compiler to automatically handle UTF-8 encoded files, and several of the Java
source files used in the Ecstasy toolchain contain UTF-8 characters. Also, to change the default
encoding used in Windows, go to the "Administrative" tab of the "Region" settings Window (also
accessed from Control Panel), click the "Change system locale..." button and check the box labeled
"Beta: Use UTF-8 for worldwide language support".

Instructions for getting started can be found in our [Contributing to Ecstasy](CONTRIBUTING.md)
document.

## Related Repositories

The xtclang organization maintains several repositories supporting the Ecstasy ecosystem:

### Core Platform
- **[platform](https://github.com/xtclang/platform)** - Ecstasy "Platform as a Service" implementation for cloud deployment

### Distribution & Packaging
- **[xdk-release](https://github.com/xtclang/xdk-release)** - Cross-platform XDK distribution and release automation
- **[homebrew-xvm](https://github.com/xtclang/homebrew-xvm)** - Homebrew tap for macOS/Linux XDK installations

### Research & Extensions
- **[jmixin](https://github.com/xtclang/jmixin)** - Java port of Ecstasy mixin functionality for research and comparison

## Questions?

To submit a contributor agreement, sign up for very hard work, fork over a giant
pile of cash, or in case of emergency: "info _at_ xtclang _dot_ org", but please
understand if we cannot respond to every e-mail. Thank you.
