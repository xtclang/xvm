import ecstasy.reflect.ClassTemplate;

/**
 * A `Selector` to discover test fixtures within a `Package`.
 *
 * @param package  the `Package` to discover tests fixtures in
 */
service PackageSelector<DataType extends Package>(Package pkg)
        extends BaseSelector<DataType> {
    @Override
    immutable Model[] select(DiscoveryConfiguration config) {
        Class<Package> pkgClass = &pkg.actualClass.as(Class<Package>);
        if (Model model := processContainer(config, pkgClass, pkg.classes)) {
           return [model];
        }
        return [];
    }
}
