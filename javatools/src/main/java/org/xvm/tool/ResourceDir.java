package org.xvm.tool;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.xvm.compiler.ast.FileExpression;

import static org.xvm.util.Handy.parentOf;

/**
 * Represents a directory that contains resources, based on the contents of one or more directories
 * and/or files.
 */
public class ResourceDir {
    /**
     * A resource entry - either a file or a subdirectory.
     */
    public sealed interface ResourceEntry {
        record FileEntry(File file) implements ResourceEntry {}
        record DirEntry(ResourceDir dir) implements ResourceEntry {}
    }

    /**
     * A ResourceDir that represents an empty set of resources.
     */
    static final ResourceDir NO_RESOURCES = new ResourceDir(List.of());

    private final ResourceDir parent;
    private final String name;
    private final List<File> resourcePath;

    @SuppressWarnings("unused")
    public ResourceDir(final File resourceLoc) {
        this(List.of(resourceLoc));
    }

    public ResourceDir(final List<File> resourcePath) {
        this(null, "", resourcePath);
    }

    protected ResourceDir(final ResourceDir parent, final String name, final List<File> resourcePath) {
        for (final var file : resourcePath) {
            Objects.requireNonNull(file, "Resource location must not be null");
            if (!file.exists()) {
                throw new IllegalArgumentException("Resource location \"" + file + "\" does not exist");
            }
        }
        this.parent       = parent;
        this.name         = name;
        this.resourcePath = List.copyOf(resourcePath);
    }

    /**
     * Given a source file, create the default ResourceDir that corresponds to that source file.
     */
    public static ResourceDir forSource(final File sourceFile, final boolean deduce) {
        if (sourceFile == null) {
            return NO_RESOURCES;
        }
        var info   = new ModuleInfo(sourceFile, deduce);
        var srcDir = info.getSourceDir();
        if (srcDir == null) {
            return NO_RESOURCES;
        }
        var resDir = deduce ? findResourcesDir(srcDir, info.getProjectDir()) : null;
        return new ResourceDir(resDir != null ? List.of(srcDir, resDir) : List.of(srcDir));
    }

    /**
     * Given source and project directories directly, create the ResourceDir without creating a ModuleInfo.
     * This avoids recursion when called from within ModuleInfo construction.
     */
    public static ResourceDir forSourceDir(final File srcDir, final File projectDir, final boolean deduce) {
        if (srcDir == null) {
            return NO_RESOURCES;
        }
        var resDir = deduce ? findResourcesDir(srcDir, projectDir) : null;
        return new ResourceDir(resDir != null ? List.of(srcDir, resDir) : List.of(srcDir));
    }

    /**
     * Walk parent directories from srcDir up to prjDir, looking for a "resources" sibling.
     */
    private static File findResourcesDir(final File srcDir, final File prjDir) {
        if (prjDir == null || prjDir.equals(srcDir)) {
            return null;
        }
        for (var dir = srcDir.getParentFile(); dir != null && dir.isDirectory(); dir = dir.getParentFile()) {
            var resDir = new File(dir, "resources");
            if (resDir.isDirectory()) {
                return resDir;
            }
            if (dir.equals(prjDir)) {
                return null;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return resourcePath.isEmpty();
    }

    public Optional<ResourceDir> getParent() {
        return Optional.ofNullable(parent);
    }

    public String getName() {
        return name;
    }

    public Optional<FileTime> getCreatedTime() {
        return getLatestTime(FileExpression::createdTime);
    }

    public Optional<FileTime> getModifiedTime() {
        return getLatestTime(FileExpression::modifiedTime);
    }

    private Optional<FileTime> getLatestTime(final Function<File, FileTime> timeFunc) {
        return resourcePath.stream()
                .filter(File::isDirectory)
                .map(timeFunc)
                .filter(Objects::nonNull)
                .max(FileTime::compareTo);
    }

    public int getDepth() {
        return parent == null ? 0 : 1 + parent.getDepth();
    }

    public List<File> getLocations() {
        return resourcePath;
    }

    public Set<String> getNames() {
        return resourcePath.stream()
                .flatMap(file -> file.isDirectory()
                        ? Optional.ofNullable(file.list()).stream().flatMap(Arrays::stream)
                        : file.exists() ? Stream.of(file.getName()) : Stream.empty())
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    /**
     * Find the specified resource name within this ResourceDir.
     */
    public Optional<ResourceEntry> find(final String name) {
        // Directories take precedence
        final var subDirs = resourcePath.stream()
                .filter(File::isDirectory)
                .map(dir -> new File(dir, name))
                .filter(child -> child.exists() && child.isDirectory())
                .toList();

        if (!subDirs.isEmpty()) {
            return Optional.of(new ResourceEntry.DirEntry(new ResourceDir(this, name, subDirs)));
        }

        // Look for a matching file
        return resourcePath.stream()
                .map(file -> file.isDirectory() ? new File(file, name) : file)
                .filter(file -> file.exists() && !file.isDirectory() && file.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(ResourceEntry.FileEntry::new);
    }

    /**
     * Get a subdirectory by name.
     */
    public ResourceDir getDirectory(final String name) {
        return find(name)
                .filter(ResourceEntry.DirEntry.class::isInstance)
                .map(e -> ((ResourceEntry.DirEntry) e).dir())
                .orElse(NO_RESOURCES);
    }

    public long getTimestamp() {
        return resourcePath.stream()
                .mapToLong(this::calcTimestamp)
                .max()
                .orElse(0L);
    }

    private long calcTimestamp(final File file) {
        return Optional.ofNullable(file.listFiles())
                .stream()
                .flatMap(Arrays::stream)
                .mapToLong(this::calcTimestamp)
                .max()
                .orElseGet(file::lastModified);
    }

    @Override
    public String toString() {
        return "ResourceDir(" + String.join(", ", resourcePath.stream().map(File::toString).toList()) + ')';
    }
}
