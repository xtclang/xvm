package org.xvm.tool.flag;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A flag representing a list of {@link File files}.
 */
public class FileListValue
        extends BaseFileValue
        implements Flag.MultiValue<File>
    {
    /**
     * Create a {@link FileListValue} with an empty list of files.
     */
    public FileListValue()
        {
        }

    /**
     * Create a {@link FileListValue} with the specified files
     * as it's initial value.
     *
     * @param files  the array of files to set as the flag value
     */
    public FileListValue(File... files)
        {
        this(List.of(files));
        }

    /**
     * Create a {@link FileListValue} with the specified files
     * as it's initial value.
     *
     * @param files  the array of files to set as the flag value
     */
    public FileListValue(Collection<? extends File> files)
        {
        value.addAll(files);
        }


    @Override
    public String asString()
        {
        return value.stream().map(File::getName).collect(Collectors.joining(","));
        }

    @Override
    public void setString(String arg)
        {
        append(arg);
        }

    @Override
    public void setValue(List<File> list)
        {
        value.clear();
        if (list != null)
            {
            value.addAll(list);
            }
        }

    @Override
    public List<File> get()
        {
        return value;
        }

    @Override
    public void append(String sArg)
        {
        List<File> list = parseFiles(sArg);
        value.addAll(list);
        }

    @Override
    public void replace(String[] args)
        {
        value.clear();
        for (String s : args)
            {
            append(s);
            }
        }

    @Override
    public String[] asStrings()
        {
        return value.stream().map(File::getName).toArray(String[]::new);
        }

    // ----- data members --------------------------------------------------------------------------

    /**
     * The list of {@link File} instances.
     */
    private final List<File> value = new ArrayList<>();
    }
