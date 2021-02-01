/**
 * Represents the compiled information for a multi-method. A multi-method represents the set of
 * methods and/or functions that are identified by a common name. A multi-method template can only
 * contain method templates, and method templates can only be contained by a multi-method template.
 */
interface MultiMethodTemplate
        extends ComponentTemplate
    {
    @Override
    MethodTemplate[] children();
    }
