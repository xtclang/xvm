/**
 * The key class for a resource.
 */
const RegistryKey<Resource> {
    /**
     * Create a `RegistryKey`.
     *
     * @param type  the `Type` of the resource
     * @param name  the name of the resource (if `Null` the `Type` name will be used)
     */
    construct(Type<Resource> type, String? name = Null) {
        this.type = type;
        this.name = name.is(String) ? name : type.toString();
    }

    /**
     * The `Type` of the resource.
     */
    Type<Resource> type;

    /**
     * The name of the resource.
     */
    String name;
}
