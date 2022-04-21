/**
 * This is a first Ecstasy-based web application.
 *
 * 1. Follow steps 1-3 from TestSimpleWeb.
 *
 * 2. Compile the DB:
 *      gradle compileOne -PtestName=webTests/welcomeDB
 *
 * 3. Compile this test:
 *      gradle compileOne -PtestName=webTests/welcome
 *
 * 4. Copy the "GUI" files into "welcome-resources" directory.
 *
 * 5. Open in the browser:
 *      http://shop.acme.user.xqiz.it:8080/index.html
 */
@web.WebModule
module welcome
    {
    package web import web.xtclang.org;
    package db  import welcomeDB;

    @web.WebService("/welcome")
    service SimpleApi
        {
        @Inject db.WelcomeSchema schema;

        @web.Get
        Int count()
            {
            return schema.count.next();
            }
        }

    @web.StaticContent(/welcome-resources, ALL_TYPE)
    service Content
        {
        }
    }