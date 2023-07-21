import ecstasy.mgmt.ModuleRepository;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;

/**
 * The native reflected FileTemplate implementation.
 */
class RTFileTemplate
        extends RTComponentTemplate
        implements FileTemplate {

    @Override
    ModuleTemplate mainModule.get()                     {TODO("native");}

    @Override
    Boolean resolved.get()                              {TODO("native");}

    @Override
    RTFileTemplate resolve(ModuleRepository repository) {TODO("native");}

    @Override
    conditional ModuleTemplate getModule(String name)   {TODO("native");}

    @Override
    Time? created.get() {
        // see OSFileNode.created.get()
        Int createdMillis = this.createdMillis;
        return createdMillis == 0
                ? Null
                : new Time(createdMillis*TimeOfDay.PicosPerMilli);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Called by native "resolve(ModuleRepository)". See FileStructure.java for the "original" code.
     */
    private RTFileTemplate linkModules(ModuleRepository repository) {
        String      moduleName      = mainModule.name;
        String[]    moduleNamesTodo = moduleNames.reify(Mutable);
        Set<String> moduleNamesDone = new HashSet();

        // the primary module is implicitly linked already
        moduleNamesDone.add(moduleName);

        ModuleTemplate[] unresolvedModules = new ModuleTemplate[];
        // iteratively link all downstream modules (moduleNamesTodo array may grow)
        for (Int i = 0; i < moduleNamesTodo.size; ++i) {
            String nextName = moduleNamesTodo[i];

            // only need to link it once (each node in the graph gets visited once)
            if (moduleNamesDone.contains(nextName)) {
                continue;
            }
            moduleNamesDone.add(nextName);

            ModuleTemplate nextModule;
            if (nextModule := getModule(nextName), nextModule.resolved) {
                continue;
            }

            // load the module against which the compilation will occur
            if (!repository.moduleNames.contains(nextName)) {
                throw new Exception($"Missing dependent module: {nextName}");
            }

            assert ModuleTemplate unresolved := repository.getModule(nextName)
                as $"Missing module {nextName.quoted()}";

            unresolvedModules += unresolved;
            moduleNamesTodo.addAll(unresolved.parent.as(FileTemplate).moduleNames);
        }

        replace(unresolvedModules);
        return this;
    }

    private void replace(ModuleTemplate[] unresolvedModules) { TODO("native"); }

    private Int createdMillis.get() { TODO("native"); }
}