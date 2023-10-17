package org.xvm.plugin;

import java.io.File;
import java.util.*;

public class CommandLine {
    private final List<String> args;

    CommandLine() {
        this.args = new ArrayList<>();
    }

    private CommandLine(final List<String> args) {
        this.args = new ArrayList<>(args);
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
        return new CommandLine(args);
    }

    @Override
    public String toString() {
        return toString(null, null, Collections.emptyList());
    }

    public String toString(final Class<?> clazz, final File javaTools, final List<String> jvmArgs) {
        if (isEmpty()) {
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
        for (final var arg : toList()) {
            sb.append(' ').append(arg.trim());
        }

        return sb.toString();
    }
}
