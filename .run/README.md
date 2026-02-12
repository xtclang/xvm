# Shared Run Configurations

IntelliJ automatically discovers `*.run.xml` files in this directory and adds them
to the run configuration dropdown in the toolbar. These are shared via version control
so all contributors get them out of the box.

## IntelliJ Plugin Development

| Configuration | What It Does |
|---|---|
| **Run Plugin in IDE** | Launches a development IntelliJ IDEA instance with the XTC plugin installed in its sandbox. Uses Gradle task `lang:runIntellijPlugin`. Supports debugging (Shift+F9). |
| **Run Plugin Tests** | Runs the IntelliJ plugin test suite (unit tests + platform tests if configured). Uses Gradle task `lang:intellij-plugin:test`. |
| **Build Plugin ZIP** | Builds a distributable plugin ZIP archive at `lang/intellij-plugin/build/distributions/`. Uses Gradle task `lang:intellij-plugin:buildPlugin`. The ZIP can be installed in any IntelliJ IDE via Settings > Plugins > Install from Disk. |

## XTC Compiler / Runtime

| Configuration | What It Does |
|---|---|
| **xtc build** | Compiles an XTC module using the XTC compiler (`org.xvm.tool.Launcher build`). Defaults to `manualTests/src/main/x/FizzBuzz.x`. Runs as a Java application in the `javatools` module. |
| **xtc run** | Compiles and runs an XTC module (`org.xvm.tool.Launcher run`). Defaults to FizzBuzz.x — outputs FizzBuzz to the console. Useful for quick end-to-end validation of compiler + runtime. |

### Prerequisites for `xtc` Configurations

The `xtc build/run` configurations require the XDK standard library modules to be
installed. Run once before first use:

```bash
./gradlew xdk:installDist
```

This populates `xdk/build/install/xdk/lib/` with the compiled standard library modules
(ecstasy.xtc, json.xtc, collections.xtc, etc.) and `xdk/build/install/xdk/javatools/`
with the runtime bridge modules. These rarely change — you only need to re-run
`installDist` after modifying the standard library or javatools.

### Customizing the Target Module

To compile/run a different `.x` file, duplicate the run configuration and change the
program arguments. The format is:

```
build|run <path-to-file.x> -L xdk/build/install/xdk/lib -L xdk/build/install/xdk/javatools/javatools_turtle.xtc -L xdk/build/install/xdk/javatools/javatools_bridge.xtc
```

Good candidates in `manualTests/src/main/x/`:
- `TestSimple.x` — minimal module with Console injection and arithmetic
- `FizzBuzz.x` — pattern matching with switch expressions
- `collections.x` — exercises the collections library
- `numbers.x` — numeric type operations
- `lambda.x` — lambda and closure tests