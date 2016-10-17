interface Class
        implements Value
    {

    @ro Type PublicType;
    @ro Type ProtectedType;
    @ro Type PrivateType;

    @ro Map<String, Class | MultiMethod | Property> Namespace;
    @ro Map<String, Class> ClassMap;
    @ro Map<String, MultiMethod> MethodMap;
    @ro Map<String, Property> PropertyMap;
    }
