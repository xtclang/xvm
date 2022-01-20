import ecstasy.reflect.ModuleTemplate;


/**
 * The native reflected ModuleTemplate implementation.
 */
class RTModuleTemplate
        extends RTClassTemplate
        implements ModuleTemplate
    {
    @Override
    String qualifiedName.get()                            {TODO("native");}

    @Override
    immutable Map<String, String> moduleNamesByPath.get() {TODO("native");}
    }
