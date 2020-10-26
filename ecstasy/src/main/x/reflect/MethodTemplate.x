/**
 * Represents the compiled information for a method.
 */
interface MethodTemplate
        extends ComponentTemplate
    {
    @Override
    @RO MultiMethodTemplate parent;

    /**
    * The method annotations. The order of the annotations in the array is
    * "left-to-right"; so for example an annotated method
    *     @A1 @A2 void foo();
    * would produce the `annotations` array holding `A1` at index zero.
    */
    Annotation[] annotations;
    }
