import collections.HashSet;

class InstantRepository
        implements ModuleRepository
    {
    /**
     * Construct a repository from an XTC structure and an optional underlying repository.
     */
    construct(immutable Byte[] moduleBytes, ModuleRepository? repository = Null)
        {
        @Inject Container.Linker linker;

        this.moduleName  = linker.validate(moduleBytes);
        this.moduleBytes = moduleBytes;

        Set<String> names = new HashSet();
        names.add(moduleName);

        if (repository != Null)
            {
            names.addAll(repository.moduleNames);
            }

        this.moduleNames = names.makeImmutable();
        this.repository  = repository;
        }

    @Override
    immutable Byte[] getModule(String name)
        {
        return name == moduleName
                ? moduleBytes
                : repository?.getModule(name);

        throw new IllegalArgument(name);
        }

    @Override
    public/private immutable Set<String> moduleNames;

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
    public/private immutable Byte[] moduleBytes;
    }

