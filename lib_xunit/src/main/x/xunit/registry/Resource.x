/**
 * A wrapper around the values defining a resource.
 *
 * @param type      the resource `Type`
 * @param resource  the resource
 * @param name      the resource name
 * @param behavior  the behaviour to use if a resource with the same key is already registered
 * @param observer  an optional `Observer` to register with the resource
 */
class Resource<RegisterAs, ResourceType extends RegisterAs>(Type<RegisterAs> type,
        ResourceType resource, String name, RegistrationBehavior behavior = Fail,
        Observer<RegisterAs>? observer = Null) {
}
