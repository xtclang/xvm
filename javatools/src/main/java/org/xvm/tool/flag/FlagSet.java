package org.xvm.tool.flag;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;

/**
 * A {@link FlagSet} holds set of POSIX/GNU-style {@link Flag --flags}
 * that can be parsed from an array of string values, typically from
 * the command line.
 * <p>
 * see <a href="https://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">
 *     GNU Program Argument Syntax Conventions</a>
 * <p>
 * Flags are specified on the command line with a double dash "--" prefix or single dash "-"
 * prefixed shorthand letters.
 * Flags that have no {@link Flag#getNoArgDefault() no-arg default value} have a value specified
 * using either the equals signe "=" or leaving a space between the flag and the value
 * as in {@code --flag x}.
 * Flags with a {@link Flag#getNoArgDefault()} specified (typically boolean flags) can be
 * combined with other shorthand flags.
 * <p>
 * Command Line Flag Syntax:
 * <pre>
 * 	--flag    // no-ard default flags only
 * 	--flag=x
 * 	--flag x  // flags without no-arg defaults only
 *  -f        // no-arg default flags only
 *  -abc      // no-arg default flags only
 *  -a=x
 *  -a x      // flags without no-arg defaults only
 * </pre>
 * <p>
 * Single dashes can be used to signify a series of shorthand letters for flags.
 * In this case, all but the last shorthand letter must have a no-arg default value.
 * <p>
 * In the example below flag 'f' must have a no-arg default value:
 * <pre>
 * 	-f  // flag 'f' must have a no-arg def
 * </pre>
 * <p>
 * In the example below flags 'a', 'b' and 'c' must have a no-arg default value:
 * <pre>
 * 	-abc
 * </pre>
 * In the examples below flags 'a' and 'b' but 'c' takes the value 'Foo' from the command line:
 * <pre>
 * 	-abc=Foo
 * 	-abc Foo
 * 	-abcFoo
 * </pre>
 * <p>
 * Single dash prefixed flags can have a value specified by just adding the value directly as
 * a suffix to the flag. For example:
 * <pre>
 * 	-fFoo // flag -f has the value "Foo"
 * </pre>
 * <p>
 * A flag set contains all the valid flags that are allowed on the
 * command line. A list of command line arguments can then be parsed
 * using the {@link #parse(String[])} method to set the flag values
 * in this flag set.
 */
