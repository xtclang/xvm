import ecstasy.mgmt.Container;
import ecstasy.mgmt.Container.Control;
import ecstasy.mgmt.Container.Model;
import ecstasy.mgmt.Container.ModuleSpec;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;

import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

service ContainerLinker
        implements Container.Linker
    {
    // TODO remove temporary methods
    @Override String       validate(Byte[] bytes)         {TODO("Native");}
    @Override FileTemplate loadFileTemplate(Byte[] bytes) {TODO("Native");}

    @Override
    (TypeSystem typeSystem, Control control) loadAndLink(
            ModuleSpec        primarySpec,
            Model             model           = Secure,
            ModuleRepository? repository      = Null,
            ResourceProvider? injector        = Null,
            Module[]          sharedModules   = [],
            ModuleSpec[]      additionalSpecs = [],
            String[]          namedConditions = [])
        {
        ModuleTemplate primaryModule;
        if (primarySpec.is(ModuleTemplate))
            {
            primaryModule = primarySpec;
            }
        else
            {
            assert:arg repository != Null;
            primaryModule = repository.getModule(primarySpec);
            }

        ModuleTemplate[] additionalModules = new Array<ModuleTemplate>(additionalSpecs.size, i ->
            {
            ModuleSpec spec = additionalSpecs[i];
            if (spec.is(ModuleTemplate))
                {
                return spec;
                }
            assert:arg repository != Null;
            return repository.getModule(spec);
            });

        return resolveAndLink(primaryModule, model, repository, injector,
            sharedModules, additionalModules, namedConditions);
        }

    /**
     * Native implementation.
     */
    (TypeSystem, Control) resolveAndLink(
            ModuleTemplate    primaryModule,
            Model             model,
            ModuleRepository? repository,
            ResourceProvider? injector,
            Module[]          sharedModules,
            ModuleTemplate[]  additionalModules,
            String[]          namedConditions)
        {TODO("Native");}
    }