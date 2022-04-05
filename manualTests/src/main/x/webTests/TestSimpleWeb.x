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
 *      curl -i -w '\n' -X POST http://localhost:8080/host/load -G -d 'app=TestSimpleWeb, realm=simple'
 *
 * 4. Use this web application:
 *      curl -i -w '\n' -X GET http://localhost/simple/hello
 */
@web.WebModule
module TestSimpleWeb
    {
    package web import web.xtclang.org;

    @web.WebService("/hello")
    service SimpleApi
        {
        @web.Get
        String hello()
            {
            return $"Hello World #{++count}".quoted();
            }

        Int count;
        }
    }