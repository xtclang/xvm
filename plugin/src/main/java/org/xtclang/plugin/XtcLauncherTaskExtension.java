package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("unused")
public interface XtcLauncherTaskExtension {
    // TODO: Increase granularity for this later, so that we can control individual module execution fork policies from the run tasks?
    //   (the easiest solution, would likely be to just add setters that manipulate the extension values in the tasks, or maybe resetting them)
    // TODO: This may be a current source of confusion - allowing a task property getter, and changing its value, will lead to the entire extension for
    //   all tasks of that kind being changed, and that is something we should definitely fix.
    // Todo Turn these into inputs directly?
    Property<Boolean> getFork();

    // The "--version" argument, for both xcc and xec, shows the version before launch and translates to argument., "--set-version" is different and xcc only, "stamp version".
    Property<Boolean> getShowVersion();

    Property<Boolean> getUseNativeLauncher();

    Property<Boolean> getVerbose();

    ListProperty<String> getJvmArgs();

    Property<InputStream> getStdin();

    Property<OutputStream> getStdout();

    Property<OutputStream> getStderr();

    void jvmArgs(String... args);

    void jvmArgs(Iterable<? extends String> args);

    void jvmArgs(Provider<? extends Iterable<? extends String>> provider);

    void setJvmArgs(Iterable<? extends String> elements);

    void setJvmArgs(Provider<? extends Iterable<? extends String>> provider);
}
