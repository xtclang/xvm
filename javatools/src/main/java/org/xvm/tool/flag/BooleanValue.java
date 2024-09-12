package org.xvm.tool.flag;

/**
 * A {@link Boolean} flag value.
 */
public class BooleanValue
        implements Flag.Value<Boolean>
    {
    /**
     * Create a {@link BooleanValue} with a value of {@code null}.
     */
    public BooleanValue()
        {
        }

    /**
     * Create a {@link BooleanValue} with a specified value.
     *
     * @param value  the value to set
     */
    public BooleanValue(boolean value)
        {
        this.value = value;
        }

    @Override
    public String asString()
        {
        return String.valueOf(value);
        }

    @Override
    public void setString(String arg)
        {
        if (arg.length() == 1)
            {
            switch (arg.charAt(0))
                {
                case 'T': case 't':
                case 'Y': case 'y':
                case '1':
                    value = true;
                    break;

                case 'F': case 'f':
                case 'N': case 'n':
                case '0':
                    value = false;
                    break;
                }
            }
        else if ("true".equalsIgnoreCase(arg) || "yes".equalsIgnoreCase(arg))
            {
            value = true;
            }
        else if ("false".equalsIgnoreCase(arg) || "no".equalsIgnoreCase(arg))
            {
            value = false;
            }
        }

    @Override
    public void setValue(Boolean value)
        {
        this.value = value != null && value;
        }

    @Override
    public Boolean get()
        {
        return value;
        }

    // ----- data members --------------------------------------------------------------------------

    /**
     * The current value.
     */
    private boolean value;
    }
