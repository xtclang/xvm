/**
 * A Hasher for hashing String values in a case insensitive manner.
 */
static const CaseInsensitive
        implements Hasher<String>
    {
    @Override
    Int hashOf(String value)
        {
        @Unchecked Int hash = 982_451_653;      // start with a prime number
        for (Char char : value)
            {
            hash = hash * 31 + char.lowercase.toInt64();
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

        Iterator<Char> iter1 = value1.iterator();
        Iterator<Char> iter2 = value2.iterator();
        while (Char char1 := iter1.next(), Char char2 := iter2.next())
            {
            if (char1 != char2 && char1.lowercase != char2.lowercase)
                {
                return False;
                }
            }

        return True;
        }

    /**
     * This is a [Type.Orderer] for [String] that uses case insensitive comparison.
     */
    static Ordered compare(String value1, String value2)
        {
        Int len1 = value1.size;
        Int len2 = value2.size;
        for (Int offset = 0, Int len = len1.minOf(len2); offset < len; ++offset)
            {
            Char char1 = value1[offset];
            Char char2 = value2[offset];
            if (char1 != char2)
                {
                char1 = char1.lowercase;
                char2 = char2.lowercase;
                if (char1 != char2)
                    {
                    return char1 <=> char2;
                    }
                }
            }

        return len1 <=> len2;
        }
    }
