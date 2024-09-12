package org.xvm.tool.flag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A command line flag.
 * <p/>
 * A flag always has a long name, which is used on the command line with a double dash
 * prefix, e.g. "--foo". Optionally a command also has a single character shorthand used
 * on the command line with a single dash prefix, e.g. "-x".
 * <p/>
 * A flag has a value of a specific type. Strings entered on the command line will be converted
 * to the type required by the flag value.
 * <p/>
 * Some flags allow multiple values and can appear multiple times on the command line, some flags
 * are singular and should appear only once.
 *
 * @param <Type>  the type that this flag represents.
 */
public class Flag<Type>
    {
    /**
     * Create a new {@link Flag}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param value         the initial value for this flag
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     */
    public Flag(String name, char shorthand, String usage, Value<Type> value, Value<Type> defaultValue,
                Value<Type> noArgDefault, boolean hidden, boolean passThru, String passThruName)
        {
        if (name == null || name.isBlank())
            {
            throw new IllegalArgumentException("a flag name cannot be null or blank");
            }
        this.name         = name;
        this.shorthand    = shorthand;
        this.usage        = usage;
        this.hidden       = hidden;
        this.passThru     = passThru;
        this.passThruName = passThruName;
        this.value        = Objects.requireNonNull(value);
        this.defaultValue = defaultValue;
        this.noArgDefault = noArgDefault;
        // make sure the current value is null
        value.setValue(null);
        }

    /**
     * Returns the long name of this flag.
     * <p>
     * This is the name that is used on the command line for this flag
     * prefixed with "--". For example, if the name is "foo" the flag
     * would appear on the command line as "--foo"
     *
     * @return the long name of this flag
     */
    public String getName()
        {
        return name;
        }

    /**
     * Returns the single character shorthand flag for this flag,
     * or {@link #NO_SHORTHAND} if this flag does not have
     * a shorthand.
     * <p>
     * This shorthand is the single character used on the command line
     * to represent this flag prefixed with "-" or chained in a group of
     * flags prefixed with "-". For example if the shorthand is "t" it
     * could appear on its own as "-t" or in a group of shorthand flags
     * "-abc".
     *
     * @return the single character shorthand flag for this flag,
     *         or {@link #NO_SHORTHAND} if this flag does not have
     *         a shorthand.
     */
    public char getShorthand()
        {
        return shorthand;
        }

    /**
     * Determine whether this flag has a single character shorthand.
     *
     * @return {@code true} if this flag has a single character shorthand
     */
    public boolean hasShorthand()
        {
        return shorthand != NO_SHORTHAND;
        }

    /**
     * Return the usage text for this flag that can be used in help messages.
     *
     * @return the usage text for this flag
     */
    public String getUsage()
        {
        return usage;
        }

    /**
     * Determine whether this flag should not be displayed in help messages.
     *
     * @return {@code true} if this flag should not be displayed in help messages
     */
    public boolean isHidden()
        {
        return hidden;
        }

    /**
     * Determine whether this flag should passed-thru to the Ecstasy layer.
     *
     * @return {@code true} if this flag should passed-thru to the Ecstasy layer
     */
    public boolean isPassThru()
        {
        return passThru;
        }

    /**
     * Return the pass-thru name for this flag.
     *
     * @return the pass-thru name for this flag
     */
    public String getPassThruName()
        {
        return passThruName;
        }

    /**
     * Return the current value for this flag.
     * <p>
     * This will be the value set when the flag was created, or the
     * value that has been set after parsing the command line.
     *
     * @return the current value for this flag
     */
    public Value<Type> getValue()
        {
        return value;
        }

    /**
     * Return the default value to use for this flag if it has
     * not been specified on the command line.
     *
     * @return the default value to use for this flag if it has
     *         not been specified on the command line, or
     *         {@code null} if there is no default value
     */
    public Value<Type> getDefaultValue()
        {
        return defaultValue;
        }

    /**
     * Return the value to use for this flag if it is specified on the
     * command line without a value.
     * <p>
     * For example a flag can be specified with a value such as "--foo=bar"
     * or "--foo bar", or "-t=foo" or "-t foo". If the {@link #noArgDefault}
     * value is set the flag can be specified without a value, "--foo" or "-t"
     * and this value will be used. This is typically used for boolean flags
     * where specifying just the flag sets the value to {@code true}.
     *
     * @return the value to use for this flag if it is specified on the
     *         command line without a value
     */
    public Value<Type> getNoArgDefault()
        {
        return noArgDefault;
        }

    // ----- inner class Value ---------------------------------------------------------------------

    /**
     * The single value for a {@link Flag}.
     *
     * @param <Type>  the type of the value
     */
    public interface Value<Type>
        {
        /**
         * Return the string representation of the value.
         *
         * @return the string representation of the value
         */
        String asString();

        /**
         * Set this value from a string, as it would appear
         * on the command line.
         *
         * @param s  the string value parsed from the
         *           command line
         */
        void setString(String s);

        /**
         * Set the value for this flag.
         *
         * @param value  the value for this flag
         */
        void setValue(Type value);

        /**
         * Get the value for this flag.
         *
         * @return the value for this flag
         */
        Type get();

        /**
         * Return the values as a string array.
         *
         * @return the values as a string array
         */
        default String[] asStrings()
            {
            return new String[] { asString() };
            }

        /**
         * Return {@code true} if this is a multi-value flag.
         *
         * @return {@code true} if this is a multi-value flag
         */
        default boolean isMultiValue()
            {
            return false;
            }
        }

    // ----- inner class MultiValue ----------------------------------------------------------------

    /**
     * The value for a {@link Flag} that can be specified multiple
     * times on the command line and holds a list of values.
     *
     * @param <Type>  the type of the value
     */
    public interface MultiValue<Type>
            extends Value<List<Type>>
        {
        /**
         * Append a value from the command line to this
         * flag's value list.
         *
         * @param s  the command line value to append to
         *           this flag's value list
         */
        void append(String s);

        /**
         * Replace this flag's value list with the specified string values.
         *
         * @param values  the string values to use to set this flags values
         */
        void replace(String[] values);

        @Override
        default boolean isMultiValue()
            {
            return true;
            }
        }

    // ----- inner class MapValue ------------------------------------------------------------------

    /**
     * The value for a {@link Flag} that can be specified multiple
     * times on the command line and holds a map of values.
     *
     * @param <K>  the type of the map keys
     * @param <V>  the type of the map values
     */
    public interface MapValue<K, V>
            extends Value<Map<K, V>>
        {
        /**
         * Append a value from the command line to this
         * flag's value list.
         *
         * @param s  the command line value to append to
         *           this flag's value list
         */
        void append(String s);

        /**
         * Replace this flag's value list with the specified string values.
         *
         * @param values  the string values to use to set this flags values
         */
        void replace(String[] values);

        @Override
        default boolean isMultiValue()
            {
            return true;
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * A {@code char} to represent a flag having no shorthand.
     */
    public static final char NO_SHORTHAND = ' ';

    // ----- data members --------------------------------------------------------------------------

    /**
     * The name of the flag, as it will be used on the command line.
     */
    private final String name;

    /**
     * The single character abbreviation for this flag, as it will be used on the command line.
     */
    private final char shorthand;

    /**
     * The help message.
     */
    private final String usage;

    /**
     * A flag that when {@code true} indicates this flag does not appear in the help message.
     */
    private final boolean hidden;

    /**
     * A flag that when {@code true} indicates this flag is passed-thru to Ecstasy code.
     */
    private final boolean passThru;

    /**
     * An optional alternative name to use for this flag when passed through to Ecstasy code
     */
    private final String passThruName;

    /**
     * The default value for this flag.
     */
    private final Value<Type> defaultValue;

    /**
     * The value of this flag, set from the command line.
     */
    private final Value<Type> value;

    /**
     * The value to set for this flag when no argument is specified on the command line.
     */
    private final Value<Type> noArgDefault;
    }
