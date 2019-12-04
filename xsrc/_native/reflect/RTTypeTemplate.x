import Ecstasy.reflect.Access;
import Ecstasy.reflect.Annotation;
import Ecstasy.reflect.ClassTemplate.Composition;
import Ecstasy.reflect.PropertyTemplate;
import Ecstasy.reflect.TypeTemplate;


/**
 * The native reflected TypeTemplate implementation.
 */
class RTTypeTemplate
        implements TypeTemplate
    {
    @Override @RO String         desc;
    @Override @RO Boolean        explicitlyImmutable;
    @Override @RO Form           form;
    @Override @RO String?        name;
    @Override @RO Boolean        recursive;
    @Override @RO TypeTemplate[] underlyingTypes;

    @Override conditional Access accessSpecified();
    @Override conditional Annotation annotated();
    @Override conditional TypeTemplate contained();
    @Override conditional Composition fromClass();
    @Override conditional PropertyTemplate fromProperty();
    @Override Boolean isA(TypeTemplate that);
    @Override conditional TypeTemplate modifying();
    @Override conditional TypeTemplate[] parameterized();
    @Override TypeTemplate purify();
    @Override conditional (TypeTemplate, TypeTemplate) relational();

    // natural:
    //   toString()
    //   estimateStringLength()
    //   appendTo(appender)
    }
