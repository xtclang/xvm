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
     * TODO:
     *
     * Note: this method does NOT have a super; it does NOT narrow to<Object[]>()
     */
    Byte[] to<Byte[]>();

    // this declaration is necessary so that the non-override to<Byte[]>() method is allowed
    @Override
    Const[] to<Const[]>();

    @Override
    @RO Int hash;
    }
