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

    /**
     * TODO:
     *
     * Note: this method does NOT have a super; it does NOT narrow to<Object[]>()
     */
    Byte[] to<Byte[]>();

    // this declaration is necessary so that the non-override to<Byte[]>() method is allowed
    @Override
    Const[] to<Const[]>();

    @Override
    String to<String>()
        {
        StringBuffer buf = new StringBuffer(estimateStringLength());
        appendTo(buf);
        return buf.to<String>();
        }

    @Override
    @RO Int hash;
    }
