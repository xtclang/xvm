import reflect.ModuleTemplate;

/**
 * A [ModuleRepository] implementation based on a single module.
 */
const InstantRepository
        implements ModuleRepository {
    /**
     * Construct a repository from an Ecstasy structure and an optional underlying repository.
     */
    construct(ModuleTemplate template, ModuleRepository? repository = Null) {
        this.template   = template;
        this.moduleName = template.qualifiedName;

        Set<String> names = new HashSet();
        names.add(moduleName);

        if (repository != Null) {
            names.addAll(repository.moduleNames);
        }

        this.moduleNames = names.makeImmutable();
        this.repository  = repository;
    }

    @Override
    public/private immutable Set<String> moduleNames;

    @Override
    conditional ModuleTemplate getModule(String name, Version? version = Null) {
        return name == moduleName
                ? (True, template)
                : (repository?.getModule(name, version) : False);
    }

    @Override
    void storeModule(ModuleTemplate template) {
        throw new Unsupported();
    }

    /**
     * An optional underlying repository.
     */
    public/private ModuleRepository? repository;

    /**
     * The primary module name.
     */
    public/private String moduleName;

    /**
     * The primary module bytes.
     */
    public/private ModuleTemplate template;
}