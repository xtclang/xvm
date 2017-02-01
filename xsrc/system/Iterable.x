/**
 * TODO
 */
interface Iteratable<ElementType>
    {
    /**
     * TODO
     */
    Iterator<ElementType> iterator();

    /**
     * TODO
     */
    Iterator<ElementType> iterator(function Boolean fn(ElementType))
        {
        return new Iterator<ElementType>()
            {
            Iterator iter = iterator();

            conditional ElementType next()
                {
                while (ElementType value : iter.next())
                    {
                    if (fn(value))
                        {
                        return (true, value);
                        }
                    }

                return false;
                }
            }
        }
        
    /**
     * TODO
     */
    Void forEach(function Void(ElementType) fn)
        {
        for (ElementType value : this)
            {
            fn(value);
            }
        }
    }
