/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<NumType> as an
 * ArrayDelegate<Bit>.
 */
class RTViewToBit<NumType extends (Number|Nibble)>
        extends RTDelegate<Bit>
    {
    private RTDelegate<NumType> source;
    }