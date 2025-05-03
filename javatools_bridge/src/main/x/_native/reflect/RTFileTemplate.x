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
    ModuleTemplate mainModule.get() = TODO("native");

    @Override
    RTFileTemplate resolve(ModuleRepository repository) = TODO("native");

    @Override
    conditional ModuleTemplate extractVersion(Version? version = Null) =
            extractVersionImpl(version?.toString() : "");

    @Override
    immutable Byte[] contents.get() = TODO("native");

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
        const ModuleId(String name, Version? version) {
            construct(ModuleTemplate template) {
                name    = template.qualifiedName;
                version = template.version;
            }
        }

        ModuleId      moduleId    = new ModuleId(mainModule);
        Set<ModuleId> modulesDone = new HashSet();

        // the primary module is implicitly linked already
        modulesDone.add(moduleId);

        ModuleTemplate[] modulesTodo       = modules.reify(Mutable);
        ModuleTemplate[] unresolvedModules = new ModuleTemplate[];

        // iteratively link all downstream modules (modulesTodo array may grow)
        for (Int i = 0; i < modulesTodo.size; ++i) {
            ModuleId nextId = new ModuleId(modulesTodo[i]);

            // only need to link it once (each node in the graph gets visited once)
            if (modulesDone.contains(nextId)) {
                continue;
            }
            modulesDone.add(nextId);

            // load the module against which the compilation will occur
            assert ModuleTemplate unresolved := repository.getModule(nextId.name, nextId.version)
                as $"Missing dependent module {nextId}";

            unresolvedModules += unresolved;
            modulesTodo.addAll(unresolved.parent.as(FileTemplate).modules);
        }

        replace(unresolvedModules);
        return this;
    }

    private conditional ModuleTemplate extractVersionImpl(String version) = TODO("native");
    private void replace(ModuleTemplate[] unresolvedModules) = TODO("native");

    private Int createdMillis.get() = TODO("native");
}