module TestSimple {

    @Inject Console console;

    typedef (Authenticator + ExtrasAware + WebService) as AuthSvc;

    void run() {

        Test    test = new Test();
        AuthSvc svc  = &test.maskAs(AuthSvc);

        assert !svc.is(Hidden);

        console.print($|{&svc.actualType=}
                       |{&svc.actualClass=}
                       |{svc.auth()=} {svc.path=}
                       );
        for (AuthSvc extra : svc.extras) {
            console.print($|{&extra.actualType=}
                           |{&extra.actualClass=}
                           |{extra.auth()=} {extra.path=}
                          );
        }

        Class<WebService> clz = &svc.actualClass.as(Class<WebService>);
        assert clz.is(Restrict);
        console.print($|{clz.displayName=}
                       |{clz.PublicType=}
                       |{clz.permission=}
                       );

        Type<WebService> serviceType = clz.PublicType;
        console.print("*** methods:");
        for (Method<WebService, Tuple, Tuple> method : serviceType.methods) {
            console.print(method);
        }
        console.print("*** properties:");
        for (Property<WebService> prop : serviceType.properties) {
            console.print(prop);
        }
    }

    interface Authenticator { Int auth(); }
    interface ExtrasAware   { AuthSvc[] extras;}
    interface Hidden        { Int hide(); }

    mixin WebService(String path) into service;
    mixin Restrict(String permission) into Class<WebService>;

    @WebService("/hello")
    @Restrict("GET:/*")
    service Test
        implements Authenticator, ExtrasAware, Hidden {

        @Override Int auth() = 1;
        @Override Int hide() = 2;
        @Override AuthSvc[] extras.get() = [&this.maskAs(Authenticator + ExtrasAware + WebService)];
    }
}