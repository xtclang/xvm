package org.xvm.tool;

import org.xvm.tool.Launcher.LauncherException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher that figures out if it's part of an XDK installation. If this
 * is not the case, it fails. If it is, it should resolve the javatools xtc modules
 * and the xdk modules from its self-discovered location and prepend those as
 * arguments to the "real" launcher. This makes it possible to replace the
 * native binary launch exes with auto generated application plugin scripts.
 * These, in turn, can be turned into binary launchers, should we want to.
 * This frees up a lot of rather complex binary handling in our bits, as well
 * as symlinks that are required for this location check to work in the current
 * solution.
 * <p>
 * The logic implemented in this file is identical to the logic used by our
 * legacy binary launchers for identifying and resolving if we are executing
 * from and XTC installation, and the locations of its core libraries required
 * to run are present.
 * <p>
 * The application plugin launch config will look something like this:
 *   + applicationDefaultJvmArgs = "-ea" (as in the current native launchers)
 *   + applicationDistribution = the xdk/build/install directory, or rather, the outputs of installDist
 *   + applicationName = "xcc" or "xec", we create two of them.
 *   + mainClass = XdkCompilerLauncher or XdkRunnerLauncher
 *   + mainModule = N/A
 */
public class XdkResolvingLauncher {
    private static final String PROTO_JAR = "javatools.jar";
    private static final String PROTO_LIB = "javatools_bridge.xtc";
    private static final String MACK_LIB = "javatools_turtle.xtc";
    private static final String XDK_MODULE_DIR = "lib";

    public static class XdkResolvingLauncherCompiler extends XdkResolvingLauncher {
        protected XdkResolvingLauncherCompiler(final String[] args) {
            super(args);
        }

        public static void main(final String[] args) throws LauncherException, IOException {
            Compiler.launch(new XdkResolvingLauncher(args).resolveXdkBootstrapArgs());
        }
    }

    public static class XdkResolvingLauncherRunner extends XdkResolvingLauncher {
        protected XdkResolvingLauncherRunner(final String[] args) {
            super(args);
        }

        public static void main(final String[] args) throws LauncherException, IOException {
            Runner.launch(new XdkResolvingLauncher(args).resolveXdkBootstrapArgs());
        }
    }

    private final List<String> args;

    protected XdkResolvingLauncher(final String[] args) {
        this.args = List.of(args);
    }

    protected String[] resolveXdkBootstrapArgs() throws IOException {
        final File protoJar = resolveLocation();
        if (protoJar == null) {
            throw new IOException("XdkLauncher is not part of an XDK installation (location: " + getCodeLocation() + ')');
        }

        // We should have picked up a javatools in /<xdk root>/libexec/javatools/javatools.jar
        final var protoDir = checkDir(protoJar.getParentFile());
        final var libExecDir = checkDir(protoDir.getParentFile());
        final var libDir = checkDir(new File(libExecDir, XDK_MODULE_DIR));
        final var mackFile = checkFile(new File(protoDir, MACK_LIB));
        final var bridgeFile = checkFile(new File(protoDir, PROTO_LIB));
        final List<String> allArgs = new ArrayList<>(
            List.of(
                "-L", libDir.getAbsolutePath(),
                "-L", mackFile.getAbsolutePath(),
                "-L", bridgeFile.getAbsolutePath())
        );
        allArgs.addAll(args);
        return allArgs.toArray(new String[args.size()]);
    }

    private static URL getCodeLocation() {
        return XdkResolvingLauncher.class.getProtectionDomain().getCodeSource().getLocation();
    }

    private static File resolveLocation() {
        final File file = new File(getCodeLocation().getFile());
        System.err.println("  proto: " + PROTO_JAR.equals(file.getName()) + " and " + isValidXdkInstallationFile(file, false));
        if (PROTO_JAR.equals(file.getName()) && isValidXdkInstallationFile(file, false) != null) {
            return file;
        }
        return null;
    }

    private File checkDir(final File file) throws IOException {
        return checkFile(file, true);
    }

    private File checkFile(final File file) throws IOException {
        return checkFile(file, false);
    }

    private File checkFile(final File file, final boolean shouldBeDir) throws IOException {
        if (isValidXdkInstallationFile(file, shouldBeDir) != null) {
            return file;
        }
        throw new IOException("File, dir, or execution context for " + getClass().getSimpleName() + " does not appear to be inside an XDK installation (file: " + file + ')');
    }

    private static File isValidXdkInstallationFile(final File file, final boolean shouldBeDir) {
        final boolean isValid = file != null && file.exists() && file.canRead() && file.isFile() != shouldBeDir;
        return isValid ? file : null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + args;
    }
}
