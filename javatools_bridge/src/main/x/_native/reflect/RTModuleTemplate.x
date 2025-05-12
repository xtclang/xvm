import ecstasy.reflect.ModuleTemplate;

/**
 * The native reflected ModuleTemplate implementation.
 */
class RTModuleTemplate
        extends RTClassTemplate
        implements ModuleTemplate {
    @Override
    String qualifiedName.get() = TODO("native");

    @Override
    @Lazy Version? version.calc() = new Version(versionString?) : Null;

    private String? versionString.get() = TODO("native");

    @Override
    immutable Map<String, ModuleTemplate> modulesByPath.get() = TODO("native");

    @Override
    @RO Boolean resolved.get() = TODO("native");
}