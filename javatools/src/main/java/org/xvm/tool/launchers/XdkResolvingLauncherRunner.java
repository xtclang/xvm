package org.xvm.tool.launchers;

import org.xvm.tool.Runner;
import org.xvm.tool.Launcher.LauncherException;

import java.io.IOException;

public class XdkResolvingLauncherRunner extends org.xvm.tool.launchers.XdkResolvingLauncher {
    protected XdkResolvingLauncherRunner(final String[] args) {
        super(args);
    }

    public static void main(final String[] args) throws LauncherException, IOException {
        Runner.launch(new org.xvm.tool.launchers.XdkResolvingLauncher(args).resolveXdkBootstrapArgs());
    }
}
