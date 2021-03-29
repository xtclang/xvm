/**
 * Represents a variable in a URI template.
 */
const UriMatchVariable(String name, Char modifier, Char operator)
        implements Orderable
        implements Hashable
    {
    /**
     * @return True if the variable is exploded
     */
    Boolean exploded.get()
        {
        return modifier == '*';
        }

    /**
     * @return True if the variable part of a query.
     */
    Boolean isQuery.get()
        {
        return switch (operator)
            {
            case '?', '#', '&': True;
            default           : False;
            };
        }

    /**
     * An optional variable is one that will allow the route to match
     * if it is not present.
     *
     * @return True if the variable is optional
     */
    Boolean isOptional.get()
        {
        return switch (operator)
            {
            case '?', '#', '&', '/': True;
            default                : False;
            };
        }

    // ----- Hashable & Orderable ------------------------------------------------------------------

    @Override
    static Int hashCode(UriMatchVariable value)
        {
        return value.name.hashCode();
        }

    @Override
    static Boolean equals(UriMatchVariable value1, UriMatchVariable value2)
        {
        return value1.name == value2.name;
        }

    @Override
    static Ordered compare(UriMatchVariable value1, UriMatchVariable value2)
        {
        return value1.name <=> value2.name;
        }
    }
