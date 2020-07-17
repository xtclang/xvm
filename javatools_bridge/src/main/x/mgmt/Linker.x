import ecstasy.mgmt.Container;
import ecstasy.mgmt.Container.ApplicationControl;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;

import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

class Linker
        implements Container.Linker
    {
    @Override
    String validate(Byte[] bytes)
        {TODO("Native");}

    @Override
    FileTemplate loadFileTemplate(Byte[] bytes)
        {TODO("Native");}

    @Override
    (TypeSystem typeSystem, ApplicationControl) resolveAndLink(ModuleTemplate template,
            ModuleRepository repository, ResourceProvider injector, Module[] sharedModules = [])
        {TODO("Native");}

    @Override
    (TypeSystem typeSystem, ApplicationControl) link(
            (ModuleTemplate | Module)[] modules, ResourceProvider injector)
        {TODO("Native");}
    }
