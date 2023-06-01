/**
 * The native ArrayDelegate class that is used to view an ArrayDelegate<ViewType> as an
 * ArrayDelegate<Bit>.
 */
class RTViewToBit<ViewType extends (Number|Nibble|Boolean)>
        extends RTDelegate<Bit> {

    private RTDelegate<ViewType> source;
}