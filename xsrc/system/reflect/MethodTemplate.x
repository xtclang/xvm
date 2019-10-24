/**
 * Represents the compiled information for a method.
 */
interface MethodTemplate
        extends Template
    {
    @Override
    @RO MultiMethodTemplate!? parent;
    }
