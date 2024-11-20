package org.xvm.tool.flag;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A base class for flag values representing file names.
 */
public abstract class BaseFileValue
    {
    /**
     * Parse a String file name value into a {@link File}.
     *
     * @param arg  the String representation of the file
     *
     * @return the {@link File} parsed from the String value
     */
    protected File parseFile(String arg)
        {
        return parse(arg, false).stream().findFirst().orElse(null);
        }

    /**
     * Parse a String file name value into possibly a list of {@link File} instances.
     *
     * @param arg  the String representation of the file, or files
     *
     * @return the list of {@link File} instances parsed from the String value
     */
    protected List<File> parseFiles(String arg)
        {
        return parse(arg, true);
        }

    /**
     * Parse a String file name value into possibly a list of {@link File} instances.
     *
     * @param arg         the String representation of the file, or files
     * @param allowMulti  {@code true} if multiple file names are allowed
     *
     * @return the list of {@link File} instances parsed from the String value
     */
    private List<File> parse(String arg, boolean allowMulti)
        {
        List<File> files = Collections.emptyList();
        if (!arg.isEmpty())
            {
            try
                {
                files = resolvePath(arg);
                }
            catch (IOException e)
                {
                throw new IllegalArgumentException("Exception resolving path \"" + arg + "\"", e);
                }

            if (!allowMulti && files.size() > 1)
                {
                throw new IllegalArgumentException("Multiple (" + files.size()
                        + ") files specified, but only one file allowed");
                }
            }
        return files;
        }

    /**
     * Resolve the specified "path string" into a list of files.
     *
     * @param paths   the path to resolve, which may be a file or directory name,
     *                and may include wildcards, etc.
     *
     * @return a list of File objects
     *
     * @throws IOException if there is an error processing the file names
     */
    protected static List<File> resolvePath(String paths)
            throws IOException
        {
        List<File> files = new ArrayList<>();
        for (String path : paths.split(File.pathSeparator))
            {
            if (path.length() >= 2 && path.charAt(0) == '~'
                    && (path.charAt(1) == '/' || path.charAt(1) == File.separatorChar))
                {
                    path = System.getProperty("user.home") + File.separatorChar + path.substring(2);
                }

            if (path.indexOf('*') >= 0 || path.indexOf('?') >= 0)
                {
                // wildcard file names
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), path))
                    {
                    stream.forEach(p -> files.add(p.toFile()));
                    }
                }
            else
                {
                files.add(new File(path));
                }
            }
        return files;
        }
    }
