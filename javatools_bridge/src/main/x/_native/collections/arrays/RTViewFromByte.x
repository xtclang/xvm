/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<Byte> as an
 * ArrayDelegate<Element>.
 */
class RTViewFromByte<Element extends Number>
        extends RTDelegate<Element>
    {
    private RTDelegate<Byte> source;
    }