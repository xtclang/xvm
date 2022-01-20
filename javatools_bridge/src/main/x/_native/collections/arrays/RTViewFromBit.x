/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<Bit> as an
 * ArrayDelegate<NumType>.
 */
class RTViewFromBit<NumType extends Number>
        extends RTDelegate<NumType>
    {
    private RTDelegate<Bit> source;
    }
