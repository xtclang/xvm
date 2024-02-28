package org.xtclang.plugin.launchers;

import java.io.File;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CommandLine {
    private final List<String> args;
    private final List<String> jvmArgs;
    private final String mainClass;

    public CommandLine(final String mainClass, final List<String> jvmArgs) {
        this(mainClass, jvmArgs, Collections.emptyList());
    }

    CommandLine(final String mainClass, final List<String> jvmArgs, final List<String> args) {
        this.mainClass = mainClass;
        this.jvmArgs = Collections.unmodifiableList(jvmArgs);
        this.args = new ArrayList<>(args);
    }

    public String getMainClassName() {
        return mainClass;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public String getIdentifier() {
        final int dot = mainClass.lastIndexOf('.');
        return (dot == -1 || dot == mainClass.length() - 1) ? mainClass : mainClass.substring(dot + 1);
    }

    /**
     * A boolean argument is not "false" or "true". It's true if it's defined, and if it's false,
     * it's not on the command line. For example "-nowarn" means "turn off all warnings", but
     * not "-nowarn true" or "-nowarn false"
     */
    public void addBoolean(final String name, final boolean value) {
        if (value) {
            args.add(name);
        }
    }

    public <T> void add(final String name, final T value) {
        args.add(name);
        args.add(Objects.requireNonNull(value).toString());
    }

    public void addRepeated(final String name, final Collection<File> values) {
        for (final File arg : values) {
            add(name, arg.getAbsolutePath());
        }
    }

    public void addRaw(final String arg) {
        args.add(arg);
    }

    public void addRaw(final List<String> argList) {
        args.addAll(argList);
    }

    public int size() {
        return args.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public List<String> toList() {
        return Collections.unmodifiableList(args); // TODO probably do all allocation in the plugin thru Gradle.Project.objectFactories.
    }

    public CommandLine copy() {
        return new CommandLine(mainClass, jvmArgs, args);
    }

    @Override
    public String toString() {
        return toString(mainClass, jvmArgs, args, null);
    }

    public String toString(final File javaTools) {
        return toString(mainClass, jvmArgs, args, javaTools);
    }

    public static String toString(final String clazz, final List<String> jvmArgs, final List<String> args, final File javaTools) {
        if (args.isEmpty()) {
            return "[no arguments]";
        }

        final StringBuilder sb = new StringBuilder("java");
        for (final var arg : jvmArgs) {
            sb.append(' ').append(arg.trim());
        }
        if (javaTools != null) {
            sb.append(" -cp ").append(javaTools.getAbsolutePath());
        }
        if (clazz != null) {
            sb.append(' ').append(clazz);
        }
        for (final var arg : args) {
            sb.append(' ').append(arg.trim());
        }

        return sb.toString();
    }
}
