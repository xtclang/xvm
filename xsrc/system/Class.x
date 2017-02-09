public interface Class
    {
    /**
     * Obtain the public interface of the class.
     */
    @ro Type PublicType;
    @ro Type ProtectedType;
    @ro Type PrivateType;

    @ro Map<String, Class | MultiMethod | Property | MultiFunction> Namespace;
    @ro Map<String, Class> ClassMap;
    @ro Map<String, MultiMethod> MethodMap;
    @ro Map<String, Property> PropertyMap;
    }
