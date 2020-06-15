/**
 * Represents all expressions that represents a type.
 */
@Abstract const TypeExpression
        extends Expression
    {
    conditional Type resolveType(TypeSystem typeSystem)
        {
        Class clz = &this.actualClass;
        TODO($"clz={clz}");
        }
    }
