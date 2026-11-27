package org.xtclang.plugin.tasks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import groovy.json.JsonOutput;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Evaluated compiler inputs, without retaining a Project/SourceSet in a task action. */
@DisableCachingByDefault(because = "Exports machine-local absolute paths for the IDE")
public abstract class XtcLspSourceSetTask extends DefaultTask {
    @Input
    public abstract Property<String> getProjectId();

    @Input
    public abstract Property<String> getProjectPath();

    /**
     * The project's path in the whole build tree, such as {@code :xdk:lib-json} for a project of an included build, which
     * names its tasks from the root of a composite build: a bare task name reaches no included build.
     */
    @Input
    public abstract Property<String> getBuildTreePath();

    @Input
    public abstract Property<String> getProjectDirectory();

    @Input
    public abstract Property<String> getBuildFile();

    @Input
    public abstract Property<String> getSourceSetName();

    @Input
    public abstract Property<String> getResourceTask();

    @Input
    public abstract ListProperty<String> getSourceRoots();

    @Input
    public abstract ListProperty<String> getResourceSourceRoots();

    @Input
    public abstract ListProperty<String> getResourceRoots();

    @Input
    public abstract ListProperty<String> getProjectDependencies();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getModulePath();

    /**
     * The method that the project's {@code xtcRun} runs each module it names with, by the module's name; where it names
     * a module more than once, the first time's.
     */
    @Input
    public abstract MapProperty<String, String> getRunModuleMethods();

    /** The arguments that the project's {@code xtcRun} runs each module it names with, by the module's name, as the methods are. */
    @Input
    public abstract MapProperty<String, List<String>> getRunModuleArgs();

    /** The JVM options that the project's {@code xtcRun} runs its modules with. */
    @Input
    public abstract ListProperty<String> getRunJvmArgs();

    /** What of the project's {@code xtcRun} could not be read, and is left out of the model, each with why. */
    @Input
    public abstract ListProperty<String> getRunProblems();

    /** The JVM options that the project's {@code xtcTest} runs its tests with. */
    @Input
    public abstract ListProperty<String> getTestJvmArgs();

    /** What of the project's {@code xtcTest} could not be read, and is left out of the model, each with why. */
    @Input
    public abstract ListProperty<String> getTestProblems();

    /**
     * The version of the XDK that the project compiles and runs with, as its {@code xdkDistribution} or {@code xdk}
     * dependency resolves, or asks for where it does not resolve. Absent, as the XDK's other properties are, for a
     * project without such a dependency, whose core modules, if any, come over its module path.
     */
    @Input
    @Optional
    public abstract Property<String> getXdkVersion();

    /**
     * The directory of that XDK's libraries, {@code ecstasy.xtc} and the other core modules, as a file URI: where the
     * plugin extracts the XDK for the project's {@code compileXtc}, {@code runXtc} and {@code testXtc}.
     */
    @Input
    @Optional
    public abstract Property<String> getXdkLibraries();

    /**
     * The path in the build tree of the project that makes that XDK, for one that a build of the build tree makes, as a
     * composite build's included XDK: the export neither builds nor reads it, so what it holds is not known.
     */
    @Input
    @Optional
    public abstract Property<String> getXdkProject();

    /** Why the export cannot say what that XDK holds, for one whose dependency does not resolve. */
    @Input
    @Optional
    public abstract Property<String> getXdkProblem();

    /**
     * The distribution archive of that XDK, for one from a repository, as far as it can be fetched: the export reads
     * the revision of each module file in it and of its {@code javatools.jar}, without extracting it.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getXdkArchive();

    /**
     * The token that the last export of a problem with that XDK left in {@link #getXdkRetryMarker()}, read as the build
     * is configured: a new one makes the next import configure the build again, and resolve the XDK anew, rather than
     * reuse a configuration cache entry that kept the problem, and runs this task again.
     */
    @Input
    public abstract Property<String> getXdkRetry();