public class FlagSet
    {
    /**
     * Create a default {@link FlagSet}.
     * <p>
     * This flag set will allow flag and no flag command line arguments
     * to be interspersed. It will not allow unknown flags.
     */
    public FlagSet()
        {
        this(true, false);
        }

    /**
     * Create a default {@link FlagSet}.
     *
     * @param allowInterspersed  {@code true} to allow flag and non-flag value to be interspersed
     *                           on the command line
     * @param allowUnknownFlags  {@code true} to allow unknown flags to be parsed, or false for
     *                           unknown flags to be a parse error
     */
    public FlagSet(boolean allowInterspersed, boolean allowUnknownFlags)
        {
        this.allowInterspersed = allowInterspersed;
        this.allowUnknownFlags = allowUnknownFlags;
        }

    /**
     * Return {@code true} if this {@link FlagSet} contains any flags.
     *
     * @return {@code true} if this {@link FlagSet} contains any flags
     */
    public boolean hasFlags()
        {
        return !formalFlags.isEmpty();
        }

    /**
     * Return {@code true} if this {@link FlagSet} contains any non-hidden flags.
     *
     * @return {@code true} if this {@link FlagSet} contains any non-hidden flags
     */
    public boolean hasNonHiddenFlags()
        {
        return formalFlags.values().stream().anyMatch(flag -> !flag.isHidden());
        }

    /**
     * Add {@link Flag flags} from the specified {@link FlagSet} to this
     * {@link FlagSet} that do not already exist in this {@link FlagSet}.
     *
     * @param flagSet  the {@link FlagSet} to add to this {@link FlagSet}
     */
    public void addFlags(FlagSet flagSet)
        {
        flagSet.mergeInto(this);
        }

    /**
     * Add a {@link Flag} to this {@link FlagSet}.
     *
     * @param flag  the {@link Flag} to add
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFlag(Flag<?> flag)
        {
        String name      = flag.getName();
        char   shorthand = flag.getShorthand();
        if (formalFlags.containsKey(name))
            {
            throw new IllegalArgumentException("This flag set already contains a flag with the name \""
                    + name + "\"");
            }
        if (shorthand != Flag.NO_SHORTHAND && shorthandFlags.containsKey(shorthand))
            {
            throw new IllegalArgumentException("This flag set already contains a flag with the shorthand '"
                    + shorthand + "'");
            }

        formalFlags.put(name, flag);
        if (shorthand != Flag.NO_SHORTHAND)
            {
            shorthandFlags.put(shorthand, flag);
            }
        return this;
        }

    /**
     * Add the standard help flag to this {@link FlagSet}.
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withHelp()
        {
        return withBoolean(FLAG_HELP, 'h', "Displays this help message");
        }

    /**
     * Add a boolean flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withBoolean(String name, char shorthand, String usage, boolean defaultValue,
                               Boolean noArgDefault, boolean hidden, boolean passThru, String passThruName)
        {
        BooleanValue noOptValue = noArgDefault == null ? null : new BooleanValue(noArgDefault);
        return withFlag(new Flag<>(name, shorthand, usage, new BooleanValue(), new BooleanValue(defaultValue),
                noOptValue, hidden, passThru, passThruName));
        }

    /**
     * Add a boolean flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withBoolean(String name, char shorthand, String usage, boolean defaultValue,
                               Boolean noArgDefault, boolean hidden)
        {
        return withBoolean(name, shorthand, usage, defaultValue, noArgDefault,
                hidden, false, null);
        }

    /**
     * Add a boolean flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withBoolean(String name, char shorthand, String usage, boolean defaultValue)
        {
        return withBoolean(name, shorthand, usage, defaultValue, null,
                false, false, null);
        }

    /**
     * Add a boolean flag to this {@link FlagSet}.
     * <p>
     * The flag will have a value of {@code false}, a default value of {@code false}, and
     * a no-arg default value of {@code true}. So this flag can be specified on the command
     * line without a value.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withBoolean(String name, String usage)
        {
        return withBoolean(name, Flag.NO_SHORTHAND, usage, false, Boolean.TRUE,
                false, false, null);
        }

    /**
     * Add a boolean flag to this {@link FlagSet}.
     * <p>
     * The flag will have a value of {@code false}, a default value of {@code false}, and
     * a no-arg default value of {@code true}. So this flag can be specified on the command
     * line without a value.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withBoolean(String name, char shorthand, String usage)
        {
        return withBoolean(name, shorthand, usage, false, Boolean.TRUE,
                false, false, null);
        }

    /**
     * Add an int flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withInt(String name, char shorthand, String usage, int defaultValue,
                           Integer noArgDefault, boolean hidden, boolean passThru, String passThruName)
        {
        IntValue noArgValue = noArgDefault == null ? null : new IntValue(noArgDefault); 
        return withFlag(new Flag<>(name, shorthand, usage, new IntValue(), new IntValue(defaultValue),
                noArgValue, hidden, passThru, passThruName));
        }

    /**
     * Add an int flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withInt(String name, char shorthand, String usage, int defaultValue, Integer noArgDefault)
        {
        return withInt(name, shorthand, usage, defaultValue, noArgDefault,
                false, false, null);
        }

    /**
     * Add an int flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withInt(String name, char shorthand, String usage, int defaultValue)
        {
        return withInt(name, shorthand, usage, defaultValue, null,
                false, false, null);
        }

    /**
     * Add an int flag to this {@link FlagSet}.
     * <p>
     * The flag will have a value of zero, a default value of zero,
     * and a {@code null} no-arg default value.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withInt(String name, String usage)
        {
        return withFlag(new Flag<>(name, Flag.NO_SHORTHAND, usage, new IntValue(),
                new IntValue(), null, false, false, null));
        }

    /**
     * Add an int flag to this {@link FlagSet}.
     * <p>
     * The flag will have a value of zero, a default value of zero,
     * and a {@code null} no-arg default value.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withInt(String name, char shorthand, String usage)
        {
        return withFlag(new Flag<>(name, shorthand, usage, new IntValue(), new IntValue(),
                new IntValue(), false, false, null));
        }

    /**
     * Add a string flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withString(String name, char shorthand, String usage, String defaultValue,
            String noArgDefault, boolean hidden, boolean passThru, String passThruName)
        {
        return withFlag(new Flag<>(name, shorthand, usage, new StringValue(),
                StringValue.valueOrNull(defaultValue), StringValue.valueOrNull(noArgDefault),
                hidden, passThru, passThruName));
        }

    /**
     * Add a string flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param noArgDefault  an optional value used when this flag is specified on the command line without a value,
     *                      for example, when just the flag is used "--foo" instead of with a value "--foo bar"
     * @param hidden        when {@code true} this flag will not appear in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withString(String name, char shorthand, String usage, String defaultValue,
            String noArgDefault, boolean hidden)
        {
        return withString(name, shorthand, usage, defaultValue, noArgDefault,
                hidden, false, null);
        }

    /**
     * Add a string flag to this {@link FlagSet}.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withString(String name, String usage)
        {
        return withString(name, Flag.NO_SHORTHAND, usage, null, null,
                false, false, null);
        }

    /**
     * Add a string flag to this {@link FlagSet}.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withString(String name, char shorthand, String usage)
        {
        return withString(name, shorthand, usage, null, null,
                false, false, null);
        }

    /**
     * Add a string flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withString(String name, char shorthand, String usage, String defaultValue)
        {
        return withString(name, shorthand, usage, defaultValue, null,
                false, false, null);
        }

    /**
     * Add a string list flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringList(String name, char shorthand, String usage, boolean hidden,
            boolean passThru, String passThruName)
        {
        return withFlag(new Flag<>(name, shorthand, usage, new StringListValue(), null, null,
                hidden, passThru, passThruName));
        }

    /**
     * Add a string list flag to this {@link FlagSet}.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringList(String name, String usage)
        {
        return withStringList(name, Flag.NO_SHORTHAND, usage, false, false, null);
        }

    /**
     * Add a string list flag to this {@link FlagSet}.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringList(String name, char shorthand, String usage)
        {
        return withStringList(name, shorthand, usage, false, false, null);
        }

    /**
     * Add a Map of string keys and values flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringMap(String name, char shorthand, String usage, boolean hidden,
            boolean passThru, String passThruName)
        {
        return withFlag(new Flag<>(name, shorthand, usage, new StringMapValue(), null, null,
                hidden, passThru, passThruName));
        }

    /**
     * Add a Map of string keys and values flag to this {@link FlagSet}.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringMap(String name, String usage)
        {
        return withStringMap(name, Flag.NO_SHORTHAND, usage, false, false, null);
        }
    /**
     * Add a Map of string keys and values flag to this {@link FlagSet}.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withStringMap(String name, char shorthand, String usage)
        {
        return withStringMap(name, shorthand, usage, false, false, null);
        }

    /**
     * Add a {@link File} flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFile(String name, char shorthand, String usage, File defaultValue,
                            boolean hidden, boolean passThru, String passThruName)
        {
        FileValue dflt = defaultValue == null ? null : new FileValue(defaultValue);
        return withFlag(new Flag<>(name, shorthand, usage, new FileValue(), dflt, null,
                hidden, passThru, passThruName));
        }

    /**
     * Add a {@link File} flag to this {@link FlagSet}.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFile(String name, char shorthand, String usage)
        {
        return withFile(name, shorthand, usage, null);
        }

    /**
     * Add a {@link File} flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param defaultValue  an optional default value for this flag that will be used when it is not set on
     *                      the command line
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFile(String name, char shorthand, String usage, File defaultValue)
        {
        return withFile(name, shorthand, usage, defaultValue, false, false, null);
        }

    /**
     * Add a {@link File} flag to this {@link FlagSet}.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFile(String name, String usage)
        {
        return withFile(name, Flag.NO_SHORTHAND, usage, null,
                false, false, null);
        }

    /**
     * Add a {@link File} list flag to this {@link FlagSet}.
     *
     * @param name          the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand     an optional single character shorthand for this flag as used on the command
     *                      line with the "-" prefix
     * @param usage         a description of this flag that will be used in help text
     * @param hidden        when {@code true} this flag will not appear in help text
     * @param passThru      this flag represents an argument that is passed-thru to Ecstasy code as an injection
     * @param passThruName  an optional alternative name for this flag when passed through to Ecstasy code
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFileList(String name, char shorthand, String usage, boolean hidden,
            boolean passThru, String passThruName)
        {
        return withFlag(new Flag<>(name, shorthand, usage, new FileListValue(), null, null,
                hidden, passThru, passThruName));
        }

    /**
     * Add a {@link File} list flag to this {@link FlagSet}.
     *
     * @param name   the long name of this flag as it appears on the command line with the "--" prefix
     * @param usage  a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFileList(String name, String usage)
        {
        return withFileList(name, Flag.NO_SHORTHAND, usage, false, false, null);
        }

    /**
     * Add a {@link File} flag to this {@link FlagSet}.
     *
     * @param name       the long name of this flag as it appears on the command line with the "--" prefix
     * @param shorthand  an optional single character shorthand for this flag as used on the command
     *                   line with the "-" prefix
     * @param usage      a description of this flag that will be used in help text
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withFileList(String name, char shorthand, String usage)
        {
        return withFileList(name, shorthand, usage, false, false, null);
        }

    /**
     * Add the default module path ("-L", "--module-path") flag to this {@link FlagSet}.
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withModulePath()
        {
        return withModulePath(false);
        }

    /**
     * Add the default verbose ("-v", "--verbose") flag to this {@link FlagSet}.
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withVerboseFlag()
        {
        return withBoolean(FLAG_VERBOSE, 'v', "Enables \"verbose\" logging and messages");
        }

    /**
     * Add the default version ("--version") flag to this {@link FlagSet}.
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withVersionFlag()
        {
        return withBoolean(FLAG_VERSION, "Displays the Ecstasy runtime version");
        }

    /**
     * Add the default module path ("-L", "--module-path") hidden flag to this {@link FlagSet}.
     *
     * @return this {@link FlagSet}
     */
    public FlagSet withModulePath(boolean fHidden)
        {
        return withFlag(new Flag<>(FLAG_MODULE_PATH, 'L',
                "Module path; a \"" + File.pathSeparator + "\"-delimited list of file and/or directory names",
                new ModuleRepoValue(), null, null, fHidden, false, null));
        }

    /**
     * Return the value from the module path flag.
     * <p>
     * This will be the value parsed from the command line, or an empty list
     * if no arguments have been parsed.
     *
     * @return the value from the module path flag
     */
    public List<File> getModulePath()
        {
        return getFileList(FLAG_MODULE_PATH);
        }

    /**
     * Return the value for the verbose flag ("-v" or "--verbose").
     * <p>
     * This will be the value parsed from the command line, or {@code false}
     * if no arguments have been parsed.
     *
     * @return the value for the verbose flag
     */
    public boolean isVerbose()
        {
        return getBoolean(FLAG_VERBOSE);
        }

    /**
     * Return the value for the version flag ("--version").
     * <p>
     * This will be the value parsed from the command line, or {@code false}
     * if no arguments have been parsed.
     *
     * @return the value for the verbose flag
     */
    public boolean isShowVersion()
        {
        return getBoolean(FLAG_VERSION);
        }

    /**
     * Return the value for the help flag ("--help" or "-h").
     * <p>
     * This will be the value parsed from the command line, or {@code false}
     * if no arguments have been parsed.
     *
     * @return the value for the verbose flag
     */
    public boolean isShowHelp()
        {
        return getBoolean(FLAG_HELP);
        }

    /**
     * Return the value for the named boolean flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not set or if
     * no arguments have been parsed.
     * <p>
     * Returns {@code false} if this set does not contain a
     * boolean flag with the specified name.
     *
     * @param name  the name of the boolean flag to obtain the value from
     *
     * @return  the value for the named boolean flag
     */
    public boolean getBoolean(String name)
        {
        Boolean b = get(name, Boolean.class);
        return b != null && b;
        }

    /**
     * Return the value for the named int flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns zero ({@code 0}) if this set does not contain an
     * int flag with the specified name or no default value
     * has been specified for the flag.
     *
     * @param name  the name of the int flag to obtain the value from
     *
     * @return  the value for the named int flag
     */
    public int getInt(String name)
        {
        Integer n = getInteger(name);
        return n == null ? 0 : n;
        }

    /**
     * Return the value for the named int flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns {@code null} if this set does not contain an
     * int flag with the specified name or no default value
     * has been specified for the flag.
     *
     * @param name  the name of the int flag to obtain the value from
     *
     * @return  the value for the named int flag
     */
    public Integer getInteger(String name)
        {
        return get(name, Integer.class);
        }

    /**
     * Return the value for the named string flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns {@code null} if this set does not contain a
     * string flag with the specified name.
     *
     * @param name  the name of the string flag to obtain the value from
     *
     * @return  the value for the named string flag
     */
    public String getString(String name)
        {
        return get(name, String.class);
        }

    /**
     * Return the value for the named string list flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns an empty list if this set does not contain a
     * string list flag with the specified name.
     *
     * @param name  the name of the string list flag to obtain the value from
     *
     * @return  the value for the named string list flag
     */
    public List<String> getStringList(String name)
        {
        return getList(name, String.class);
        }

    /**
     * Return the value for the named {@link File} flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns {@code null} if this set does not contain a
     * {@link File} flag with the specified name.
     *
     * @param name  the name of the {@link File} flag to obtain the value from
     *
     * @return  the value for the named {@link File} flag
     */
    public File getFile(String name)
        {
        return get(name, File.class);
        }

    /**
     * Return the value for the named {@link File} list flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns {@code null} if this set does not contain a
     * {@link File} list flag with the specified name.
     *
     * @param name  the name of the {@link File} list flag to obtain the value from
     *
     * @return  the value for the named {@link File} list flag
     */
    public List<File> getFileList(String name)
        {
        return getList(name, File.class);
        }

    /**
     * Return the value for the named {@link Map} of strings flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns an empty map if this set does not contain a
     * {@link Map} of strings flag with the specified name.
     *
     * @param name  the name of the {@link Map} of strings flag to obtain the value from
     *
     * @return  the value for the named {@link Map} of strings flag
     */
    public Map<String, String> getMapOfStrings(String name)
        {
        return getMap(name, String.class, String.class);
        }

    /**
     * Return the value for the named flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns {@code null} if this set does not contain a
     * flag with the specified name.
     * <p>
     * Returns {@code null} if the flag is present but not of the requested type.
     *
     * @param name  the name of the flag to obtain the value from
     * @param type  the expected type for the flag value
     * @param <V>   the expected type for the flag value
     *
     * @return  the value for the named list flag
     */
    @SuppressWarnings("unchecked")
    public <V> V get(String name, Class<V> type)
        {
        Flag<?> flag = formalFlags.get(name);
        if (flag != null)
            {
            Flag.Value<?> value = flag.getValue();
            if (String.class.equals(type))
                {
                String sValue = value.asString();
                if (sValue == null)
                    {
                    Flag.Value<?> dflt = flag.getDefaultValue();
                    sValue = dflt == null ? null : String.valueOf(dflt.get());
                    }
                return (V) sValue;
                }

            Type actual = Arrays.stream(value.getClass().getGenericInterfaces())
                    .filter(t -> t instanceof ParameterizedType)
                    .filter(t -> ((ParameterizedType) t).getRawType().equals(Flag.Value.class))
                    .map(t -> ((ParameterizedType) t).getActualTypeArguments()[0])
                    .findAny()
                    .orElse(type);

            if (type.equals(actual))
                {
                V oValue = (V) value.get();
                if (oValue == null)
                    {
                    Flag.Value<?> dflt = flag.getDefaultValue();
                    return dflt == null ? null : (V) dflt.get();
                    }
                return oValue;
                }
            }
        return null;
        }

    /**
     * Return the value for the named list flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed, or
     * if no arguments have been parsed.
     * <p>
     * Returns an empty list if this set does not contain a
     * flag with the specified name.
     * <p>
     * Returns an empty list if the flag is present but not of the requested type.
     *
     * @param name  the name of the list flag to obtain the value from
     * @param type  the expected type for the flag value
     * @param <V>   the expected type for the flag value
     *
     * @return  the value for the named list flag
     */
    @SuppressWarnings("unchecked")
    public <V> List<V> getList(String name, Class<V> type)
        {
        Flag<?> flag = actualFlags.get(name);
        if (flag != null)
            {
            Flag.Value<?> value  = flag.getValue();
            Type          actual = Arrays.stream(value.getClass().getGenericInterfaces())
                                        .filter(t -> t instanceof ParameterizedType)
                                        .filter(t -> ((ParameterizedType) t).getRawType().equals(Flag.MultiValue.class))
                                        .map(t -> ((ParameterizedType) t).getActualTypeArguments()[0])
                                        .findAny()
                                        .orElse(type);

            if (type.equals(actual))
                {
                Object oValue = value.get();
                if (oValue instanceof List<?>)
                    {
                    return Collections.unmodifiableList((List<V>) oValue);
                    }
                return List.of((V) oValue);
                }
            }
        return Collections.emptyList();
        }

    /**
     * Return the value for the named map flag.
     * <p>
     * This will be the value parsed from the command
     * line, or the default flag value if not parsed or
     * if no arguments have been parsed.
     * <p>
     * Returns an empty map if this set does not contain a
     * flag with the specified name.
     * <p>
     * Returns an empty list if the flag is present but not of the requested type.
     *
     * @param name       the name of the Map flag to obtain the value from
     * @param keyType    the expected type for the map keys
     * @param valueType  the expected type for the map values
     * @param <V>        the expected type for the map keys
     * @param <K>        the expected type for the map values
     *
     * @return  the value for the named list flag
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String name, Class<K> keyType, Class<V> valueType)
        {
        Flag<?> flag = actualFlags.get(name);
        if (flag != null)
            {
            Flag.Value<?>   value    = flag.getValue();
            Optional<Type>  optional = Arrays.stream(value.getClass().getGenericInterfaces())
                                            .filter(t -> t instanceof ParameterizedType)
                                            .filter(t -> ((ParameterizedType) t).getRawType().equals(Flag.MapValue.class))
                                            .findAny();

            Type actualKey   = optional.map(t -> ((ParameterizedType) t).getActualTypeArguments()[0]).orElse(keyType);
            Type actualValue = optional.map(t -> ((ParameterizedType) t).getActualTypeArguments()[1]).orElse(keyType);

            if (keyType.equals(actualKey) && valueType.equals(actualValue))
                {
                Object oValue = value.get();
                if (oValue instanceof Map<?, ?>)
                    {
                    return Collections.unmodifiableMap((Map<K, V>) oValue);
                    }
                }
            }
        return Collections.emptyMap();
        }

    /**
     * Return {@code true} if this flag set allows flags and non-flag
     * arguments to be interspersed on the command line.
     *
     * @return {@code true} if this flag set allows flags and non-flag
     *         arguments to be interspersed on the command line
     */
    public boolean isAllowInterspersed()
        {
        return allowInterspersed;
        }

    /**
     * Return {@code true} if this flag set allows unknown flags to be parsed.
     *
     * @return {@code true} if this flag set allows unknown flags to be parsed
     */
    public boolean isAllowUnknownFlags()
        {
        return allowUnknownFlags;
        }

    /**
     * Return an unmodifiable map of {@link Flag flags} added to this {@link FlagSet}
     * keyed by the {@link Flag#getName() name} of the flag.
     *
     * @return an unmodifiable map of {@link Flag flags} added to this {@link FlagSet}
     */
    public Map<String, Flag<?>> getFormalFlags()
        {
        return Collections.unmodifiableMap(formalFlags);
        }

    /**
     * Return an unmodifiable map of {@link Flag flags} with shorthands that
     * have been added to this {@link FlagSet} keyed by the
     * {@link Flag#getShorthand() shorthand} of the flag.
     *
     * @return an unmodifiable map of {@link Flag flags} with shorthands
     *         that have been added to this {@link FlagSet}
     */
    public Map<Character, Flag<?>> getShorthandFlags()
        {
        return Collections.unmodifiableMap(shorthandFlags);
        }

    /**
     * Return an unmodifiable map of {@link Flag flags} that were present on
     * the command line.
     *
     * @return an unmodifiable map of {@link Flag flags} that were present on
     *         the command line
     */
    public Map<String, Flag<?>> getActualFlags()
        {
        return Collections.unmodifiableMap(actualFlags);
        }

    /**
     * Determine whether a specified flag was seen when the command line was parsed.
     *
     * @param sFlag  the name of the flag
     *
     * @return {@code true} if the specified flag was seen when the command line was parsed
     */
    public boolean wasSpecified(String sFlag)
        {
        return actualFlags.containsKey(sFlag);
        }

    /**
     * Return any non-flag arguments parsed from the command line
     * before the "--" separator was parsed.
     * <p>
     * If no "--" separator was parsed this will be all the
     * non-flag arguments from the command line
     *
     * @return any non-flag arguments parsed from the command line
     *         before the "--" separator was parsed
     */
    public List<String> getArgumentsBeforeDashDash()
        {
        if (argsLengthAtDash == NO_DASH_DASH)
            {
            return Collections.unmodifiableList(arguments);
            }
        if (argsLengthAtDash == 0)
            {
            return Collections.emptyList();
            }
        return arguments.stream().limit(argsLengthAtDash).toList();
        }

    /**
     * Return any non-flag arguments parsed from the command line
     * after the "--" separator was parsed.
     * <p>
     * If no "--" separator was parsed this will be an empty list.
     *
     * @return any non-flag arguments parsed from the command line
     *         after the "--" separator was parsed
     */
    public List<String> getArgumentsAfterDashDash()
        {
        if (argsLengthAtDash == NO_DASH_DASH)
            {
            return Collections.emptyList();
            }
        return arguments.stream().skip(argsLengthAtDash).toList();
        }

    /**
     * Returns all the non-flag arguments parsed from the command line.
     *
     * @return all the non-flag arguments parsed from the command line
     */
    public List<String> getArguments()
        {
        return Collections.unmodifiableList(arguments);
        }

    /**
     * Returns all the pass-thru arguments parsed from the command line.
     * <p/>
     * The map returned will use the flag names for keys and the values will be converted
     * to String for single arguments, or string arrays for list arguments.
     *
     * @return all the pass-thru arguments parsed from the command line
     */
    public Map<String, ?> getPassThruArguments()
        {
        Map<String, Object> map = new HashMap<>();
        for (Flag<?> flag : formalFlags.values())
            {
            if (flag.isPassThru())
                {
                Flag.Value<?> value = flag.getValue();
                if (value.isMultiValue())
                    {
                    map.put(flag.getName(), value.asStrings());
                    }
                else
                    {
                    map.put(flag.getName(), value.asString());
                    }
                }
            }
        return map;
        }

    /**
     * Return the number of non-flag arguments seen before the
     * "--" separator was parsed.
     *
     * @return the number of non-flag arguments seen before the
     *         "--" separator was parsed
     */
    public int getArgsLengthAtDash()
        {
        return argsLengthAtDash;
        }

    /**
     * Determine whether a named flag has a no-argument default value.
     *
     * @param sFlag  the name of the flag
     *
     * @return {@code true} if the named flag had a no-argument
     *         default value.
     */
    public boolean hasNoArgDefault(String sFlag)
        {
        Flag<?> flag = sFlag.startsWith("--")
                ? formalFlags.get(sFlag.substring(2))
                : formalFlags.get(sFlag);
        return hasNoArgDefault(flag);
        }

    /**
     * Determine whether a named flag has a no-argument default value.
     *
     * @param cShorthand  the shorthand name of the flag
     *
     * @return {@code true} if the named flag had a no-argument
     *         default value.
     */
    public boolean hasNoArgDefault(char cShorthand)
        {
        Flag<?> flag = shorthandFlags.get(cShorthand);
        return hasNoArgDefault(flag);
        }

    private
    boolean hasNoArgDefault(Flag<?> flag)
        {
        Flag.Value<?> value = flag == null ? null : flag.getNoArgDefault();
        return value != null && value.get() != null;
        }

    /**
     * Merge this {@link FlagSet} into the target {@link FlagSet}.
     * <p>
     * Flags from this set will only be added to the target set if not already present.
     *
     * @param into  the {@link FlagSet} to merge this set into
     */
    public void mergeInto(FlagSet into)
        {
        for (Flag<?> flag : formalFlags.values())
            {
            if (into.formalFlags.putIfAbsent(flag.getName(), flag) == null)
                {
                if (flag.getShorthand() != Flag.NO_SHORTHAND)
                    {
                    into.shorthandFlags.put(flag.getShorthand(), flag);
                    }
                }
            }
        }

    /**
     * Return {@code true} if this {@link FlagSet} has parsed
     * a set of command line arguments.
     *
     * @return {@code true} if this {@link FlagSet} has parsed
     *         a set of command line arguments
     */
    public boolean isParsed()
        {
        return parsed;
        }

    /**
     * Returns an unmodifiable list of the last arguments parsed
     * by this flag set.
     *
     * @return an unmodifiable list of the last arguments parsed
     *         by this flag set
     */
    public List<String> getLastParsedArguments()
        {
        return Collections.unmodifiableList(lastParsedArguments);
        }

    /**
     * Parse the specified command line arguments and set
     * the appropriate flag values.
     *
     * @param args  the arguments to parse
     *
     * @throws ParseException     if the arguments are invalid
     * @throws ParseHelpException if the help message should be displayed due to an error
     */
    public void parse(String... args) throws ParseException
        {
        parse(new LinkedList<>(Arrays.asList(args)));
        }

    /**
     * Parse the specified command line arguments and set
     * the appropriate flag values.
     *
     * @param args  the arguments to parse
     *
     * @throws ParseException     if the arguments are invalid
     * @throws ParseHelpException if the help message should be displayed due to an error
     */
    public void parse(Collection<String> args) throws ParseException
        {
        parsed = true;
        arguments.clear();

        if (args == null || args.isEmpty())
            {
            lastParsedArguments = Collections.emptyList();
            return;
            }

        lastParsedArguments = new ArrayList<>(args);

        Queue<String> queueArgs = new LinkedList<>(args);
        while (!queueArgs.isEmpty())
            {
            String arg    = queueArgs.poll();
            int    argLen = arg.length();
            if (argLen == 0 || arg.charAt(0) != '-' || argLen == 1)
                {
                arguments.add(arg);
                if (!allowInterspersed)
                    {
                    arguments.addAll(queueArgs);
                    return;
                    }
                continue;
                }

            if (arg.charAt(1) == '-')
                {
                if (argLen == 2)
                    {
                    // we have the flags terminator "--"
                    argsLengthAtDash = arguments.size();
                    arguments.addAll(queueArgs);
                    break;
                    }
                // else we have a long name, e.g. --foo
                parseLongArg(arg, queueArgs);
                }
            else
                {
                // we have a shorthand (e.g. -f) or combined shorthand (e.g. -abc)
                parseShorthandArg(arg, queueArgs);
                }
            }
        }

    /**
     * Parse a long argument name.
     *
     * @param arg        the argument name
     * @param queueArgs  the remaining arguments to parse
     *
     * @throws ParseException if the arguments are invalid
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void parseLongArg(String arg, Queue<String> queueArgs) throws ParseException
        {
        String name     = arg.substring(2);
        String argValue = "";

        if (name.isEmpty() || name.charAt(0) == '-' || name.charAt(0) == '=')
            {
            throw new ParseException("Bad flag syntax: %s", arg);
            }

        int index = name.indexOf('=');
        if (index > 0)
            {
            argValue = name.substring(index + 1);
            name     = name.substring(0, index);
            }

        Flag flag = formalFlags.get(name);
        if (flag == null)
            {
            if (FLAG_HELP.equals(name))
                {
                throw new ParseHelpException();
                }
            else if (allowUnknownFlags)
                {
                // we have "--unknown arg" OR "--unknown=arg"
                if (argValue.isEmpty())
                    {
                    // we have "--unknown arg" so we need to strip the arg
                    stripUnknownFlagValue(queueArgs);
                    }
                return;
                }
            throw new ParseException("Unknown flag %s", arg);
            }

        Flag.Value<?> noArgDefault = flag.getNoArgDefault();
        Object        objValue     = null;
        String        stringValue     = null;
        if (!argValue.isEmpty())
            {
            // we have "--flag=arg"
            stringValue = argValue;
            }
        else if (noArgDefault != null)
            {
            // we have "--flag" (arg was optional as we have a default)
            objValue = noArgDefault.get();
            }
        else if (!queueArgs.isEmpty())
            {
            // we have "--flag arg"
            stringValue = queueArgs.poll();
            }
        else
            {
            throw new ParseException("Flag --%s requires an argument", name);
            }

        if (objValue != null)
            {
            flag.getValue().setValue(objValue);
            }
        else if (stringValue != null)
            {
            flag.getValue().setString(stringValue);
            }
        actualFlags.put(flag.getName(), flag);
        }

    /**
     * Parse a shorthand argument, or a chain of shorthand arguments.
     *
     * @param arg        the shorthand argument, or chain of shorthands to parse
     * @param queueArgs  the remaining arguments to parse
     *
     * @throws ParseException if the arguments are invalid
     */
    private void parseShorthandArg(String arg, Queue<String> queueArgs) throws ParseException
        {
        if (arg.length() == 2 || arg.charAt(2) == '=')
            {
            // we have -x or -x=arg
            parseSingleShorthandArg(arg.charAt(1), arg, 1, queueArgs);
            }
        else
            {
            // we have "-abc" or "-abc=arg"
            int index = 1;
            while (index < arg.length())
                {
                char shorthand = arg.charAt(index);
                index = parseSingleShorthandArg(shorthand, arg, index, queueArgs);
                }
            }
        }

    /**
     * Parse a single shorthand argument.
     *
     * @param arg        the argument to parse
     * @param queueArgs  the remaining arguments to parse
     *
     * @throws ParseException if the arguments are invalid
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int parseSingleShorthandArg(char shorthand, String arg, int index, Queue<String> queueArgs) throws ParseException
        {
        int indexOut  = index + 1;
        int argLength = arg.length() - indexOut;

        Flag flag = shorthandFlags.get(shorthand);
        if (flag == null)
            {
            if (shorthand == 'h')
                {
                throw new ParseHelpException();
                }
            else if (allowUnknownFlags)
                {
                // we have "-x arg" OR "-x=arg"
                if (argLength > 0 && arg.charAt(indexOut) == '=')
                    {
                    // we have "-x=arg"
                    return indexOut;
                    }
                // we have "-x arg" so we need to strip the possible following arg
                stripUnknownFlagValue(queueArgs);
                return indexOut;
                }
            throw new ParseException("Unknown short flag '%s' in %s", shorthand, arg);
            }

        Flag.Value<?> noArgDefault = flag.getNoArgDefault();
        Object        objValue     = null;
        String        stringValue  = null;

        if (argLength > 0 && arg.charAt(indexOut) == '=')
            {
            // we have -x=arg
            stringValue = arg.substring(indexOut + 1);
            indexOut    = arg.length();
            }
        else if (noArgDefault != null)
            {
            // we have "-x" (arg was optional as we have a default)
            objValue = noArgDefault.get();
            }
        else if (argLength > 0)
            {
            // we have "-xarg" (arg is not optional so remaining string is the value)
            stringValue = arg.substring(indexOut);
            indexOut    = arg.length();
            }
        else if (!queueArgs.isEmpty())
            {
            // we have "-x arg"
            stringValue = queueArgs.poll();
            }
        else
            {
            throw new ParseException("Flag -%s requires an argument", shorthand);
            }

        if (objValue != null)
            {
            flag.getValue().setValue(objValue);
            }
        else if (stringValue != null)
            {
            flag.getValue().setString(stringValue);
            }
        actualFlags.put(flag.getName(), flag);

        return indexOut;
        }

    /**
     * Remove any unknown flag values from the arguments.
     *
     * @param queueArgs  the arguments to remove unknown flags from
     */
    private void stripUnknownFlagValue(Queue<String> queueArgs)
        {
        if (queueArgs.isEmpty())
            {
            return;
            }
        String arg = queueArgs.peek();
        if (!arg.isEmpty() && arg.charAt(0) == '-')
            {
            //we have "--unknown --next-flag ..." so nothing to strip
            return;
            }
        // we have an arg so remove it
        queueArgs.poll();
        }

    // ----- inner class: ParseException -----------------------------------------------------------

    /**
     * An exception that occurs due to a parsing error.
     */
    public static class ParseException
            extends Exception
        {
        public ParseException(String message)
            {
            super(message);
            }

        public ParseException(String message, Object... args)
            {
            super(String.format(message, args));
            }
        }

    // ----- inner class: ParseException -----------------------------------------------------------

    /**
     * An exception that occurs due to a parsing error that
     * should result in a help message being displayed.
     */
    public static class ParseHelpException
            extends ParseException
        {
        public ParseHelpException()
            {
            super("help requested");
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * A value for {@link #argsLengthAtDash} to indicate the "--" separator was not parsed.
     */
    public static final int NO_DASH_DASH = -1;

    /**
     * The name of the help flag.
     */
    public static final String FLAG_HELP = "help";

    /**
     * The name of the module path flag.
     */
    public static final String FLAG_MODULE_PATH = "module-path";

    /**
     * The name of the help flag.
     */
    public static final String FLAG_VERBOSE = "verbose";

    /**
     * The name of the help flag.
     */
    public static final String FLAG_VERSION = "version";

    // ----- data members --------------------------------------------------------------------------

    /**
     * {@code true} to allow interspersed flag/non-flag arguments.
     */
    private final boolean allowInterspersed;

    /**
     * {@code true} to allow unknown flags when parsing.
     */
    private final boolean allowUnknownFlags;

    /**
     * The valid flags for this {@link FlagSet} keyed by name.
     */
    private final Map<String, Flag<?>> formalFlags = new TreeMap<>();

    /**
     * The valid flags for this {@link FlagSet} keyed by shorthand.
     */
    private final Map<Character, Flag<?>> shorthandFlags = new TreeMap<>();

    /**
     * The actual flags parsed from the command line.
     */
    private final Map<String, Flag<?>> actualFlags = new TreeMap<>();

    /**
     * The non-flag command line arguments.
     */
    private final List<String> arguments = new ArrayList<>();

    /**
     * The index into {@link #arguments} when "--" was found on the command line,
     * or {@code -1} if there was no "--" on the command line.
     */
    private int argsLengthAtDash = NO_DASH_DASH;

    /**
     * {@code true} if the state of this {@link FlagSet} has been set by a call to {@link #parse(String[])}.
     */
    private boolean parsed;

    /**
     * The last list of arguments parsed by this flag set.
     */
    private List<String> lastParsedArguments = Collections.emptyList();
    }
