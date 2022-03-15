/**
 * A Hasher for hashing String values in a case insensitive manner.
 */
static const CaseInsensitiveHasher
        implements Hasher<String>
    {
    @Override
    Int hashOf(String value)
        {
        @Unchecked Int hash = 982_451_653;      // start with a prime number

        Int len  = value.size;
        if (len <= 0x40)
            {
            for (Char c : value)
                {
                hash = hash * 31 + c.lowercase.toInt64();
                }
            }
        else
            {
            // just sample ~60 characters from across the entire length of the string
            for (Int offset = 0, Int step = (len >>> 6) + 1; offset < len; offset += step)
                {
                hash = hash * 31 + value[offset].lowercase.toInt64();
                }
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
        while (Char c1 := it1.next(), Char c2 := it2.next())
            {
            if (c1 != c2 && c1.lowercase != c2.lowercase)
                {
                return False;
                }
            }

        return True;
        }
    }
