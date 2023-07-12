/**
 * The `models` package contains implementations of the `Model`
 * interface and utilities for handling models.
 */
package models {
    /**
     * Return a `ModelBuilder` that will build a `ContainerModel` for
     * the `UniqueId`.
     *
     * @param uniqueId  the `UniqueId` to obtain a `ModelBuilder` for
     *
     * @return `True` iff the `UniqueId` represents a `Class`, `Package` or `Module`
     *         known to the type system, otherwise returns `False`
     * @return a `ModelBuilder<ContainerModel>` that will build the parent test container
     */
    static conditional ModelBuilder<ContainerModel> builderFor(UniqueId uniqueId) {
        if (uniqueId.type != Method) {
            TypeSystem typeSystem = this:service.typeSystem;
            if (Class clz := typeSystem.classForName(uniqueId.path, True)) {
                ModelBuilder<ContainerModel> builder = new ContainerModel.Builder(clz);
                return True, builder.as(ModelBuilder<ContainerModel>);
            }
        }
        return False;
    }
}