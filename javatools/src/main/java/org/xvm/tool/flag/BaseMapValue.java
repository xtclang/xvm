package org.xvm.tool.flag;

import java.util.HashMap;
import java.util.Map;

/**
 * A base class for flag values that are types of maps.
 *
 * @param <K>  the type of the map keys
 * @param <V>  the type of the map values
 */
public abstract class BaseMapValue<K, V>
        implements Flag.MapValue<K, V>
    {
    /**
     * Create a {@link BaseMapValue} with an empty map.
     */
    protected BaseMapValue()
        {
        this.value = new HashMap<>();
        }

    protected BaseMapValue(Map<? extends K, ? extends V> values)
        {
        this.value = new HashMap<>();
        if (values != null)
            {
            this.value.putAll(values);
            }
        }


    @Override
    public void setString(String s)
        {
        Map<K, V> map = parseStringMap(s);
        if (map != null)
            {
            value.putAll(map);
            }
        }

    @Override
    public void setValue(Map<K, V> value)
        {
        this.value.clear();
        if (value != null)
            {
            this.value.putAll(value);
            }
        }

    @Override
    public Map<K, V> get()
        {
        return value;
        }

    @Override
    public void append(String s)
        {
        Map<K, V> map = parseStringMap(s);
        if (map != null)
            {
            value.putAll(map);
            }
        }

    @Override
    public void replace(String[] values)
        {
        Map<K, V> map = new HashMap<>();
        if (values != null)
            {
            for (String value : values)
                {
                Map<K, V> parsed = parseStringMap(value);
                if (parsed != null)
                    {
                    map.putAll(parsed);
                    }
                }
            }
        this.value.clear();
        this.value.putAll(map);
        }

    @Override
    public String[] asStrings()
        {
        return value.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        }

    /**
     * Parse the specified string into a map.
     *
     * @param s  the string to parse
     *
     * @return  the map parsed from the string
     */
    protected abstract Map<K, V> parseStringMap(String s);

    // ----- data members --------------------------------------------------------------------------

    /**
     * The flag value.
     */
    protected final Map<K, V> value;
    }
