import Ecstasy.reflect.Annotation;
import Ecstasy.reflect.ClassTemplate;
import Ecstasy.reflect.ClassTemplate.Composition;
import Ecstasy.reflect.ClassTemplate.Contribution;
import Ecstasy.reflect.MultiMethodTemplate;
import Ecstasy.reflect.PropertyTemplate;
import Ecstasy.reflect.SourceCodeInfo;
import Ecstasy.reflect.TypeParameter;
import Ecstasy.reflect.TypeTemplate;


/**
 * The native reflected ClassTemplate implementation.
 */
class RTClassTemplate
        extends RTComponentTemplate
        implements ClassTemplate
    {
    @Override @RO ClassTemplate[]       classes;
    @Override @RO Contribution[]        contribs;
    @Override @RO ClassTemplate         mixesInto;
    @Override @RO MultiMethodTemplate[] multimethods;
    @Override @RO PropertyTemplate[]    properties;
    @Override @RO Boolean               singleton;
    @Override @RO SourceCodeInfo?       sourceInfo;
    @Override @RO ClassTemplate         template;
    @Override @RO TypeTemplate          type;
    @Override @RO TypeParameter[]       typeParams;
    @Override @RO Boolean               virtualChild;

    @Override conditional (Annotation, Composition) deannotate();
    @Override Class<> ensureClass(Type... actualTypes);

    // natural code (these *could* be optimized if they were made native)
    //   Boolean extends(Composition composition)
    //   conditional Boolean incorporates(Composition composition)
    //   Boolean implements(Composition composition)
    //   Boolean derivesFrom(Composition composition)
    //   conditional ClassTemplate hasSuper()
    //   Composition! annotate(Annotation annotation)
    }
