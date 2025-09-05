# Welcome to Ecstasy! #

This is the public repository for the Ecstasy language ([xtclang.org](http://xtclang.org/)) and the
Ecstasy virtual machine (XVM) project.

## 🚀 Quick Start

**Want to try Ecstasy right now? Here's the fastest way:**

### Option 1: Homebrew (macOS/Linux) - Recommended
```bash
# Install Homebrew if you don't have it: https://brew.sh/
brew tap xtclang/xvm && brew install xdk-latest

# Create your first Ecstasy program
echo 'module HelloWorld { void run() { @Inject Console console; console.print("Hello, World!"); } }' > HelloWorld.x

# Compile and run it
xcc HelloWorld.x
xec HelloWorld
```

### Option 2: Docker - Works everywhere
```bash
# Create your first program
echo 'module HelloWorld { void run() { @Inject Console console; console.print("Hello, World!"); } }' > HelloWorld.x

# Compile and run using Docker
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xcc /workspace/HelloWorld.x
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xec /workspace/HelloWorld
```

### Option 3: Build from source
```bash
git clone https://github.com/xtclang/xvm.git
cd xvm
./gradlew xdk:installDist
export PATH=$PWD/xdk/build/install/xdk/bin:$PATH

# Now you can use xcc and xec commands
```

**Next Steps:**
- 📖 [Learn Ecstasy Language Basics](https://github.com/xtclang/xvm/wiki)
- 🛠️ [XDK Development Kit Guide](#installation-options) (below)
- 🐳 [Docker Development Guide](docker/README.md)
- ⚙️ [GitHub Actions & CI Documentation](.github/GITHUB_WORKFLOWS.md)

---

## 📚 Documentation Navigation

This repository contains comprehensive documentation organized hierarchically:

### Core Documentation
- **[Main README](README.md)** (this file) - Platform overview, quickstart, and XDK installation
- **[Contributing Guide](CONTRIBUTING.md)** - How to contribute to the Ecstasy project
- **[Code of Conduct](CODE_OF_CONDUCT.md)** - Community guidelines and expectations

### Development Documentation  
- **[Docker Guide](docker/README.md)** - Container development, build instructions, and CI integration
- **[GitHub Actions](/.github/GITHUB_WORKFLOWS.md)** - CI/CD pipeline, workflows, and automation documentation
- **[Developer Scripts](bin/DEVELOPER_SCRIPTS.md)** - Build automation and development utility scripts
- **[Testing Guide](manualTests/TESTING_GUIDE.md)** - Manual testing procedures and test suite documentation

### Language Documentation
- **[Ecstasy Language Wiki](https://github.com/xtclang/xvm/wiki)** - Language specification, tutorials, and examples
- **[XTC Language Website](http://xtclang.org/)** - Official language website and resources
- **[Core Documentation](doc/DOCUMENTATION.md)** - Language specification files, BNF grammar, and VM instruction set

---

## What is Ecstasy?

<table cellspacing="0" cellpadding="0" style="border-collapse: collapse; border: none;">
<tr style="border: none;"><td style="border: none;">

![Ecstasy](./doc/logo/x.jpg "The Ecstasy Project")

</td><td style="border: none;">

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
# Upgrade to latest CI build:
brew update && brew upgrade xdk-latest
```

Note: Homebrew delivers CI snapshots for development. Each snapshot gets a unique timestamp-based
version (e.g., `0.4.4-SNAPSHOT.20250831181403`) ensuring `brew update && brew upgrade xdk-latest`
detects newer builds correctly. Stable releases will be available through other package managers.

#### How Snapshot Releases Work

Our CI system maintains a single, continuously updated snapshot release:

- **Release Name**: `XDK Latest Snapshot`
- **GitHub Tag**: `xdk-latest-snapshot`
- **Download URL**: `https://github.com/xtclang/xvm/releases/download/xdk-latest-snapshot/xdk-0.4.4-SNAPSHOT.zip`

**Automatic Overwrite Process:**
1. Every push to `master` triggers the CI pipeline
2. The existing `xdk-latest-snapshot` release is **completely deleted**
3. A new release with the same tag is created with the latest build
4. The Homebrew formula gets dynamic versioning: `0.4.4-SNAPSHOT.{commitSHA}`

This ensures:
- ✅ Only **one** snapshot release exists (never accumulates old releases)
- ✅ Download URL remains consistent for automation
- ✅ `brew update` works correctly due to commit-qualified versioning
- ✅ Always reflects the latest master commit

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

**[📖 .github/README.md - XVM GitHub Workflows and Actions](.github/README.md)**

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
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xcc /workspace/MyModule.x
docker run --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest xec /workspace/MyModule

# Interactive development shell  
docker run -it --rm -v $(pwd):/workspace ghcr.io/xtclang/xvm:latest bash
```

**Multi-platform Support**: The XDK Docker image supports both `linux/amd64` and `linux/arm64` architectures, running natively on macOS (Intel/Apple Silicon), Windows (via WSL2), and Linux systems.

**Development Container Support**: This project includes a devcontainer configuration at `.devcontainer/devcontainer.json` for VSCode development.

**📖 For complete Docker documentation, build instructions, CI integration, and advanced usage, see:**
**[docker/README.md](docker/README.md)**

### Maven Artifacts and IDE Integration

**For Most Developers:** Use the XTC Gradle plugin in your IDE instead of command-line tools:

```kotlin
// In your build.gradle.kts
plugins {
    id("org.xtclang.xtc") version "0.4.4-SNAPSHOT"
}
```

**Maven Repository Access:**

```kotlin
repositories {
    // For snapshots and releases (current)
    maven {
        url = uri("https://maven.pkg.github.com/xtclang/xvm")
        credentials {
            username = System.getenv("GITHUB_USERNAME")
            password = System.getenv("GITHUB_TOKEN") // needs read:packages scope
        }
    }
    // For local development builds
    mavenLocal()
    // Maven Central (coming soon - will eliminate need for GitHub credentials)
    mavenCentral()
}
```

**Future Repository Access:** We plan to publish Maven artifacts to Maven Central (Sonatype OSSRH), which will eliminate the need for GitHub user/token configuration. This will make XDK artifacts available through standard Maven Central without authentication.

**Gradle Plugin Portal:** The XTC language plugin is published to the [Gradle Plugin Portal](https://plugins.gradle.org/) and we're moving toward continuous publication of plugin updates. This means you can use the plugin without any special repository configuration:

```kotlin
// No special repositories needed - fetched from Gradle Plugin Portal
plugins {
    id("org.xtclang.xtc") version "0.4.4-SNAPSHOT"
}
```

The plugin handles all XDK dependencies automatically - most XTC developers won't need the command-line tools.

Manual local build for **any computer** (for advanced users):

* **Bootstrap JVM**: Any Java 8+ to run the Gradle wrapper (just to bootstrap the build)
* **Target JDK**: Gradle toolchain automatically provisions the correct JDK version for building XTC
* **Gradle**: Not required to be pre-installed (project includes Gradle Wrapper)

The build system uses Gradle's toolchain support to automatically download and configure the exact JDK version needed for compilation, so you only need a basic Java installation to get started.

* Use `git` to obtain the XDK:

```
  git clone https://github.com/xtclang/xvm.git
```

* `cd` into the git repo (the directory will contain [these files](https://github.com/xtclang/xvm/))
  and execute the Gradle build:

```
  ./gradlew build
```

**Note:** The Gradle build system uses a toolchain to automatically download and configure the correct Java version if it's not already installed. No manual Java installation is typically required.

## Gradle Build Tasks and XDK Setup

The XVM project uses Gradle for building and distribution management. Understanding the different build tasks and installation options is essential for development and deployment.

### Core Build Tasks

- **`./gradlew build`** - Executes the complete build lifecycle including compilation, testing, and packaging. This creates all XDK components but doesn't install them locally.

- **`./gradlew xdk:installDist`** - Installs the complete XDK distribution to `xdk/build/install/xdk/` with cross-platform shell script launchers (`xec`, `xcc`) ready to use immediately. This is the recommended installation method for development.

### Distribution Tasks

The project provides two main distribution variants:

#### Installation Tasks (creates local installations):

1. **`./gradlew xdk:installDist`** - **Recommended** default installation with cross-platform shell script launchers
    - **Output**: `xdk/build/install/xdk/`
    - **Contents**: Cross-platform script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
    - **Ready to use**: Just add `bin/` to your PATH - no configuration needed

2. **`./gradlew xdk:installWithNativeLaunchersDist`** - Platform-specific native binary launchers
    - **Output**: `xdk/build/install/xdk-native-{os}_{arch}/` (e.g., `xdk-native-linux_amd64/`)
    - **Contents**: Platform-specific native binary launchers (`xec`, `xcc`)
    - **Ready to use**: Just add `bin/` to your PATH - no configuration needed


#### Archive Tasks (creates distributable archives):

1. **`./gradlew xdk:distZip`** / **`./gradlew xdk:distTar`** - **Recommended** default archives with cross-platform script launchers
    - **Output**: `xdk-{version}.zip` / `xdk-{version}.tar.gz`
    - **Contents**: Cross-platform script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
    - **Ready to use**: Extract and add `bin/` to PATH

2. **`./gradlew xdk:withNativeLaunchersDistZip`** / **`./gradlew xdk:withNativeLaunchersDistTar`** - Platform-specific native binary launchers
    - **Output**: `xdk-{version}-native-{os}_{arch}.zip` / `xdk-{version}-native-{os}_{arch}.tar.gz`
    - **Contents**: Platform-specific native launchers (`xec`, `xcc`)
    - **Ready to use**: Extract and add `bin/` to PATH


#### Distribution Differences

**Default Distribution** (`installDist`, `distZip`, `distTar`):
- ✅ Cross-platform shell script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
- ✅ Ready to use immediately - just add `bin/` to your PATH
- ✅ **Recommended for all users**

**Native Launcher Distribution** (`withNativeLaunchers*`):
- ✅ Platform-specific native binary launchers (`xec`, `xcc`)
- ✅ Ready to use immediately - just add `bin/` to your PATH
- ℹ️ **Alternative for specific platform requirements**

The archive tasks produce the same XDK installation content as their corresponding installation tasks, but package them as ZIP and tar.gz files in the `xdk/build/distributions/` directory. These archives are suitable for distribution and deployment to other systems.

**Example archive filenames** (for version `0.4.4-SNAPSHOT`):
- `xdk-0.4.4-SNAPSHOT.zip` - **Default**: Cross-platform script launchers
- `xdk-0.4.4-SNAPSHOT-native-macos_arm64.zip` - macOS ARM64 native launchers

### Quick Development Setup

For developers who want a working XDK installation on their local machine:

1. **Build and install:**
   ```bash
   ./gradlew xdk:installDist
   ```

2. **Add the XDK bin directory to your PATH:**
   ```bash
   # Default installation with script launchers (recommended):
   export PATH="/path/to/xvm/xdk/build/install/xdk/bin:$PATH"
   
   # Alternative: Platform-specific binary launchers (adjust {os}_{arch} as needed):
   export PATH="/path/to/xvm/xdk/build/install/xdk-native-linux_amd64/bin:$PATH"
   ```

3. **Set XDK_HOME environment variable:**
   ```bash
   # Default installation (recommended):
   export XDK_HOME="/path/to/xvm/xdk/build/install/xdk"
   
   # Alternative: Platform-specific binary launchers (adjust {os}_{arch} as needed):
   export XDK_HOME="/path/to/xvm/xdk/build/install/xdk-native-linux_amd64"
   ```

**Tip for Local Development:** You can create a symlink from your home directory to simplify path management:
```bash
# Recommended for development:
ln -sf "/path/to/xvm/xdk/build/install/xdk" ~/xdk-latest
export PATH="~/xdk-latest/bin:$PATH"
export XDK_HOME="~/xdk-latest"
```

This approach shouldn't be controversial since production installations are handled by package managers anyway.

**Important:** The default `installDist` task creates a complete, self-contained XDK installation with proper classpath configuration and ready-to-use launchers. There's no need to run platform-specific configuration scripts like `cfg_macos.sh` - these are legacy approaches that have been superseded by the current Gradle-based distribution system.

### Environment Configuration

Once you have an XDK installation (via any of the install tasks), configure your environment:

- **XDK_HOME**: Set this to the root of your XDK installation directory (e.g., `xdk/build/install/xdk`)
- **PATH**: Add `$XDK_HOME/bin` to your PATH to access `xec` and `xcc` launchers

When `XDK_HOME` is properly set and the launchers are in your PATH, any `xec` (Ecstasy runner) or `xcc` (Ecstasy compiler) command will automatically use the correct XDK libraries and classpath.

### Understanding the Build Artifacts

After running any install task, you'll find:

- **`lib/`** - Core Ecstasy modules (`ecstasy.xtc`, `collections.xtc`, etc.)
- **`javatools/`** - Java-based toolchain (`javatools.jar`, bridge modules)
- **`bin/`** - Executable launchers (if using launcher variants)
    - `xec` - Ecstasy code runner
    - `xcc` - Ecstasy compiler

The difference between `build` and `installDist` is that `build` creates all the necessary artifacts but leaves them in their individual project build directories, while `installDist` assembles everything into a unified, deployable XDK structure ready for use.

## Development

### Git Workflow: Rebase-Only, Linear History

**Core Principle**: We maintain a **strict linear history** in master. **NEVER merge from master to branches** - always rebase instead to avoid merge commits and keep history clean.

*Note: All commands below are supported by IDE Git integrations (IntelliJ, VSCode, etc.). This section uses command-line examples for clarity, but the same operations work in any modern IDE.*

#### Initial Git Configuration

**Required:** Configure git to use rebase by default to prevent accidental merge commits:

```bash
# Check current setting
git config --get pull.rebase

# Set globally (recommended)
git config --global pull.rebase true

# Or set for this repository only
git config --local pull.rebase true
```

The output of the first command should be `true`. If not, run one of the configuration commands above.

#### Branch-Based Development Workflow

**Rule 1**: Always work in feature branches. Direct commits to master are prohibited.
**Rule 2**: Always rebase your branch on top of latest master. Never merge master into your branch.

##### 1. Create and Setup Your Feature Branch

```bash
# Create new branch from latest master
git checkout master
git pull                                    # This will rebase thanks to pull.rebase=true
git checkout -b feature/descriptive-name   # Use descriptive names

# Push and set upstream tracking
git push --set-upstream origin feature/descriptive-name
```

##### 2. Work on Your Changes

```bash
# Make changes and commit frequently
git add .
git commit -m "Add feature X functionality"

# Push your work regularly  
git push
```

##### 3. Keep Your Branch Current (Critical Step)

**Before creating a PR**, and **anytime master moves ahead**, rebase your branch:

```bash
# Fetch latest changes from all remotes
git fetch origin

# Rebase your branch on top of latest master
git rebase origin/master
```

**If conflicts occur during rebase:**
1. **Fix conflicts** in your editor
2. **Test that everything still builds**: `./gradlew build`
3. **Continue the rebase**:
   ```bash
   git add .
   git rebase --continue
   ```
4. **If you get stuck**, abort and ask for help:
   ```bash
   git rebase --abort
   ```

**After successful rebase, force-push** (this is safe and necessary):
```bash
git push --force-with-lease  # Safer than git push -f
```

##### 4. Clean Up Your Commits (Before PR)

Use interactive rebase to create clean, logical commits:

```bash
# Interactive rebase for last n commits
git rebase -i HEAD~3  # Example: last 3 commits

# In the editor, you can:
# - squash: combine commits
# - reword: change commit message  
# - drop: remove commits
# - reorder: change commit order
```

**PR Quality Standards:**
- ✅ Each commit should build and pass tests
- ✅ Commit messages should be descriptive
- ✅ Related changes should be in the same commit
- ✅ Unrelated changes should be in separate commits
- ❌ No "fix typo", "wip", or broken commits

##### 5. Create Pull Request

```bash
# Final push after cleanup
git push --force-with-lease

# Create PR using GitHub CLI (optional)
gh pr create --title "Add feature X" --body "Description of changes"
```

#### What NOT to Do

**❌ NEVER do these things:**
```bash
# DON'T: Merge master into your branch
git merge master                    # This creates merge commits!
git pull origin master             # This might create merge commits!

# DON'T: Work directly on master  
git checkout master
git commit -m "direct change"      # Use branches instead!

# DON'T: Create merge commits
git merge feature/my-branch        # Maintainers handle PR merging
```

**✅ ALWAYS do these instead:**
```bash
# DO: Rebase your branch on master
git rebase origin/master

# DO: Use pull with rebase configured  
git pull                           # Safe with pull.rebase=true

# DO: Work in branches
git checkout -b feature/my-change
```

#### Emergency: Fixing a Broken Rebase

If you get into a confusing state during rebase:

```bash
# 1. Abort the rebase to start over
git rebase --abort

# 2. Make sure you have the latest master
git fetch origin

# 3. Try again with a clean approach
git rebase origin/master

# 4. If still stuck, ask for help on the team chat
```

#### GitHub Branch Protection Settings

**For Repository Administrators**: Configure GitHub to enforce this workflow:

**Settings → Branches → Add rule** for `master` branch:
- ✅ **"Restrict pushes that create merge commits"**
- ✅ **"Require pull request reviews before merging"** 
- ✅ **"Require status checks to pass before merging"**
- ✅ **"Require branches to be up to date before merging"**
- ✅ **"Include administrators"** (enforce rules for everyone)

**GitHub CLI setup** (for administrators):
```bash
# Enable branch protection with merge commit prevention
gh api repos/xtclang/xvm/branches/master/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"checks":[]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews='{"required_approving_review_count":1}' \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false \
  --field block_creations=false \
  --field required_linear_history=true
```

The `required_linear_history=true` setting **blocks merge commits** and enforces the rebase-only workflow.

#### Why This Workflow Matters

**Benefits of linear history:**
- 🔍 **Easy to follow**: `git log --oneline` shows clear chronological development
- 🐛 **Simple debugging**: `git bisect` works reliably to find bugs
- 📈 **Clean commits**: Each commit represents a logical change
- 🚀 **Fast builds**: CI doesn't waste time on merge commit combinations
- 📚 **Clear blame**: `git blame` points to actual changes, not merge commits

**Problems with merge commits:**
- 🕸️ **Complex history**: Hard to understand what actually changed
- 🐛 **Difficult bisection**: Merge commits create confusing paths
- ⚡ **CI overhead**: More commit combinations to test
- 📊 **Unclear metrics**: Commit counts and author statistics get distorted

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

## Understanding Gradle Clean vs Make Clean

**Important:** Gradle's `clean` is fundamentally different from traditional `make clean`:

- **Make clean**: Simply deletes all build outputs ("delete everything" semantic)
- **Gradle clean**: Intelligently removes only outputs that need to be rebuilt, leveraging Gradle's incremental build system and caching

### When to Use Clean

```bash
./gradlew clean
```

Gradle's incremental build system tracks input/output relationships and only rebuilds what has changed. You should **rarely need** to run `clean` because:

- Gradle automatically detects when files need to be rebuilt
- The build cache preserves intermediate outputs for faster rebuilds
- Clean invalidates all cached work, making subsequent builds slower

**Only use `clean` when:**
- You suspect build cache corruption
- You're troubleshooting unusual build behavior
- You're preparing for a completely fresh build for testing

### Composite Build Limitation

**Critical:** Due to our parallel composite build architecture, **never combine `clean` with other build tasks** in a single command:

```bash
# ❌ DON'T DO THIS - will cause build failures
./gradlew clean build

# ✅ DO THIS INSTEAD - run separately
./gradlew clean
./gradlew build
```

The parallel composite build runs subprojects concurrently, and `clean` will interfere with other tasks that are simultaneously creating files, leading to race conditions and build failures.

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
  permissions is required).
* Use `publish` to run both of the above tasks.

*Note*: Some publish tasks may have race conditions due to parallel execution. If you encounter publishing errors:

```bash
./gradlew clean
./gradlew publishTask --no-parallel
```

Remember to run `clean` and the publish task separately due to our composite build architecture.

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

If you would like to contribute to the Ecstasy Project, use the latest development version by building and installing locally:

```
./gradlew xdk:installDist
```

*Note*: this would be done after installing the XDK via `brew`, or through any other installation
utility, depending on your platform. This will overwrite several libraries and files in any
local installation.

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

## Related Repositories

The xtclang organization maintains several repositories supporting the Ecstasy ecosystem:

### Core Platform
- **[platform](https://github.com/xtclang/platform)** - Ecstasy "Platform as a Service" implementation for cloud deployment
- **[examples](https://github.com/xtclang/examples)** - Example web applications demonstrating platform capabilities

### Development Tools
- **[intellij-plugin](https://github.com/xtclang/intellij-plugin)** - IntelliJ IDEA plugin for XTC language support
- **[language-support](https://github.com/xtclang/language-support)** - Language Server Protocol implementation for IDE integration
- **[xtc-app-template](https://github.com/xtclang/xtc-app-template)** - Template repository for creating new XTC applications

### Distribution & Packaging
- **[xdk-release](https://github.com/xtclang/xdk-release)** - Cross-platform XDK distribution and release automation
- **[homebrew-xvm](https://github.com/xtclang/homebrew-xvm)** - Homebrew tap for macOS/Linux XDK installations

### Research & Extensions
- **[jmixin](https://github.com/xtclang/jmixin)** - Java port of Ecstasy mixin functionality for research and comparison

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