    /** Where the export of a problem with that XDK leaves a new token ({@link #getXdkRetry()}); not an output. */
    @Internal
    public abstract RegularFileProperty getXdkRetryMarker();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void exportModel() throws IOException {
        final var model = new LinkedHashMap<String, Object>();
        model.put("projectId", getProjectId().get());
        model.put("projectPath", getProjectPath().get());
        model.put("buildTreePath", getBuildTreePath().get());
        model.put("projectDirectory", getProjectDirectory().get());
        model.put("buildFile", getBuildFile().get());
        model.put("sourceSet", getSourceSetName().get());
        model.put("sourceRoots", getSourceRoots().get());
        model.put("sourceFiles", uris(getSourceFiles().getFiles().stream().toList()));
        final var roots = getSourceRoots().get().stream().map(URI::create).map(Path::of).toList();
        model.put("moduleRoots", uris(getSourceFiles().getFiles().stream()
            .filter(file -> roots.contains(file.toPath().getParent())).toList()));
        model.put("resourceSourceRoots", getResourceSourceRoots().get());
        model.put("resourceRoots", getResourceRoots().get());
        model.put("resourceTask", getResourceTask().get());
        model.put("projectDependencies", getProjectDependencies().get());
        model.put("modulePath", uris(getModulePath().getFiles().stream().toList()));
        // The project's run settings, which apply to the modules of each of its source sets alike.
        final var arguments = getRunModuleArgs().get();
        final var modules = new LinkedHashMap<String, Object>();
        getRunModuleMethods().get().forEach((name, method) -> {
            final var module = new LinkedHashMap<String, Object>();
            module.put("methodName", method);
            module.put("moduleArgs", arguments.getOrDefault(name, List.of()));
            modules.put(name, module);
        });
        final var run = new LinkedHashMap<String, Object>();
        run.put("jvmArgs", getRunJvmArgs().get());
        run.put("modules", modules);
        run.put("problems", getRunProblems().get());
        model.put("run", run);
        final var test = new LinkedHashMap<String, Object>();
        test.put("jvmArgs", getTestJvmArgs().get());
        test.put("problems", getTestProblems().get());
        model.put("test", test);
        // the project's XDK, which its source sets share, as run and test are; none without an XDK dependency, and
        // no version for a file dependency
        if (getXdkLibraries().isPresent()) {
            final var xdk = new LinkedHashMap<String, Object>();
            if (getXdkVersion().isPresent()) {
                xdk.put("version", getXdkVersion().get());
            }
            xdk.put("libraries", getXdkLibraries().get());
            if (getXdkProject().isPresent()) {
                xdk.put("project", getXdkProject().get());
            } else if (getXdkProblem().isPresent()) {
                xdk.put("problem", getXdkProblem().get());
            } else {
                xdk.putAll(contents(getXdkArchive().getFiles()));
            }
            model.put("xdk", xdk);
            if (xdk.containsKey("problem")) {
                final var marker = getXdkRetryMarker().get().getAsFile().toPath();
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, UUID.randomUUID() + "\n");
            }
        }
        // replaced whole, as the model is: another build exporting the same project at once, as two editors' imports
        // of one folder, would otherwise read the report part written
        XtcLspModelTask.writeAtomically(getReport().get().getAsFile().toPath(),
            JsonOutput.prettyPrint(JsonOutput.toJson(model)) + '\n');
    }

    /**
     * What the XDK distribution among {@code archives} holds: the SHA-256 of each of its module files, in order, as
     * {@code modules}, and the revision of its {@code javatools.jar}, the compiler and runtime, as {@code javatools}
     * ({@link #javatoolsRevision}); or, as a {@code problem}, why it cannot say, as for an archive that the import could
     * not fetch.
     */
    static Map<String, Object> contents(final Set<File> archives) {
        final var archive = archives.stream().filter(file -> file.getName().endsWith(".zip")).findFirst();
        if (archive.isEmpty()) {
            return Map.of("problem", "the import could not fetch its distribution");
        }
        try (var zip = new ZipFile(archive.get())) {
            final var modules = new ArrayList<String>();
            final var contents = new LinkedHashMap<String, Object>();
            for (final var entry : Collections.list(zip.entries())) {
                final var name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                if (name.endsWith(".xtc")) {
                    modules.add(sha256(bytes(zip, entry)));
                } else if (name.equals("javatools.jar")) {
                    contents.put("javatools", javatoolsRevision(bytes(zip, entry)));
                }
            }
            contents.put("modules", modules.stream().sorted().toList());
            return contents;
        } catch (IOException failure) {
            return Map.of("problem", "the import could not read its distribution (" + failure.getMessage() + ')');
        }
    }

    /**
     * The revision of the javatools jar whose bytes are {@code jar}, which tells two builds of the compiler and runtime
     * apart: the SHA-256 of a line {@code <name>=<SHA-256>} for each file in it, in name order, with that of
     * {@code build-info.properties} taken without its blank lines and its {@code git.} lines. Those name the commit and
     * branch the jar was built at, which change with every commit, whatever it changes, as javatools' own build leaves
     * them out of its classpath's normalization; the language server's build records its bundled jar's revision alike.
     */
    public static String javatoolsRevision(final byte[] jar) throws IOException {
        final var revisions = new TreeMap<String, String>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (!entry.isDirectory()) {
                    final var bytes = input.readAllBytes();
                    revisions.put(entry.getName(), sha256(entry.getName().equals("build-info.properties") ? withoutGit(bytes) : bytes));
                }
            }
        }
        return sha256(revisions.entrySet().stream().map(each -> each.getKey() + '=' + each.getValue() + '\n')
            .collect(Collectors.joining()).getBytes(StandardCharsets.UTF_8));
    }

    /** The lines of {@code properties} but the blank ones and the {@code git.} ones, which name a build's commit and branch. */
    private static byte[] withoutGit(final byte[] properties) {
        return new String(properties, StandardCharsets.ISO_8859_1).lines().filter(line -> !line.isBlank() && !line.startsWith("git."))
            .collect(Collectors.joining("\n")).getBytes(StandardCharsets.ISO_8859_1);
    }

    /** The bytes of {@code entry} of {@code zip}. */
    private static byte[] bytes(final ZipFile zip, final ZipEntry entry) throws IOException {
        try (var input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    /** The SHA-256 of {@code bytes}, in hex. */
    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    public static List<String> uris(final List<File> files) {
        return files.stream().map(file -> file.toURI().toString()).toList();
    }
}
