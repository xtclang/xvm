import collections.ListMap;
import io.IOException;

/**
 * A JSON document.
 */
class Doc
    {
    // ----- constructors --------------------------------------------------------------------------

    construct()
        {
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * A JSON document is a sequence of key/value pairs, in which the key is always a String, and
     * the value is always a JSON value.
     */
    protected/private ListMap<String, FieldType> contents;

    /**
     * A map of the fields of this document, keyed by name.
     */
    @RO Map<String, FieldType> fields.get()
        {
        TODO create a virtual Map class that validates operations and values, and delegates storage to the `contents` property
        }

    Boolean getBoolean(String key, Boolean defaultValue = False)
        {
        if (FieldType value := contents.get(key))
            {
            if (value.is(Boolean))
                {
                return value;
                }
            else if (value != Null)
                {
                // only a Boolean or a Null value is acceptable; any other value is an error
                TODO throw new fieldWasWrongType(key, Boolean, &value.ActualType)
                }
            }

        return defaultValue;
        }

    Boolean requireBoolean(String key)
        {
        if (FieldType value := contents.get(key))
            {
            if (value.is(Boolean))
                {
                return value;
                }
            else if (value != Null)
                {
                throw wrongType(key, "Boolean", value);
                }
            }

        throw missingField(key);
        }


    // ----- helpers -------------------------------------------------------------------------------

    static Boolean valid(FieldType value)
        {
        if (value.is(Nullable)   ||
            value.is(Boolean)    ||
            value.is(String)     ||
            value.is(IntLiteral) ||
            value.is(FPLiteral)  ||
            value.is(Doc) )
            {
            return True;
            }

        if (value.is(Array))
            {
            if (value.is(Array<FieldType>))
                {
                for (val val : value)
                    {
                    if (!valid(val))
                        {
                        return False;
                        }
                    }
                return True;
                }
            }

        return False;
        }

    Exception missingField(String name)
        {
        throw new IOException($"missing JSON field {name}");
        }

    Exception wrongType(String location, String requiredType, FieldType actualValue)
        {
        throw new IOException($"JSON value for \"{location}\" is required to be of type"
                + " {requiredType}, but is of type {&actualValue.ActualType.toString()}");
        }
    }
