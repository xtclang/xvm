# XTC Command-Line Interface

The `xtc` command is the unified command-line tool for working with Ecstasy projects.

## Commands

| Command | Description |
|---------|-------------|
| `xtc init` | Create a new XTC project |
| `xtc build` | Compile Ecstasy source files (alias: `xcc`) |
| `xtc run` | Execute an Ecstasy module (alias: `xec`) |
| `xtc test` | Run tests in an Ecstasy module using xunit |
| `xtc disass` | Disassemble a compiled Ecstasy module |

## xtc init - Project Creation

Creates a new XTC project with the standard directory structure and build files.

### Usage

```bash
xtc init <project-name> [options]
```

### Options

| Option | Description |
|--------|-------------|
| `-t, --type <type>` | Project type: `application` (default), `library`, or `service` |
| `-m, --multi-module` | Create a multi-module project structure |
| `-v, --verbose` | Enable verbose output |
| `-h, --help` | Display help message |

### Examples

```bash
# Create an application project (default)
xtc init myapp

# Create a library project
xtc init mylib --type=library
xtc init mylib -t lib

# Create a service project
xtc init mysvc --type=service

# Create a multi-module project
xtc init myproject --multi-module
xtc init myproject -m

# Combine options
xtc init myproject --type=service --multi-module
```

## Project Types

### APPLICATION

An executable module with an entry point. This is the default project type.

**Use when:** You want to create a runnable program.

**Generated structure:**
```
myapp/
├── build.gradle.kts      # Includes xtcRun configuration
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── gradlew / gradlew.bat
└── src/main/x/
    └── myapp.x           # Module with void run() entry point
```

**Generated module source:**
```ecstasy
module myapp {
    void run() {
        @Inject Console console;
        console.print("Hello from myapp!");
    }
}
```

**Build commands:**
```bash
./gradlew build    # Compile
./gradlew run      # Execute
```

### LIBRARY

A reusable module that exports types and services for other modules to import. Has no entry point.

**Use when:** You want to create shared functionality that other projects will depend on.

**Generated structure:**
```
mylib/
├── build.gradle.kts      # No xtcRun configuration
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── gradlew / gradlew.bat
└── src/main/x/
    └── mylib.x           # Module with exported service
```

**Generated module source:**
```ecstasy
module mylib {
    /**
     * A greeting service.
     */
    service Greeter {
        String greet(String name) {
            return $"Hello, {name}!";
        }
    }
}
```

**Build commands:**
```bash
./gradlew build    # Compile (produces .xtc file)
# No 'run' task - libraries are not executable
```

### SERVICE

Similar to APPLICATION but semantically intended for background/daemon processes.

**Use when:** You want to create a long-running service or daemon.

**Generated structure:** Same as APPLICATION.

**Generated module source:**
```ecstasy
module mysvc {
    void run() {
        @Inject Console console;
        console.print("mysvc service starting...");

        // TODO: Add your service logic here
    }
}
```

**Build commands:**
```bash
./gradlew build    # Compile
./gradlew run      # Execute
```

## Multi-Module Projects

The `--multi-module` flag creates a project with multiple subprojects that can depend on each other.

**Generated structure:**
```
myproject/
├── settings.gradle.kts   # Includes app and lib subprojects
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── gradlew / gradlew.bat
├── app/
│   ├── build.gradle.kts
│   └── src/main/x/
│       └── app.x         # Application that imports lib
└── lib/
    ├── build.gradle.kts
    └── src/main/x/
        └── lib.x         # Library module
```

Note: Multi-module projects do not have a root `build.gradle.kts`. Each subproject is self-contained with its own build file, and `settings.gradle.kts` defines the project structure. This is the recommended modern Gradle style.

**app.x imports lib:**
```ecstasy
module app {
    package lib import lib;

    void run() {
        @Inject Console console;

        // Use the Greeter service from lib
        lib.Greeter greeter = new lib.Greeter();
        console.print(greeter.greet("World"));
    }
}
```

**Build commands:**
```bash
./gradlew build    # Build all subprojects
./gradlew run      # Run the app module
./gradlew :app:build   # Build only app
./gradlew :lib:build   # Build only lib
```

## xtc build - Compilation

Compiles Ecstasy source files into `.xtc` module files.

### Usage

```bash
xtc build [options] <source_files>
```

### Options

| Option | Description |
|--------|-------------|
| `-L <path>` | Module path for dependencies |
| `-o <file>` | Output file or directory |
| `-r <path>` | Resource path |
| `--rebuild` | Force rebuild |
| `--strict` | Treat warnings as errors |
| `--nowarn` | Suppress warnings |
| `-v, --verbose` | Verbose output |

### Examples

```bash
xtc build src/main/x/myapp.x
xtc build -L lib/ -o build/ src/main/x/myapp.x
xtc build --rebuild myapp.x
```

## xtc run - Execution

Executes a compiled Ecstasy module.

### Usage

```bash
xtc run [options] <module> [args...]
```

### Options

| Option | Description |
|--------|-------------|
| `-L <path>` | Module path |
| `-M <method>` | Entry method name (default: `run`) |
| `-I <name=value>` | Injection values |
| `-J, --jit` | Enable JIT compiler |
| `--no-recompile` | Disable automatic recompilation |

### Examples

```bash
xtc run myapp.xtc
xtc run -L lib/ myapp.xtc
xtc run -M main myapp.xtc
xtc run -I config=prod myapp.xtc arg1 arg2
```

## xtc test - Testing

Runs tests in an Ecstasy module using the xunit framework.

### Usage

```bash
xtc test [options] <module>
```

### Options

| Option | Description |
|--------|-------------|
| `-c, --test-class <class>` | Run tests in specific class |
| `-g, --test-group <group>` | Run tests with specific @Test group |
| `-p, --test-package <pkg>` | Run tests in specific package |
| `-t, --test-method <method>` | Run specific test method |
| `--xunit-out <dir>` | Output directory for test results |

### Examples

```bash
xtc test myapp.xtc
xtc test -c MyTests myapp.xtc
xtc test --test-group integration myapp.xtc
```

## xtc disass - Disassembly

Disassembles a compiled `.xtc` module to inspect its contents.

### Usage

```bash
xtc disass [options] <module_file>
```

### Options

| Option | Description |
|--------|-------------|
| `--files` | List embedded files in the module |
| `--findfile <file>` | Search for a specific file |

### Examples

```bash
xtc disass myapp.xtc
xtc disass --files myapp.xtc
xtc disass --findfile config.json myapp.xtc
```

## IntelliJ IDEA Integration

The XTC IntelliJ plugin provides a **New Project** wizard that offers the same project types and options as `xtc init`:

1. **File → New → Project**
2. Select **XTC** from the generators list
3. Configure:
   - **Project name** and **Location**
   - **Project type**: Application, Library, or Service
   - **Multi-module project** checkbox

The wizard uses the same `XtcProjectCreator` as `xtc init`, ensuring identical project structures whether created from the command line or IDE.
