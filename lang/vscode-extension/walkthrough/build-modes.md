# Ecstasy build modes

| Mode | Build, Rebuild and Run use |
|------|----------------------------|
| **Ecstasy compiler (embedded)**, the default | The language server's compiler and its bundled XDK, with nothing to sync, also in a folder without a Gradle build. It needs the compiler language adapter. In a folder with a Gradle build, of the build's own settings only `xtcRun`'s method, arguments and JVM options and `xtcTest`'s JVM options apply, once its Gradle model is imported. A folder whose Gradle build has another XDK than the bundled one, as its imported model says, builds and runs with Gradle whatever the mode, as one whose modules replace bundled libraries does; the picker and the status bar say why. |
| **Gradle** | The folder's Gradle build, with all of its settings, as on the command line. |
| **Automatic** | Gradle when the folder has a Gradle build, and the embedded compiler otherwise. |

The next build or run uses the mode you choose, with no restart. The setting is `xtc.build.mode`.
