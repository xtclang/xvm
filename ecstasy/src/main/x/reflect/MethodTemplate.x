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

    /**
     * True iff the method is a constructor.
     */
    @RO Boolean isConstructor.get()
        {
        return name == "construct";
        }

    /**
     * The method parameters.
     */
    ParameterTemplate[] parameters;

    /**
     * The method return values.
     */
    ParameterTemplate[] returns;

    @Override
    @RO String path.get()
        {
        // the method name is always the same as the its parent's (multimethod)
        // TODO GG: use the parameter types to fill inside the parenthesis
        return parent.path + "()";
        }
    }
