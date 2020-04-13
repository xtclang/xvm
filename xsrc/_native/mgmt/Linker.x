import ecstasy.TypeSystem;

import ecstasy.mgmt.Container;
import ecstasy.mgmt.Container.ApplicationControl;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;

import ecstasy.reflect.ModuleTemplate;

class Linker
        implements Container.Linker
    {
    construct()
        {
        }

    @Override
    String validate(Byte[] bytes)
        {TODO("Native");}

    @Override
    (TypeSystem typeSystem, ApplicationControl) resolveAndLink(immutable Byte[] bytes,
            ModuleRepository repository, ResourceProvider injector, Module[] sharedModules = [])
        {TODO("Native");}

    @Override
    (TypeSystem typeSystem, ApplicationControl) link(
            (ModuleTemplate | Module)[] modules, ResourceProvider injector)
        {TODO("Native");}
    }
