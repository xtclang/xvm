interface Const
        extends collections.Hashable
        extends Orderable
    {
    /**
     * The default implementation of comparison for Const implementations is to compare each of
     * the fields.
     */
    static <CompileType extends Const> Ordered compare(CompileType value1, CompileType value2)
        {
        TODO -- native
        }

    /**
     * The default implementation of comparison-for-equality for Const implementations is to
     * compare each of the fields for equality.
     */
    static <CompileType extends Const> Boolean equals(CompileType value1, CompileType value2)
        {
        TODO -- native
        }

    /**
     * TODO
     */
    Byte[] to<Byte[]>();

    @Override
    @RO Int hash;
    }
