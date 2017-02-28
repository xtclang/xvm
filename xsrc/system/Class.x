interface Class<ClassType>
    {
    Class | Method | Function | Property | ... ? parent

    /**
     * Simple (unqualified) name.
     */
    String name;

    /**
     * Obtain the public interface of the class.
     */
    @ro Type PublicType;
    @ro Type ProtectedType;
    @ro Type PrivateType;

    @ro Map<String, Class | MultiMethod | Property | MultiFunction> children;
    @ro Map<String, Class> classes;
    @ro Map<String, MultiMethod> methods;
    @ro Map<String, Property> properties;

    /**
     * Determine if the class implements the specified interface.
     */
    Boolean implements(Class interface);

    /**
     * Determine if the class extends (or is) the specified class.
     */
    Boolean extends(Class class);

    /**
     * Determine if the class incorporates the specified trait or mixin.
     */
    Boolean incorporates(Class traitOrMixin);

    /**
     * Determine if the class is a service.
     */
    @ro Boolean isService;

    /**
     * Determine if the class is an immutable const.
     */
    @ro Boolean isConst;

    /**
     * The singleton instance.
     */
    conditional ClassType singleton;
    }
