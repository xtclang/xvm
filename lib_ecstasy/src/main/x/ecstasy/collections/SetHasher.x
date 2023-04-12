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
// TODO GG
// Type mismatch: "ecstasy:collections.Hasher<CompileType.Element>" expected, "ecstasy:collections.Hasher<CompileType.Element> | Boolean" found. ("CompileType.Element.hashed() ?: assert")
// Hasher<CompileType.Element> hasher = CompileType.Element.hashed() ?: assert;
        assert Hasher<CompileType.Element> hasher := CompileType.Element.hashed();

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
        // TODO GG (is <> necessary here?)
        return Set<CompileType.Element>.equals(value1, value2);
        }
    }
