package org.xvm.tool.flag;

import java.io.File;

/**
 * A {@link File} flag value.
 */
public class FileValue
        extends BaseFileValue
        implements Flag.Value<File>
    {
    /**
     * Create a {@link FileValue} with a {@code null} value.
     */
    public FileValue()
        {
        this(null);
        }

    /**
     * Create a {@link FileValue} with a {@link File} value.
     *
     * @param value  the {@link File} to set as the initial value
     */
    public FileValue(File value)
        {
        this.value = value;
        }

    @Override
    public String asString()
        {
        return value == null ? null : value.getName();
        }

    @Override
    public void setString(String arg)
        {
        value = parseFile(arg);
        }

    @Override
    public void setValue(File value)
        {
        this.value = value;
        }

    @Override
    public File get()
        {
        return value;
        }

    // ----- data members --------------------------------------------------------------------------

    /**
     * The flag value.
     */
    private File value;
    }
