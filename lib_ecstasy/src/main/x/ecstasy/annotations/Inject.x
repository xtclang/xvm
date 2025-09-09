/**
 * `@Inject` is used to make a final reference that is automatically injected with a value no later
 * than the point at which the reference comes into scope.
 */
 annotation Inject<Referent>(String? resourceName = Null, Options opts = Null)
        into Ref<Referent> {
    typedef immutable Object? as Options;
}
