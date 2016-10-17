interface Enumeration<T>
    {
    @ro int Count;
    @ro String[] Names;
    @ro T[] Values;
    @ro Map<String, T> NameTable;
    }

interface Enum
        extends Value
    {
    @ro Enumeration enumeration;

    @ro int Ordinal;
    @ro String Name;

    // TODO some sort of "next / previous" that allows iteration over the individual elements?
    }
