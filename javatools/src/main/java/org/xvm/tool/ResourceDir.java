package org.xvm.tool;


import java.io.File;

import java.nio.file.attribute.FileTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;


import org.xvm.compiler.ast.FileExpression;
import static org.xvm.util.Handy.listFiles;


/**
 * Represents a directory that contains resources, based on the contents of one or more directories
 * and/or files.
 */
public class ResourceDir {
    /**
     * @param resourceLoc  the non-null File indicating the directory (or single file)
     *                     location to use as the entire resource path
     */
    @SuppressWarnings("unused")
    public ResourceDir(final File resourceLoc) {
        this(List.of(resourceLoc));
    }

    /**
     * @param resourcePath  the non-null list of non-null File objects indicating the sequence
     *                      of directory (or single file) locations to use as the resource path
     */
    public ResourceDir(final List<File> resourcePath) {
        this(null, "", new ArrayList<>(resourcePath));

        for (final File file : resourcePath) {
            if (file == null) {
                throw new IllegalArgumentException("Resource location must not be null");
            }
            if (!file.exists()) {
                throw new IllegalArgumentException("Resource location \"" + file + "\" does not exist");
            }
        }
    }

    /**
     * @param resourcePath  the non-null list of non-null File objects indicating the sequence
     *                      of directory (or single file) locations to use as the resource path
     */
    protected ResourceDir(final ResourceDir parent, final String name, final List<File> resourcePath) {
        this.parent       = parent;
        this.name         = name;
        this.resourcePath = resourcePath;
    }

    /**
     * Given a source file, create the default ResourceDir that corresponds to that source file.
     *
     * @param sourceFile the location of an Ecstasy module source file
     * @param deduce     pass true to enable the algorithm to search for a likely resource directory
     */
    public static ResourceDir forSource(final File sourceFile, final boolean deduce) {
        if (sourceFile == null) {
            return NoResources;
        }

        ModuleInfo info   = new ModuleInfo(sourceFile, deduce);
        File       prjDir = info.getProjectDir();
        File       srcDir = info.getSourceDir();
        if (deduce && prjDir != null && srcDir != null && !prjDir.equals(srcDir)) {
            File parentDir = srcDir.getParentFile();
            while (parentDir != null && parentDir.isDirectory()) {
                File resDir = new File(parentDir, "resources");
                if (resDir.isDirectory()) {
                    return new ResourceDir(List.of(srcDir, resDir));
                }

                // don't go "up" past the project directory
                if (parentDir.equals(prjDir)) {
                    break;
                }

                parentDir = parentDir.getParentFile();
            }
        }

        if (srcDir != null) {
            return new ResourceDir(List.of(srcDir));
        }

        return NoResources;
    }

    /**
     * @return the parent of this ResourceDir, of null iff this is the "root" ResourceDir
     */
    public ResourceDir getParent() {
        return parent;
    }

    /**
     * @return the name by which this ResourceDir is known within its parent ResourceDir
     */
    public String getName() {
        return name;
    }

    /**
     * @return the creation date/time for the directory itself
     */
    public FileTime getCreatedTime() {
        return getLatestTime(FileExpression::createdTime);
    }

    /**
     * @return the modification date/time for the directory itself
     */
    public FileTime getModifiedTime() {
        return getLatestTime(FileExpression::modifiedTime);
    }

    private FileTime getLatestTime(final Function<File, FileTime> timeExtractor) {
        return resourcePath.stream()
                .filter(File::isDirectory)
                .map(timeExtractor)
                .filter(Objects::nonNull)
                .max(FileTime::compareTo)
                .orElse(null);
    }

    /**
     * @return the depth of this ResourceDir, with zero being the "root" ResourceDir
     */
    public int getDepth() {
        return parent == null ? 0 : 1 + parent.getDepth();
    }

    /**
     * @return a list of File objects, each of which represents a resource or a resource
     *         directory
     */
    public List<File> getLocations() {
        return List.copyOf(resourcePath);
    }

    /**
     * Obtain the set of names contained directly within this ResourceDir. The set is ordered in
     * a predictable (i.e. stable) manner.
     *
     * @return a set of names of resource files and/or directories inside this ResourceDir
     */
    public Set<String> getNames() {
        final var names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (File file : resourcePath) {
            if (file.isDirectory()) {
                listFiles(file).stream()
                        .map(File::getName)
                        .forEach(n -> names.putIfAbsent(n, null));
            } else if (file.exists()) {
                names.putIfAbsent(file.getName(), null);
            }
        }
        return names.keySet();
    }

    /**
     * Find the specified resource name within this ResourceDir.
     *
     * @param name  the name of a file or directory within this ResourceDir
     *
     * @return the specified ResourceDir or File object iff the name exists in this ResourceDir;
     *         otherwise null
     */
    public Object getByName(final String name) {
        final var subDirs = new ArrayList<File>();

        for (File file : resourcePath) {
            if (file.isDirectory()) {
                File child = new File(file, name);
                if (child.exists()) {
                    if (child.isDirectory()) {
                        subDirs.add(child);
                    } else if (subDirs.isEmpty()) {
                        return child;
                    }
                }
            } else if (subDirs.isEmpty() && file.getName().equalsIgnoreCase(name)) {
                return file;
            }
        }

        return subDirs.isEmpty() ? null : new ResourceDir(this, name, subDirs);
    }

    /**
     * Obtain a ResourceDir (a resource directory) that is located in this ResourceDir.
     *
     * @param name  the directory name
     *
     * @return the specified ResourceDir, if it exists nested directly within this ResourceDir,
     *         otherwise null
     */
    public ResourceDir getDirectory(final String name) {
        return getByName(name) instanceof ResourceDir dir ? dir : null;
    }

    /**
     * Obtain a File that is located in this ResourceDir.
     *
     * @param name  the file name
     *
     * @return the specified file, if it exists in the ResourceDir, otherwise null
     */
    @SuppressWarnings("unused")
    public File getFile(final String name) {
        return getByName(name) instanceof File file ? file : null;
    }

    /**
     * @return the timestamp for all the resources (i.e. the most recent file timestamp);
     *         if there are no resources, 0L is returned
     */
    public long getTimestamp() {
        long timestamp = 0L;
        for (File file : resourcePath) {
            timestamp = Math.max(timestamp, calcTimestamp(file));
        }
        return timestamp;
    }

    private long calcTimestamp(final File dirOrFile) {
        return listFiles(dirOrFile).stream()
                .mapToLong(this::calcTimestamp)
                .reduce(dirOrFile.lastModified(), Math::max);
    }

    @Override
    public String toString() {
        final var buf = new StringBuilder().append("ResourceDir(");
        boolean first = true;
        for (File file : resourcePath) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(file);
        }
        return buf.append(')').toString();
    }

    /**
     * A ResourceDir that represents an empty set of resources.
     */
    static final ResourceDir NoResources = new ResourceDir(List.of());

    private final ResourceDir parent;

    private final String name;

    private final List<File> resourcePath;
}
