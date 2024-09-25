package org.xvm.tool;

/**
 * A RuntimeException thrown upon a launcher failure.
 */
public class LauncherException
        extends RuntimeException
    {
    /**
     * @param error  true to abort with an error status
     */
    public LauncherException(final boolean error)
        {
        this(error, null);
        }

    public LauncherException(final boolean error, final String msg)
       {
        super(msg);
        this.error = error;
        }

    @Override
    public String toString()
        {
        return '[' + getClass().getSimpleName() + ": isError=" + error + ", msg=" + getMessage() + ']';
        }

    public final boolean error;
    }
