/**
 * This is a test for web-based hosting. To run it:
 *
 * 1. Start the host module "hostWeb":
 *      gradle hostWeb
 *
 * 2. Compile this test:
 *      gradle compileOne -PtestName=webTests/TestSimpleWeb
 *
 * 3. Load this test:
 *      curl -i -w '\n' -X POST http://localhost:8080/host/run/TestSimpleWeb
 *
 * 4. Use this Web app:
 *      curl -i -w '\n' -X POST http://localhost:8080/TestSimpleWeb/hello
 *
 */
@web.WebModule
module TestSimpleWeb
    {
    package web import web.xtclang.org;

    @web.WebService("/simple")
    service SimpleApi
        {
        @web.Get("/hello")
        String hello()
            {
            return "Hello World".quoted();
            }
        }
    }