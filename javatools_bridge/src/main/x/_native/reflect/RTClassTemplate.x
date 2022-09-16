import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;
import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.AnnotatingComposition;
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
    // ----- Composition methods -------------------------------------------------------------------

    @Override @RO ClassTemplate[]       classes;
    @Override @RO Contribution[]        contribs;
    @Override @RO ClassTemplate?        mixesInto;
    @Override @RO MultiMethodTemplate[] multimethods;
    @Override @RO PropertyTemplate[]    properties;
    @Override @RO Boolean               singleton;
    @Override @RO Boolean               hasDefault;
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


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    conditional PropertyTemplate fromProperty()
        {
        return False;
        }

    @Override @RO String? implicitName;

    // helper function to create a Contribution
    Contribution createContribution(Action            action,
                                    Type              ingredientType,
                                    Object[]?         parameters,
                                    PropertyTemplate? delegatee,
                                    String[]?         names,
                                    Type[]?           types)
        {
        assert Composition         ingredient := ingredientType.template.fromClass();
        Map<String, TypeTemplate>? constraints = Null;

        switch (action)
            {
            case AnnotatedBy:
                assert ingredient.is(ClassTemplate) && parameters != Null;

                Argument[] arguments = parameters.size == 0
                        ? []
                        : names == Null
                            ? new Array(parameters.size, i -> new Argument(parameters[i].as(immutable|service)))
                            : new Array(parameters.size, i -> new Argument(parameters[i].as(immutable|service), names[i]));

                ingredient = new AnnotatingComposition(new AnnotationTemplate(ingredient, arguments), this);
                break;

            case Incorporates:
                if (names != Null && types != Null)
                    {
                    Int count = names.size;

                    assert count == types.size;

                    TypeTemplate[] constraintTemplates =
                            new Array<TypeTemplate>(count, i -> types[i].template);
                    constraints = new ListMap(names, constraintTemplates);
                    }
                break;
            }

        return new Contribution(action, ingredient, delegatee, constraints);
        }

    // helper function to create an array of TypeParameters
    static TypeParameter[] createTypeParameters(String[] names, TypeTemplate[] types)
        {
        return new Array(names.size, i -> new TypeParameter(names[i], types[i]));
        }
    }