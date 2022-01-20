/**
 * Represents all expressions that represents a type.
 */
@Abstract const TypeExpression
        extends Expression
    {
    /**
     * Resolve this `TypeExpression` into an Ecstasy `Type` from the provided [TypeSystem].
     *
     * @param typeSystem      the TypeSystem to resolve against
     * @param hideExceptions  pass True to catch type exceptions and return them as `False` instead
     *
     * @return True iff the type could be successfully resolved
     * @return (conditional) the type
     *
     * @throws InvalidType  if a type exception occurs and `hideExceptions` is not specified
     */
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        Class clz = &this.actualClass;
        TODO($"clz={clz}");
        }
    }
