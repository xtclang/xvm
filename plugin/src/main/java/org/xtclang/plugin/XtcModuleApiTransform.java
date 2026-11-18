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
 * Turns the XTC modules that a compile depends on into their APIs, as javatools' {@code org.xvm.asm.ModuleApi}
 * defines them: what a module that imports them compiles against. A compile task takes the APIs as its input in
 * place of the modules, so a change to a module that leaves its API the same, such as one inside a method body, does
 * not compile the modules that import it again.
 *
 * <p>The input is a directory of modules, as a project's compile task produces it, or a module file; the output is a
 * directory with the API of each module under the module's file name. With a javatools that has no
 * {@code ModuleApi}, the output is a copy of each module, which a compile then depends on as a whole, as before.
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

    private static final String MODULE_API_CLASS = "org.xvm.asm.ModuleApi";

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
        if (runtime == null) {
            throw failure("Found no javatools.jar to read the APIs of the modules in '{}' with.", input);
        }
        try (var loader = new URLClassLoader(urls(runtime.classpath()), ClassLoader.getPlatformClassLoader())) {
            final Method write = moduleApiWriter(loader);
            for (final Path module : modules) {
                final Path api = output.toPath().resolve(module.getFileName().toString());
                if (write == null) {
                    Files.copy(module, api);
                } else {
                    write.invoke(null, module, api);
                }
            }
        } catch (final IOException | IllegalAccessException e) {
            throw failure(e, "Failed to write the APIs of the modules in '{}'.", input);
        } catch (final InvocationTargetException e) {
            throw failure(e.getCause(), "Failed to write the APIs of the modules in '{}'.", input);
        }
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
     * @return {@code ModuleApi.write(Path, Path)}, or null if the javatools has none
     */
    private static Method moduleApiWriter(final ClassLoader loader) {
        try {
            return loader.loadClass(MODULE_API_CLASS).getMethod("write", Path.class, Path.class);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            LOG.info("[plugin] This javatools has no {}: compiles depend on whole modules.", MODULE_API_CLASS);
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
