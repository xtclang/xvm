/**
 * Represents the source of injected resources.
 */
interface ResourceProvider
    {
    /**
     * Obtain a resource for specified type and name. Most commonly, failure
     * a provider to return a resource (throwing an exception) will fail to load or
     * terminate the requesting container.
     */
    <Resource> Resource getResource(Type<Resource> type, String name);

    // --- alternative: split between static and dynamic resources ---

//        enum Category {Unavailable, Static, Dynamic}
//
//        <Resource> Category getResourceCategory(Type<Resource> type, String name);
//
//        <Resource> Resource getStaticResource(Type<Resource> type, String name);
//
//        <Resource> function Resource() getDynamicResource(Type<Resource> type, String name);
    }

