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
    // TODO remove the temporary method
    @Override FileTemplate loadFileTemplate(File file) {TODO("Native");}

    @Override
    Control loadAndLink(
            ModuleSpec        primarySpec,
            Model             model           = Secure,
            ModuleRepository? repository      = Null,
            ResourceProvider? provider        = Null,
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
            assert primaryModule := repository.getModule(primarySpec) as $"Missing {primarySpec}";
            }

        ModuleTemplate[] additionalModules = new Array<ModuleTemplate>(additionalSpecs.size, i ->
            {
            ModuleSpec spec = additionalSpecs[i];
            if (spec.is(ModuleTemplate))
                {
                return spec;
                }
            assert:arg repository != Null;
            assert ModuleTemplate template :=
                    repository.getModule(spec) as $"Missing additional module {spec}";
            return template;
            });

        return resolveAndLink(primaryModule, model, repository, provider,
            sharedModules, additionalModules, namedConditions);
        }

    @Override
    String toString()
        {
        return "Linker";
        }

    /**
     * Native implementation.
     */
    Control resolveAndLink(
            ModuleTemplate    primaryModule,
            Model             model,
            ModuleRepository? repository,
            ResourceProvider? provider,
            Module[]          sharedModules,
            ModuleTemplate[]  additionalModules,
            String[]          namedConditions)
        {TODO("Native");}
    }