/**
 * This mixin is used to mark a module as being a web-application module.
 */
mixin WebModule()
        into Module
    {
    /**
     * Collect all roots declared by this Module and instantiate corresponding WebServices.
     */
    immutable Map<String, WebService> collectRoots_()
        {
        import ecstasy.reflect.Annotation;

        Map<String, WebService> roots = new HashMap();

        Module webModule = this;
        for (Class child : webModule.classes)
            {
            if (child.implements(WebService), Struct structure := child.allocate())
                {
                WebService webService = child.instantiate(structure).as(WebService);

                String path = webService.path;
                if (roots.contains(path))
                    {
                    // TODO: how to report a duplicate path?
                    }
                else
                    {
                    roots.put(path, &webService.maskAs(WebService));
                    }
                }
            }

        return roots.makeImmutable();
        }
    }

