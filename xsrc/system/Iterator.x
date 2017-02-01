/**
 * TODO
 */
interface Iterator<ElementType>
    {
    /**
     * TODO
     */
    conditional ElementType next();

    /**
     * TODO
     */
    Void forEach(function Void(ElementType) fn)
        {
        while (ElementType value : next())
            {
            fn(value);
            }
        }
    }
