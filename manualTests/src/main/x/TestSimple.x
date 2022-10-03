@web.LoginRequired // this used to blow at runtime
@web.WebApp
module TestSimple
    {
    @Inject Console console;

    package web import web.xtclang.org;

    import ecstasy.reflect.*;

    void run()
        {
        Class clz = Test;

        assert AnnotationTemplate template := clz.annotatedBy(web.WebService);

        assert clz.is(web.LoginRequired);
        assert clz.security == High;

        Class m = TestSimple;
        assert m.is(web.LoginRequired);
        assert m.security == Normal;

        Type<Module> t = TestSimple;
        assert Class m1 := t.fromClass();
        assert m1.is(web.LoginRequired);
        assert m1.security == Normal;
        }

    @web.LoginRequired(High)
    @web.WebService("/")
    service Test
        {
        }
    }