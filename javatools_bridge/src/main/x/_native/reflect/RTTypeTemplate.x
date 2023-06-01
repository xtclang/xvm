import ecstasy.reflect.Access;
import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.AnnotatingComposition;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.TypeTemplate;


/**
 * The native reflected TypeTemplate implementation.
 */
const RTTypeTemplate
        implements TypeTemplate {
    @Override @RO String         desc;
    @Override @RO Boolean        explicitlyImmutable;
    @Override @RO Form           form;
    @Override @RO String?        name;
    @Override @RO Boolean        recursive;
    @Override @RO TypeTemplate[] underlyingTypes;

    @Override conditional Access accessSpecified();
    @Override conditional AnnotationTemplate annotated();
    @Override conditional TypeTemplate contained();
    @Override conditional Composition fromClass();
    @Override conditional Composition fromProperty();
    @Override Boolean isA(TypeTemplate that);
    @Override conditional TypeTemplate modifying();
    @Override conditional (TypeTemplate, TypeTemplate) relational();
    @Override conditional TypeTemplate[] parameterized();
    @Override TypeTemplate purify();
    @Override conditional TypeTemplate resolveFormalType(String typeName);
    @Override TypeTemplate parameterize(TypeTemplate[] paramTypes = []);
    @Override TypeTemplate annotate(AnnotationTemplate annotation);

    // natural:
    //   toString()
    //   estimateStringLength()
    //   appendTo(buf)

    // helper method to create an AnnotationComposition
    static conditional Composition createComposition(ClassTemplate baseTemplate,
                                                     AnnotationTemplate[] annotations) {
        Composition result = baseTemplate;
        for (AnnotationTemplate annotation : annotations) {
            result = new AnnotatingComposition(annotation, result);
        }
        return True, result;
    }
}