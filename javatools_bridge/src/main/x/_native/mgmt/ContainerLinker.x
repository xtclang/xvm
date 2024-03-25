import ecstasy.mgmt.Container;
import ecstasy.mgmt.Container.Control;
import ecstasy.mgmt.Container.InjectionKey;
import ecstasy.mgmt.Container.Model;
import ecstasy.mgmt.Container.ModuleSpec;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;

import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

service ContainerLinker
        implements Container.Linker {

    @Override
    InjectionKey[] collectInjections(ModuleTemplate template, String[] definedNames = []) {
        (String[] names, Type[] types) = collectInjectionsImpl(template, definedNames);
        return new InjectionKey[names.size](i -> new InjectionKey(names[i], types[i])).freeze(True);
    }

    @Override
    Control loadAndLink(
            ModuleSpec        primarySpec,
            Model             model           = Secure,
            ModuleRepository? repository      = Null,
            ResourceProvider? provider        = Null,
            Module[]          sharedModules   = [],
            ModuleSpec[]      additionalSpecs = [],
            String[]          definedNames    = []) {
        ModuleTemplate primaryModule;
        if (primarySpec.is(ModuleTemplate)) {
            primaryModule = primarySpec;
        } else {
            assert:arg repository != Null;
            assert primaryModule := repository.getModule(primarySpec) as $"Missing {primarySpec}";
        }

        ModuleTemplate[] additionalModules = new ModuleTemplate[additionalSpecs.size](i -> {
            ModuleSpec spec = additionalSpecs[i];
            if (spec.is(ModuleTemplate)) {
                return spec;
            }
            assert:arg repository != Null;
            assert ModuleTemplate template :=
                    repository.getModule(spec) as $"Missing additional module {spec}";
            return template;
        });

        return resolveAndLink(primaryModule, model, repository, provider,
            sharedModules, additionalModules, definedNames);
    }

    @Override
    FileTemplate loadFileTemplate(File file) {
        return loadFileTemplate(file.contents);
    }

    @Override
    String toString() {
        return "Linker";
    }

    // ----- native helpers ------------------------------------------------------------------------

    (String[], Type[]) collectInjectionsImpl(ModuleTemplate template, String[] definedNames = [])
        {TODO("Native");}

    Control resolveAndLink(
            ModuleTemplate    primaryModule,
            Model             model,
            ModuleRepository? repository,
            ResourceProvider? provider,
            Module[]          sharedModules,
            ModuleTemplate[]  additionalModules,
            String[]          definedNames) {TODO("Native");}

    FileTemplate loadFileTemplate(Byte[] contents) {TODO("Native");}
}