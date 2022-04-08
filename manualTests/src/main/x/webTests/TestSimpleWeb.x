/**
 * This is a test for web-based hosting. To run it:
 *
 * 1. Make sure your "hosts" file contains the following entries:
 *      127.0.0.10 admin.xqiz.it
 *      127.0.0.20 shop.acme.user.xqiz.it
 *
 * 2. Allow the loopback addresses binding by running this script:
 *        xvm/bin/allowLoopback.sh
 *
 * 3. Start the hosting module:
 *      gradle host
 *
 * 4. Compile this test:
 *      gradle compileOne -PtestName=webTests/TestSimpleWeb
 *
 * 5. Load this test:
 *      curl -i -w '\n' -X POST http://admin.xqiz.it:8080/host/load -G -d 'app=TestSimpleWeb, domain=shop.acme.user'
 *
 * 6. Use this web application:
 *      curl -i -w '\n' -X GET http://shop.acme.user.xqiz.it:8080/welcome
 */
@web.WebModule
module TestSimpleWeb
    {
    package web import web.xtclang.org;

    @web.WebService("/welcome")
    service SimpleApi
        {
        @web.Get
        String hello()
            {
            return $"Welcome! You are a visitor #{++count}".quoted();
            }

        Int count;
        }
    }