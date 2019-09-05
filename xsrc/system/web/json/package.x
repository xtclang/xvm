package json
    {
    /**
     * JSON primitive types are all JSON values except for arrays and objects.
     */
    typedef (Nullable | Boolean | IntLiteral | FPLiteral | String) Primitive;

    /**
     * JSON types include primitive types, document types, and array types.
     */
    typedef (Primitive | Doc | Array) FieldType;
    }