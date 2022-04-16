/**
 * See the TestSimpleWeb doc.
 *
 * To test:
 *  curl -i -w '\n' -X GET http://shop.acme.user.xqiz.it:8080/catalog/classic.jpg
 */
@WebModule
module TestStaticWeb
    {
    package web import web.xtclang.org;

    import web.StaticContent;
    import web.WebModule;

    @StaticContent(/sock-shop/catalog/images/, ALL_TYPE, "/catalog")
    service Catalog
        {
        }
    }