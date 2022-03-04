/**
 * The module for basic web-based hosting functionality.
 */
module hostWeb.xtclang.org
    {
    package host import host.xtclang.org;
    package web  import web.xtclang.org;

    import ecstasy.reflect.AnnotationTemplate;
    import ecstasy.reflect.ClassTemplate;
    import ecstasy.reflect.ModuleTemplate;
    import ecstasy.reflect.TypeTemplate;

    import web.WebServer;

    void run()
        {
        server.addRoutes(new HostApi(), "/host")
              .start();

        @Inject Console console;
        console.println("Started Ecstasy hosting at http://localhost:8080");

        wait();
        }

    void wait()
        {
        // wait forever
        @Inject Timer timer;
        timer.schedule(Duration:1h, () -> {});
        }

    @Lazy WebServer server.calc()
        {
        return new WebServer(8080);
        }

    /**
     * Check if the `ClassTemplate` has a specified annotation.
     *
     * @return True iff there is an annotation of the specified name
     * @return the corresponding `AnnotationTemplate` (optional)
     */
    conditional AnnotationTemplate findClassAnnotation(ClassTemplate template, String annotationName)
        {
        import ecstasy.reflect.ClassTemplate.Composition;
        import ecstasy.reflect.ClassTemplate.AnnotatingComposition;

        for (val contrib : template.contribs)
            {
            if (contrib.action == AnnotatedBy)
                {
                assert AnnotatingComposition composition := contrib.ingredient.is(AnnotatingComposition);
                if (composition.annotation.template.displayName == annotationName)
                    {
                    return True, composition.annotation;
                    }
                }
            }

        return False;
        }
    }
