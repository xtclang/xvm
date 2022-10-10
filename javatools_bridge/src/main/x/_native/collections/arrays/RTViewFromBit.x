/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<Bit> as an
 * ArrayDelegate<Element>.
 */
class RTViewFromBit<Element extends (Number|Nibble|Boolean)>
        extends RTDelegate<Element>
    {
    private RTDelegate<Bit> source;
    }