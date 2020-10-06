import ecstasy.mgmt.ModuleRepository;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

/**
 * The native reflected FileTemplate implementation.
 */
class RTFileTemplate
        extends RTComponentTemplate
        implements FileTemplate
    {
    @Override
    ModuleTemplate mainModule.get()         {TODO("native");}

    @Override
    Boolean resolved.get()                  {TODO("native");}

    @Override
    RTFileTemplate resolve(ModuleRepository repository)
                                            {TODO("native");}

    @Override
    ModuleTemplate getModule(String name)   {TODO("native");}
    }
