import ecstasy.reflect.ClassTemplate;

//import xunit.models.*;

/**
 * A `Class` discovery selector to discover classes that that may or may not be a test fixture.
 * The class may contain other classes and/or test methods.
 *
 * @param clz  the `Class` to discover test fixtures in
 */
service ClassSelector<DataType>(Class<DataType> clz)
        extends BaseSelector<DataType> {
    @Override
    immutable Model[] select(DiscoveryConfiguration config) {
        if (Model model := processContainer(config, clz)) {
            return [model];
        }
        return [];
    }
}
