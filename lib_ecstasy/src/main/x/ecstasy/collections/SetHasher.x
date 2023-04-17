/**
 * The SetHasher is simple implementation of the [Hashable] funky interface for sets with hashable
 * elements.
 */
mixin SetHasher<Element extends Hashable>
        into Set<Element>
        implements Hashable
    {
    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends SetHasher> Int64 hashCode(CompileType value)
        {
        Hasher<CompileType.Element> hasher = CompileType.Element.hashed() ?: assert;

        Int64 hash = value.size.toInt64();
        for (CompileType.Element element : value)
            {
            hash ^= hasher.hashOf(element);
            }
        return hash;
        }

    @Override
    static <CompileType extends SetHasher> Boolean equals(CompileType value1, CompileType value2)
        {
        return Set.equals(value1, value2);
        }
    }