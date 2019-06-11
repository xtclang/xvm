interface Const
        extends collections.Hashable
        extends Orderable
        extends Stringable
    {
    /**
     * The default implementation of comparison for Const implementations is to compare each of
     * the fields.
     */
    static <CompileType extends Const> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1 <=> value2;
        }

    /**
     * The default implementation of comparison-for-equality for Const implementations is to
     * compare each of the fields for equality.
     */
    static <CompileType extends Const> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1 == value2;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer(estimateStringLength());
        appendTo(buf);
        return buf.toString();
        }

    @Override
    @RO Int hash;
    }
