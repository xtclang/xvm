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

    /**
    * The property annotations. These are the annotations that apply to the property itself (i.e.
    * they mix into `Property`), such as `@RO`. The order of the annotations in the array is
    * "left-to-right"; so for example an annotated property:
    *     @A1 @A2 List list = ...
    * would produce the `annotations` array holding `A1` at index zero.
    */
    @RO immutable AnnotationTemplate[] annotations;

    /**
     * Check if this template has a specified annotation.
     *
     * @return True iff there is an annotation of the specified name
     * @return the corresponding `AnnotationTemplate` (optional)
     */
    conditional AnnotationTemplate findAnnotation(String annotationName)
        {
        for (AnnotationTemplate annotation : annotations)
            {
            if (annotation.template.displayName == annotationName)
                {
                return True, annotation;
                }
            }

        return False;
        }
    }