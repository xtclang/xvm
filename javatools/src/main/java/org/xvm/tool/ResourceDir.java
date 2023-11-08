package org.xvm.tool;


import java.io.File;

import java.nio.file.attribute.FileTime;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;


import static org.xvm.compiler.ast.FileExpression.createdTime;
import static org.xvm.compiler.ast.FileExpression.modifiedTime;


/**
 * Represents a directory that contains resources, based on the contents of one or more directories
 * and/or files.
 */
public class ResourceDir
    {
    /**
     * @param resourceLoc  the non-null File indicating the directory (or single file)
     *                     location to use as the entire resource path
     */
    public ResourceDir(File resourceLoc)
        {
        this(new File[] {resourceLoc});
        }

    /**
     * @param resourcePath  the non-null array of non-null File objects indicating the sequence
     *                      of directory (or single file) locations to use as the resource path
     */
    public ResourceDir(File[] resourcePath)
        {
        this(null, "", resourcePath.clone());

        for (File file : resourcePath)
            {
            if (file == null)
                {
                throw new IllegalArgumentException("Resource location must not be null");
                }

            if (!file.exists())
                {
                throw new IllegalArgumentException(
                        "Resource location \"" + file + "\" does not exist");
                }
            }
        }

    /**
     * @param resourcePath  the non-null array of non-null File objects indicating the sequence
     *                      of directory (or single file) locations to use as the resource path
     */
    protected ResourceDir(ResourceDir parent, String name, File[] resourcePath)
        {
        this.parent       = parent;
        this.name         = name;
        this.resourcePath = resourcePath;
        }

    /**
     * Given a source file, create the default ResourceDir that corresponds to that source file.
     *
     * @param sourceFile  the location of an Ecstasy module source file
     */
    public static ResourceDir forSource(File sourceFile)
        {
        if (sourceFile == null)
            {
            return NoResources;
            }

        ModuleInfo info   = new ModuleInfo(sourceFile);
        File       prjDir = info.getProjectDir();
        File       srcDir = info.getSourceDir();
        if (prjDir != null && srcDir != null && !prjDir.equals(srcDir))
            {
            File parentDir = srcDir.getParentFile();
            while (parentDir != null && parentDir.isDirectory())
                {
                File resDir = new File(parentDir, "resources");
                if (resDir.isDirectory())
                    {
                    return new ResourceDir(new File[] {srcDir, resDir});
                    }

                // don't go "up" past the project directory
                if (parentDir.equals(prjDir))
                    {
                    break;
                    }

                parentDir = parentDir.getParentFile();
                }
            }

        if (srcDir != null)
            {
            return new ResourceDir(new File[] {srcDir});
            }

        return NoResources;
        }

    /**
     * @return the parent of this ResourceDir, of null iff this is the "root" ResourceDir
     */
    public ResourceDir getParent()
        {
        return parent;
        }

    /**
     * @return the name by which this ResourceDir is known within its parent ResourceDir
     */
    public String getName()
        {
        return name;
        }

    /**
     * @return the creation date/time for the directory itself
     */
    public FileTime getCreatedTime()
        {
        FileTime created = null;
        for (File file : resourcePath)
            {
            if (file.isDirectory())
                {
                FileTime newTime = createdTime(file);
                if (created == null)
                    {
                    created = newTime;
                    }
                else if (newTime.compareTo(created) > 0)
                    {
                    created = newTime;
                    }
                }
            }
        return created;
        }

    /**
     * @return the modification date/time for the directory itself
     */
    public FileTime getModifiedTime()
        {
        FileTime modified = null;
        for (File file : resourcePath)
            {
            if (file.isDirectory())
                {
                FileTime newTime = modifiedTime(file);
                if (modified == null)
                    {
                    modified = newTime;
                    }
                else if (newTime.compareTo(modified) > 0)
                    {
                    modified = newTime;
                    }
                }
            }
        return modified;
        }

    /**
     * @return the depth of this ResourceDir, with zero being the "root" ResourceDir
     */
    public int getDepth()
        {
        return parent == null ? 0 : 1 + parent.getDepth();
        }

    /**
     * @return an array of File objects, each of which represents a resource or a resource
     *         directory
     */
    public File[] getLocations()
        {
        return resourcePath.clone();
        }

    /**
     * Obtain the set of names contained directly within this ResourceDir. The set is ordered in
     * a predictable (i.e. stable) manner.
     *
     * @return a set of names of resource files and/or directories inside this ResourceDir
     */
    public Set<String> getNames()
        {
        TreeMap<String, Object> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (File file : resourcePath)
            {
            if (file.isDirectory())
                {
                for (String name : file.list())
                    {
                    names.putIfAbsent(name, null);
                    }
                }
            else if (file.exists())
                {
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
    public Object getByName(String name)
        {
        ArrayList<File> subdirs = null;
        for (File file : resourcePath)
            {
            if (file.isDirectory())
                {
                File child = new File(file, name);
                if (child.exists())
                    {
                    if (child.isDirectory())
                        {
                        if (subdirs == null)
                            {
                            subdirs = new ArrayList<>();
                            }
                        subdirs.add(child);
                        }
                    else if (subdirs == null)
                        {
                        return child;
                        }
                    }
                }
            else if (subdirs == null && file.getName().equalsIgnoreCase(name))
                {
                return file;
                }
            }

        return subdirs == null
                ? null
                : new ResourceDir(this, name, subdirs.toArray(new File[0]));
        }

    /**
     * Obtain a ResourceDir (a resource directory) that is located in this ResourceDir.
     *
     * @param name  the directory name
     *
     * @return the specified ResourceDir, if it exists nested directly within this ResourceDir,
     *         otherwise null
     */
    public ResourceDir getDirectory(String name)
        {
        return getByName(name) instanceof ResourceDir dir ? dir : null;
        }

    /**
     * Obtain a File that is located in this ResourceDir.
     *
     * @param name  the file name
     *
     * @return the specified file, if it exists in the ResourceDir, otherwise null
     */
    public File getFile(String name)
        {
        return getByName(name) instanceof File file ? file : null;
        }

    /**
     * @return the timestamp for all of the resources (i.e. the most recent file timestamp);
     *         if there are no resources, 0L is returned
     */
    public long getTimestamp()
        {
// TODO CP optimize
        long resourcesTimestamp = 0L;
        for (String name : getNames())
            {
            Object resource = getByName(name);
            if (resource != null)
                {
                long timestamp = resource instanceof File file
                        ? file.lastModified()
                        : ((ResourceDir) resource).getTimestamp();
                if (timestamp > resourcesTimestamp)
                    {
                    resourcesTimestamp = timestamp;
                    }
                }
            }
        return resourcesTimestamp;
        }

    /**
     * A ResourceDir that represents an empty set of resources.
     */
    public static final ResourceDir NoResources = new ResourceDir(new File[0]);

    private final ResourceDir parent;

    private final String name;

    private final File[] resourcePath;
    }