/**
 * Represents the compiled information for a property.
 */
interface PropertyTemplate
        extends ComponentTemplate
    {
    /**
     * The TypeTemplate representing the type of this property.
     */
     @RO TypeTemplate type;

    /**
     * TODO GG
     */
    @RO Boolean isConstant;

    /**
     * TODO CP: can it be a PropertyTemplate for a const value?
     */
    conditional Const hasInitialValue();

    /**
     * TODO GG
     */
    conditional (function Const ()) hasInitializer();
    }
