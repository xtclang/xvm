package org.xvm.tool.flag;

/**
 * A string flag value.
 */
public class StringValue
        implements Flag.Value<String>
    {
    /**
     * Create a {@link StringValue} with a {@code null} value.
     */
    public StringValue()
        {
        this(null);
        }

    /**
     * Create a {@link StringValue}.
     *
     * @param value  the initial value to set
     */
    public StringValue(String value)
        {
        this.value = value;
        }

    @Override
    public String asString()
        {
        return value;
        }

    @Override
    public void setString(String sArg)
        {
        setValue(sArg);
        }

    @Override
    public void setValue(String arg)
        {
        if (arg == null || arg.isEmpty())
            {
            value = arg;
            }
        else if (arg.charAt(0) == '\"')
            {
            if (arg.length() >= 2 && arg.charAt(arg.length()-1) == '\"')
                {
                value = arg.substring(1, arg.length()-1);
                }
            }
        else
            {
            value = arg;
            }
        }

    @Override
    public String get()
        {
        return value;
        }

    /**
     * Returns a {@link StringValue} wrapping the specified string
     * or {@code null} if the string parameter is {@code null}.
     *
     * @param s  the string value to wrap
     *
     * @return a {@link StringValue} wrapping the specified string
     *         or {@code null} if the string parameter is {@code null}
     */
    public static StringValue valueOrNull(String s)
        {
        return s == null ? null : new StringValue(s);
        }

    // ----- data members --------------------------------------------------------------------------

    /**
     * The string value.
     */
    private String value;
    }
