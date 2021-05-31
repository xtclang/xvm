/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<Byte> as an
 * ArrayDelegate<NumType>.
 */
class RTViewFromByte<NumType extends Number>
        extends RTDelegate<NumType>
    {
    private RTDelegate<Byte> source;
    }
