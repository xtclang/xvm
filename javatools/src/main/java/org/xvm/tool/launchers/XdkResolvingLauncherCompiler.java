package org.xvm.tool.launchers;

import org.xvm.tool.Compiler;
import org.xvm.tool.Launcher.LauncherException;

import java.io.IOException;

public class XdkResolvingLauncherCompiler extends org.xvm.tool.launchers.XdkResolvingLauncher {
    protected XdkResolvingLauncherCompiler(final String[] args) {
        super(args);
    }

    public static void main(final String[] args) throws LauncherException, IOException {
        Compiler.launch(new org.xvm.tool.launchers.XdkResolvingLauncher(args).resolveXdkBootstrapArgs());
    }
}
