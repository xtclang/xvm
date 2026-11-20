package org.xtclang.plugin;

import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginUtils.failure;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Turns the XTC modules that a compile depends on into their APIs, as javatools'
 * {@code org.xvm.asm.FileStructure.writeApi} defines them: what a module that imports them compiles against. A compile
 * task takes the APIs as its input in place of the modules, so a change to a module that leaves its API the same, such
 * as one inside a method body, does not compile the modules that import it again.
 *
 * <p>The input is a directory of modules, as a project's compile task produces it, or a module file; the output is a
 * directory with the API of each module, named after the module with the extension {@code .api}: the API files
 * are only compared, never loaded, and are not valid module files. Without a javatools that has
 * {@code FileStructure.writeApi}, or for a file that cannot be read as a module, the output is a copy of the file,
 * which a compile then depends on as a whole, as before.
 *
 * <p>Javatools is loaded for each transform and the class loader closed after it: one kept open across builds would
 * read a jar that the build has since rewritten in place.
 */
@CacheableTransform
public abstract class XtcModuleApiTransform implements TransformAction<XtcModuleApiTransform.Parameters> {
    /**
     * The artifact type of a module's API.
     */
    public static final String ARTIFACT_TYPE = "xtc-api";

    private static final String API_WRITER_CLASS = "org.xvm.asm.FileStructure";

    private static final String API_WRITER_METHOD = "writeApi";

    private static final String API_FILE_EXTENSION = "api";

    private static final Logger LOG = Logging.getLogger(XtcModuleApiTransform.class);

    public interface Parameters extends TransformParameters {
        /**
         * @return the jars to find javatools.jar among, as the compile task finds it
         */
        @Classpath
        ConfigurableFileCollection getJavaTools();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getModules();

    @Override
    public void transform(final TransformOutputs outputs) {
        final File input  = getModules().get().getAsFile();
        final File output = outputs.dir(input.getName());
        final List<Path> modules = modules(input);
        if (modules.isEmpty()) {
            return;
        }

        final var runtime = XtcJavaToolsRuntime.resolveRuntimeFromFiles("module API",
                getParameters().getJavaTools().getFiles().stream().filter(File::isFile).toList(), null);
        try {
            if (runtime == null) {
                LOG.info("[plugin] Found no javatools.jar to read the APIs of the modules in '{}' with: compiles depend on the whole modules.", input);
                for (final Path module : modules) {
                    copy(module, output);
                }
                return;
            }
            try (var loader = new URLClassLoader(urls(runtime.classpath()), ClassLoader.getPlatformClassLoader())) {
                final Method write = moduleApiWriter(loader);
                for (final Path module : modules) {
                    if (write == null) {
                        copy(module, output);
                    } else {
                        writeApi(write, module, output);
                    }
                }
            }
        } catch (final IOException | IllegalAccessException e) {
            throw failure(e, "Failed to write the APIs of the modules in '{}'.", input);
        }
    }

    /**
     * Write the API of a module, or a copy of the module if it cannot be read as one: a compile then depends on the
     * whole file, as it did before, and reports what is wrong with it itself.
     */
    private static void writeApi(final Method write, final Path module, final File output)
            throws IOException, IllegalAccessException {
        try {
            write.invoke(null, module, apiFile(module, output));
        } catch (final InvocationTargetException e) {
            LOG.info("[plugin] Cannot read the API of '{}' ({}): compiles depend on the whole module.", module, e.getCause().toString());
            copy(module, output);
        } catch (final LinkageError e) {
            // javatools cannot run in this JVM, such as one older than the javatools was built for
            LOG.info("[plugin] Cannot run javatools in this JVM to read the API of '{}' ({}): compiles depend on the whole module.", module, e.toString());
            copy(module, output);
        }
    }

    private static void copy(final Path module, final File output) throws IOException {
        Files.copy(module, apiFile(module, output), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * @return the file for the API of a module: its name with {@value #API_FILE_EXTENSION} in place of the module
     *         file's extension, so that it is not taken for a module
     */
    private static Path apiFile(final Path module, final File output) {
        final String name = module.getFileName().toString();
        final String base = name.endsWith('.' + XTC_MODULE_FILE_EXTENSION)
                ? name.substring(0, name.length() - XTC_MODULE_FILE_EXTENSION.length() - 1)
                : name;
        return output.toPath().resolve(base + '.' + API_FILE_EXTENSION);
    }

    /**
     * @return the module files of the input, in name order; none if it does not exist
     */
    private static List<Path> modules(final File input) {
        if (input.isFile()) {
            return List.of(input.toPath());
        }
        final File[] files = input.listFiles((dir, name) -> name.endsWith('.' + XTC_MODULE_FILE_EXTENSION));
        return files == null ? List.of() : Stream.of(files).sorted().map(File::toPath).toList();
    }

    /**
     * @return {@code FileStructure.writeApi(Path, Path)}, or null if the javatools has none, or cannot run in this JVM,
     *         such as one older than the javatools was built for: the compiles themselves run javatools in their own
     *         JVM
     */
    private static Method moduleApiWriter(final ClassLoader loader) {
        try {
            return loader.loadClass(API_WRITER_CLASS).getMethod(API_WRITER_METHOD, Path.class, Path.class);
        } catch (final ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            LOG.info("[plugin] Cannot use {}.{} from this javatools ({}): compiles depend on whole modules.", API_WRITER_CLASS, API_WRITER_METHOD, e.toString());
            return null;
        }
    }

    private static URL[] urls(final List<File> classpath) {
        return classpath.stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (final MalformedURLException e) {
                throw failure(e, "Invalid javatools classpath entry '{}'.", file);
            }
        }).toArray(URL[]::new);
    }
}
