package org.xvm.tool.flag;

import org.xvm.util.Handy;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command line value representing zero or more key value pairs.
 * <p/>
 * The flag may be specified multiple times on the command line to allow
 * multiple key value paris to be specified. Alternatively the value for the
 * flag can be a comma-delimited list of key value pairs.
 * <p/>
 * The format of the key value pair is a string "key=value"
 */
public class StringMapValue
        extends BaseMapValue<String, String>
    {
    public StringMapValue()
        {
        }

    public StringMapValue(Map<? extends String, ? extends String> values)
        {
        super(values);
        }

    @Override
    public String asString()
        {
        return value.entrySet().stream()
                .map(e -> Handy.quotedString(e.getKey()) + "=" + Handy.quotedString(e.getValue()))
                .collect(Collectors.joining(","));
        }

    @Override
    protected Map<String, String> parseStringMap(String s)
        {
        return Handy.parseStringMap(s);
        }
    }
