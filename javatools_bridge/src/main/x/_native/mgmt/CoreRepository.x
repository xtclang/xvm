import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

const CoreRepository
        implements ModuleRepository {
    @Override immutable Set<String> moduleNames.get() {TODO("Native");}

    @Override
    conditional ModuleTemplate getModule(String name) {TODO("Native");}

    @Override
    void storeModule(ModuleTemplate template) {
        throw new ReadOnly();
    }

    @Override
    String toString() {
        return "CoreRepository";
    }
}