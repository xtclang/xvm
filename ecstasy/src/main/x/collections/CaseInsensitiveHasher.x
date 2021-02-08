/**
 * A Hasher for hashing String values in a case insensitive manner.
 */
const CaseInsensitiveHasher
        implements Hasher<String>
    {
    @Override
    Int hashOf(String value)
        {
        Int hash = 0;
        for (Char c : value.toCharArray())
            {
            hash += c.lowercase.toInt64();
            }
        return hash;
        }

    @Override
    Boolean areEqual(String value1, String value2)
        {
        if (value1.size != value2.size)
            {
            return False;
            }

        Iterator<Char> it1 = value1.iterator();
        Iterator<Char> it2 = value2.iterator();
        while (Char c1 := it1.next())
            {
            if (Char c2 := it2.next())
                {
                return c1.lowercase == c2.lowercase;
                }
            else
                {
                // should not get here as the sizes should be the same
                return False;
                }
            }

        return True;
        }
    }
