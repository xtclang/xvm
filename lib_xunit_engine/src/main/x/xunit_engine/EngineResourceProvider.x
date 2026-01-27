import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.*;

/**
 * A `ResourceProvider` implementation that can provide resources to inject into tests.
 *
 * @param curDir      the current working directory
 * @param outDir      the XUnit root test output directory
 * @param repository  the module repository containing the test module and dependencies
 */
service EngineResourceProvider(Directory curDir, Directory outDir, ModuleRepository repository)
        extends BaseResourceProvider(curDir, repository) {

    /**
     * The test output root directory directory.
     */
    @Lazy Directory testOutputRootDir.calc() = outDir.dirFor(TestOutputRootDir);

    @Override
    Supplier getResource(Type type, String name) {
        switch (type, name) {

        case (Directory, "testOutputRoot"):
            return testOutputRootDir;
        }

        return super(type, name);
    }
}
