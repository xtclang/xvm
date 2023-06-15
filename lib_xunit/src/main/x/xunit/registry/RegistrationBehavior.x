/**
 * `RegistrationBehavior` is used to specifying the required behavior
 *  when registering a resource that has already been registered.
 */
enum RegistrationBehavior {
    /**
     * Registration should be Ignored if a resource with the same identifier is
     * already registered.
     */
    Ignore,

    /**
     * The resource being registered should Replace any existing resource.
     */
    Replace,

    /**
     * Resource registration should Fail (by raising an exception) if a
     * resource with the same identifier is already registered.
     */
    Fail,

    /**
     * Specifies that registration must Always occur. If an resource is already registered
     * with the same identifier, a new identifier is generated (based
     * on the provided identity) and the specified artifact is registered.
     */
    Always;
}
