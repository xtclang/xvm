import ecstasy.annotations.Inject.Options;

interface ResourceLookupExtension {

    conditional Object lookup(Type type, String name, Options opts = Null);
}