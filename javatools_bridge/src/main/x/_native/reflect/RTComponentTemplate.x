import ecstasy.reflect.Access;
import ecstasy.reflect.ComponentTemplate;


/**
 * The native reflected ComponentTemplate implementation.
 */
class RTComponentTemplate
        implements ComponentTemplate {
    @Override @RO Access             access;
    @Override @RO String?            doc;
    @Override @RO Format             format;
    @Override @RO Boolean            isAbstract;
    @Override @RO Boolean            isStatic;
    @Override @RO String             name;
    @Override @RO ComponentTemplate? parent;
    @Override @RO Boolean            synthetic;

    @Override ComponentTemplate[] children();
    @Override String toString();                            // TODO eventually make this natural
}
