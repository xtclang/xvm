import ecstasy.mgmt.Container;
import ecstasy.mgmt.Container.Control;
import ecstasy.mgmt.Container.Model;
import ecstasy.mgmt.Container.ModuleSpec;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;

import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

class ContainerLinker
        implements Container.Linker
    {
    @Override (TypeSystem typeSystem, Control control) loadAndLink(ModuleSpec primaryModule, Model
            model, ModuleRepository? repository, ResourceProvider? injector, Module[] sharedModules,
            ModuleSpec[] additionalModules, String[] namedConditions)              {TODO("Native");}

    // TODO remove temporary methods
    @Override String       validate(Byte[] bytes)                                  {TODO("Native");}
    @Override FileTemplate loadFileTemplate(Byte[] bytes)                          {TODO("Native");}
    @Override (TypeSystem, Control) resolveAndLink(ModuleTemplate template, ModuleRepository
            repository, ResourceProvider injector, Module[] sharedModules)         {TODO("Native");}
    @Override (TypeSystem typeSystem, Control) link((ModuleTemplate | Module)[] modules,
            ResourceProvider injector)                                             {TODO("Native");}
    }
