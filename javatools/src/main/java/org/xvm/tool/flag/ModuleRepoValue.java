package org.xvm.tool.flag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.xvm.util.Handy.parseDelimitedString;

/**
 * A flag value representing a module path.
 * <p>
 * This is an extension of a {@link FileListValue} that only accepts
 * file names that are readable and valid as module files.
 */
public class ModuleRepoValue
        extends FileListValue
    {
    @Override
    public List<File> parseFiles(String sArg)
        {
        List<File> repo = new ArrayList<>();
        if (!sArg.isEmpty())
            {
            for (String sPath : parseDelimitedString(sArg, File.pathSeparatorChar))
                {
                List<File> files;
                try
                    {
                    files = resolvePath(sPath);
                    }
                catch (IOException e)
                    {
                    throw new IllegalArgumentException("Exception resolving path \"" + sPath + "\": ", e);
                    }

                if (files.isEmpty())
                    {
                    throw new IllegalArgumentException("Could not resolve: \"" + sPath + "\"");
                    }
                else
                    {
                    for (File file : files)
                        {
                        if (file.canRead())
                            {
                            repo.add(file);
                            }
                        else if (file.exists())
                            {
                            throw new IllegalArgumentException((file.isDirectory() ? "Directory" : "File")
                                    + " not readable: " + file);
                            }
                        }
                    }
                }
            }
        return repo;
        }
    }
