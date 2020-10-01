import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.ClassTemplate.Contribution;
import ecstasy.reflect.MultiMethodTemplate;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.SourceCodeInfo;
import ecstasy.reflect.TypeParameter;
import ecstasy.reflect.TypeTemplate;


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
    @Override @RO TypeTemplate          type;
    @Override @RO TypeParameter[]       typeParams;
    @Override @RO Boolean               virtualChild;

    @Override conditional (AnnotationTemplate, Composition) deannotate();
    @Override Class<> ensureClass(Type[] actualTypes = []);

    // natural code (these *could* be optimized if they were made native)
    //   Boolean extends(Composition composition)
    //   conditional Boolean incorporates(Composition composition)
    //   Boolean implements(Composition composition)
    //   Boolean derivesFrom(Composition composition)
    //   conditional ClassTemplate hasSuper()
    //   Composition! annotate(AnnotationTemplate annotation)

    // helper function to create a Contribution
    static Contribution createContribution(Action           action,
                                          Type              ingredientType,
                                          PropertyTemplate? delegatee,
                                          String[]?         constraintNames,
                                          Type[]?           constraintTypes)
        {
        assert Composition         ingredient := ingredientType.template.fromClass();
        Map<String, TypeTemplate>? constraints = Null;

        if (constraintNames != Null && constraintTypes != null)
            {
            Int count = constraintNames.size;

            assert count == constraintTypes.size;

            TypeTemplate[] constraintTemplates =
                    new Array<TypeTemplate> (count, i -> constraintTypes[i].template);
            constraints = new ListMap(constraintNames, constraintTemplates);
            }

        return new Contribution(action, ingredient, delegatee, constraints);
        }
    }
