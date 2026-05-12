/**
 * InstrumentationScope is a logical unit of software with which the emitted telemetry can be
 * associated. It is typically the developer's choice to decide what denotes a reasonable
 * instrumentation scope. The most common approach is to use the name and version of the
 * instrumentation library, with any additional identifying information as part of the scope's
 * attributes. Other software components can be used too to get name, version and additional
 * attributes, e.g. a module, a package, a class or a plugin.
 *
 * An instrumentation scope is defined by the its name, version, schema_url and attributes,
 * where version, schema_url, and attributes are optional. This SHOULD uniquely identify the
 * logical unit of software that emits the telemetry. A typical approach to ensure uniqueness is
 * to use the fully qualified name of the emitting software unit (e.g. fully qualified library
 * name or fully qualified class name).
 */
const InstrumentationScope(String name, String? version = Null, String? schemaUrl = Null,
                           Attributes attributes = []) {
    /**
     * An empty `InstrumentationScope`.
     */
    static InstrumentationScope Empty = new InstrumentationScope("");

    /**
     * Create an `InstrumentationScope` from the given `Type`.
     *
     * @param type        the `Type` to create the `InstrumentationScope` from
     * @param attributes  (optional) the attributes to use for the `InstrumentationScope`
     *
     * @return The `InstrumentationScope` created from the given `Type`
     */
    static InstrumentationScope from(Type type, Attributes attributes = []) {
        assert Class  cls        := type.fromClass();
        String        path       = cls.path;
        assert Int    colon      := path.indexOf(':');
        String        moduleName = path[0 ..< colon];
        TypeSystem    ts         = type.typeSystem;
        assert Module mod        := ts.moduleByQualifiedName.get(moduleName);
        assert String relPath    := cls.pathWithin(ts);
        String        name       = moduleName + "." + relPath;
        String        version    = mod.version.toString();
        return new InstrumentationScope(name, version, Null, attributes);
    }

    static <CompileType extends InstrumentationScope> Int hashCode(CompileType value1) {
        return value1.name.hashCode() ^
               value1.version?.hashCode() : 0 ^
               value1.schemaUrl?.hashCode() : 0;
    }
}
