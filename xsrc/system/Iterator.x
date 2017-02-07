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
    Void forEach(function Void(ElementType) process)
        {
        while (ElementType value : next())
            {
            process(value);
            }
        }
    }
