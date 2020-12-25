import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

class CoreRepository
        implements ModuleRepository
    {
    @Override immutable Set<String> moduleNames.get()
        {TODO("Native");}

    @Override
    ModuleTemplate getModule(String name)
        {TODO("Native");}

    @Override
    void storeModule(ModuleTemplate template)
        {
        throw new UnsupportedOperation();
        }
    }