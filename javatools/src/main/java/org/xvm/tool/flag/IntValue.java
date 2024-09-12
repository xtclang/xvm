package org.xvm.tool.flag;

/**
 * An {@link Integer} flag value.
 */
public class IntValue
        implements Flag.Value<Integer>
    {
    /**
     * Create an {@link IntValue} with an initial value of {@code null}.
     */
    public IntValue()
        {
        }

    /**
     * Create an {@link IntValue} with an initial value.
     *
     * @param i  the initial value to set
     */
    public IntValue(Integer i)
        {
        value = i;
        }

    @Override
    public String asString()
        {
        return value == null ? null : String.valueOf(value);
        }

    @Override
    public void setString(String arg)
        {
        value = arg == null || arg.isBlank() ? null : Integer.parseInt(arg.trim());
        }

    @Override
    public void setValue(Integer value)
        {
        this.value = value;
        }

    @Override
    public Integer get()
        {
        return value;
        }

    // ----- data members --------------------------------------------------------------------------

    /**
     * The flag value.
     */
    private Integer value;
    }
