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
     * @return True iff the property represents a named constant value
     */
    @RO Boolean isConstant;

    /**
     * TODO CP: can it be a PropertyTemplate for a const value?
     */
    conditional Const hasInitialValue();

    /**
     * Check if the property is initialized using an initializer function.
     *
     * @return True iff the property is initialized using an initializer function
     * @return (optional) the MethodTemplate representing the initializer of the property
     */
    conditional MethodTemplate hasInitializer();
    }
