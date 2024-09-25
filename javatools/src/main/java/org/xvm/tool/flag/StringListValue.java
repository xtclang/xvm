package org.xvm.tool.flag;

import org.xvm.util.Handy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A flag value holding a list of strings.
 */
public class StringListValue
        implements Flag.MultiValue<String>
    {
    /**
     * Create a {@link StringListValue} with an empty list.
     */
    public StringListValue()
        {
        }

    /**
     * Create a {@link StringListValue}.
     *
     * @param values  the initial list of string values
     */
    public StringListValue(String... values)
        {
        this(List.of(values));
        }

    /**
     * Create a {@link StringListValue}.
     *
     * @param values  the initial list of string values
     */
    public StringListValue(Collection<? extends String> values)
        {
        this.values.addAll(values);
        }

    @Override
    public String asString()
        {
        return values.stream().map(Handy::quotedString).collect(Collectors.joining(","));
        }

    @Override
    public void setString(String s)
        {
        append(s);
        }

    @Override
    public void setValue(List<String> list)
        {
        values.clear();
        if (list != null)
            {
            values.addAll(list);
            }
        }

    @Override
    public List<String> get()
        {
        return values;
        }

    @Override
    public void append(String s)
        {
        values.add(s);
        }

    @Override
    public void replace(String[] as)
        {
        values.clear();
        values.addAll(Arrays.asList(as));
        }

    @Override
    public String[] asStrings()
        {
        return values.toArray(Handy.NO_ARGS);
        }

    // ----- data members --------------------------------------------------------------------------

    private final List<String> values = new ArrayList<>();
    }
