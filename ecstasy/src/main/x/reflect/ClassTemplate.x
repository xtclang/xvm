/**
 * A ClassTemplate is a representation of the compiled form of an Ecstasy `module`, `package`,
 * `class`, `const`, `enum`, `service`, `mixin`, or `interface`.
 */
interface ClassTemplate
        extends ComponentTemplate
        extends Composition
    {
    // ----- local types ---------------------------------------------------------------------------

    /**
     * A Composition represents the manner in which a Class is formed out of ClassTemplate objects.
     * The purpose of separating the Class from the Composition reflects the complexities introduced
     * by generic types, because the formation of the Class is dependent on its formal types --
     * particularly with respect to _conditional incorporation_, but also with respect to the manner
     * in which method call chains are formed out of methods whose parameter(s) or return type(s)
     * are based on those formal types. As a result, the Composition is a necessary interposition
     * that acts as a factory for Class instances, based on the values of the formal type parameters
     * for the Class.
     */
    static interface Composition
        {
        /**
         * The underlying ClassTemplate representing the basis of the class composition.
         */
        @RO ClassTemplate baseTemplate.get()
            {
            Composition base = this;
            while ((AnnotationTemplate annotation, base) := base.deannotate())
                {
                }
            assert base.is(ClassTemplate);
            return base;
            }
        
        /**
         * The TypeTemplate representing the type of this composition.
         */
        @RO TypeTemplate type;

        /**
         * An `Action` describes the manner in which one step of the class composition was achieved.
         */
        enum Action
            {
            /**
             * AnnotatedBy - The specified `mixin` is applied "around" a class (preceding the
             * members defined on the underlying `ClassTemplate` for this composition) in order to
             * form a new class.
             */
            AnnotatedBy,
            /**
             * Extends - The underlying `ClassTemplate` for this composition `extends` (inherits
             * from) another class. (This action does not apply to interfaces, despite the use of
             * the `extends` keyword for defining interface inheritance; interface inheritance is
             * represented by the `Implements` action in the compiled form of the code.)
             */
            Extends,
            /**
             * Implements - This represents interface inheritance. The `ingredient` must be an
             * _interface_ type (i.e. not _class_ or _mixin_type).
             */
            Implements,
            /**
             * Delegates - This represents interface inheritance, and additionally, it represents
             * the _delegation_ of all of the properties and methods _of_ the specified interface,
             * to a reference of a compatible type (something that "is a" of that interface), which
             * reference is obtained from a specified property of this composition.
             */
            Delegates,
            /**
             * MixesInto - The `ingredient` represents the constraint type for a `mixin`; it is the
             * type that the mixin can be applied to as an annotation, or can be incorporated into.
             */
            MixesInto,
            /**
             * Incorporates - The `ingredient` specifies a `mixin` type that is incorporated into
             * the resulting mixin or class.
             */
            Incorporates
            }

        /**
         * A `Contribution` represents a single step in a compositional recipe. A class is composed
         * as a series of composition steps.
         */
        static const Contribution(Action                     action,
                                  Composition                ingredient,
                                  PropertyTemplate?          delegatee = Null,
                                  Map<String, TypeTemplate>? constraints = Null);

        /**
         * A composition is represented by a sequence of contributions.
         * REVIEW can we use Contribution... instead?
         */
        @RO Contribution[] contribs;

        /**
         * Determine if this composition extends (or is) the specified class.
         *
         * @param composition  the composition representing the super class
         *
         * @return True iff this Composition extends the specified class
         */
        Boolean extends(Composition! composition)
            {
            Format thisFormat = baseTemplate.format;
            Format thatFormat = composition.baseTemplate.format;

            // interfaces do not extend and cannot be extended
            if (thisFormat == Interface || thatFormat == Interface)
                {
                return False;
                }

            if (&this == &composition)
                {
                return True;
                }

            // unwrap any annotations from the composition that we are testing extension of
            while ((AnnotationTemplate annotation, composition) := composition.deannotate())
                {
                if (!this.incorporates(annotation.template))
                    {
                    return False;
                    }

                if (&this == &composition)
                    {
                    return True;
                    }
                }

            // one can only "extend" a class
            if (!composition.is(ClassTemplate))
                {
                return False;
                }

            // test whether it is possible for this to extend whatever the class template represents
            switch (thisFormat, thatFormat)
                {
                // any class can extend a class
                case (Class    , Class):
                case (Const    , Class):
                case (Enum     , Class):
                case (EnumValue, Class):
                case (Service  , Class):
                case (Package  , Class):
                case (Module   , Class):

                // a const (including Enum, EnumValue, Package, Module) can extend a const
                case (Const    , Const):
                case (Enum     , Const):
                case (EnumValue, Const):
                case (Package  , Const):
                case (Module   , Const):

                // only an EnumValue can extend an enum
                case (EnumValue, Enum):

                // only a mixin can extend a mixin
                case (Mixin, Mixin):

                // only a service can extend a service
                case (Service, Service):
                    break;

                default:
                    return False;
                }

            // search through the composition of this class to find the specified super class
            for (val contrib : baseTemplate.contribs)
                {
                if (contrib.ingredient.extends(composition))
                    {
                    return True;
                    }
                }

            return False;
            }

        /**
         * Determine if this composition incorporates (or is) the specified mixin.
         *
         * @param composition  the composition representing the mixin
         *
         * @return True iff this Composition incorporates the specified mixin
         * @return (conditional) True iff the incorporation is conditional
         */
        conditional Boolean incorporates(Composition! composition)
            {
            Format  thisFormat   = baseTemplate.format;
            Format  thatFormat   = composition.baseTemplate.format;
            Boolean fConditional = False;

            // interfaces do not incorporate and cannot be incorporated; only a mixin can be
            // incorporated
            if (thisFormat == Interface || thatFormat != Mixin)
                {
                return False;
                }

            if (&this == &composition)
                {
                return True, False;
                }

            // unwrap any annotations from the composition that we are testing extension of
            Composition baseThat = composition;
            while ((AnnotationTemplate annoThat, baseThat) := baseThat.deannotate())
                {
                if (Boolean fCond := this.incorporates(annoThat.template))
                    {
                    fConditional |= fCond;
                    }
                else
                    {
                    return False;
                    }

                if (&this == &baseThat)
                    {
                    return True, fConditional;
                    }
                }

            if (!baseThat.is(ClassTemplate))
                {
                return False;
                }

            Composition baseThis = this;
            while ((AnnotationTemplate annoThis, baseThis) := baseThis.deannotate())
                {
                if (Boolean fCond := annoThis.template.incorporates(baseThat))
                    {
                    fConditional |= fCond;
                    }
                else
                    {
                    return False;
                    }

                if (&baseThis == &baseThat)
                    {
                    return True, fConditional;
                    }
                }

            if (!baseThis.is(ClassTemplate))
                {
                return False;
                }

            // search through the composition of this class to find the specified mixin
            for (val contrib : baseThis.contribs)
                {
                if (Boolean fCond := contrib.ingredient.incorporates(baseThat))
                    {
                    fConditional |= fCond |
                            (contrib.action == Incorporates && contrib.constraints != Null);
                    return True, fConditional;
                    }
                }

            return False;
            }

        /**
         * Determine if this composition implements (or is) the specified interface.
         *
         * Note: Unlike [Type.isA], this method doesn't simply check if the referent's class
         * has all methods that the specified interface has. Instead, it returns True iff any of the
         * following conditions holds true:
         *  - the referent's class explicitly declares that it implements the specified interface, or
         *  - the referent's super class implements the specified interface (recursively), or
         *  - any of the interfaces that the referent's class declares to implement extends the
         *    specified interface (recursively)
         *
         * @param composition  the composition representing the interface
         *
         * @return True iff this Composition implements the specified interface
         */
        Boolean implements(Composition! composition)
            {
            if (&this == &composition)
                {
                return True;
                }

            // one can only "implement" an interface
            // REVIEW implication of the possibility of an @Annotated interface
            if (!(composition.is(ClassTemplate) && composition.format == Interface))
                {
                return False;
                }

            // search through the composition of this class to find the specified interface
            for (val contrib : contribs)
                {
                if (contrib.ingredient.implements(composition))
                    {
                    return True;
                    }
                }

            return False;
            }

        /**
         * Determine if this composition "derives from" (or is) the specified composition.
         *
         * @param composition  a composition representing an class, mixin, or interface
         *
         * @return True iff this (or something that this derives from) extends the specified class,
         *         incorporates the specified mixin, or implements the specified interface
         */
        Boolean derivesFrom(Composition! composition)
            {
            return &this == &composition
                    || this.extends(composition)
                    || this.incorporates(composition)
                    || this.implements(composition);
            }

        /**
         * Determine if this composition has a super-class.
         *
         * * Other than the Class for `Object`, a Class whose category is Class will **always** have
         *   a super-class.
         * * A Class whose category is Module, Package, Const, Enum, or Service will **always** have a
         *   super-class.
         * * A Mixin _may_ have a super-class, which must be a `mixin`.
         * * An Interface will *never* have a super-class.
         *
         * @return True iff this Composition has a super class
         * @return (conditional) the ClassTemplate of the super class
         */
        conditional ClassTemplate hasSuper()
            {
            if (baseTemplate.format == Interface)
                {
                return False;
                }

            for (Contribution contrib : contribs)
                {
                if (contrib.action == Extends)
                    {
                    return True, contrib.ingredient.as(ClassTemplate);
                    }
                }

            return False;
            }

        /**
         * Produce a new composition that represents an annotation of an existing composition.
         *
         * @param annotation  the annotation to apply to this existing composition
         *
         * @return the newly annotated composition
         */
        Composition! annotate(AnnotationTemplate annotation)
            {
            return new AnnotatingComposition(annotation, this);
            }

        /**
         * Determine if this composition is the result of annotating an existing composition, and if
         * so, return both the original composition and the annotation that was applied to it.
         *
         * @return True iff the composition is the result of annotating an existing composition
         * @return (conditional) the annotation used to create this annotated composition
         * @return (conditional) the underlying composition that was annotated
         */
        conditional (AnnotationTemplate, Composition!) deannotate();

        /**
         * Obtain the Class represented by the combination of this composition and the specified
         * formal types.
         *
         * @param actualTypes  the specific types to use for each of the formal type parameters of
         *        the class
         *
         * @throws UnsupportedOperation iff the Composition implementation is not able to create a
         *         Class because the necessary components have not been loaded, verified, and linked
         * @throws TypeRequired iff a formal type parameter of the resulting class is missing a type
         * @throws InvalidClass iff the result of the composition and/or formal types would produce
         *         a class that violates the verifier rules
         */
        Class!<> ensureClass(Type[] actualTypes = [])
            {
            TODO("This Composition has not been loaded into a container");
            }
        }

    /**
     * Represents an annotated form of an existing composition.
     */
    static const AnnotatingComposition(AnnotationTemplate annotation, Composition composition)
            implements Composition
        {
        assert()
            {
            assert composition.is(ClassTemplate) || composition.is(AnnotatingComposition);
            }

        @Override
        conditional (AnnotationTemplate, Composition!) deannotate()
            {
            return True, annotation, composition;
            }
        }


    // ----- attributes ----------------------------------------------------------------------------

    /**
     * Determine if the class is an inner class, which must be instantiated virtually.
     *
     * Consider the following example:
     *
     *     class BaseParent
     *         {
     *         class Child {}
     *         }
     *
     *     class DerivedParent
     *             extends BaseParent
     *         {
     *         @Override
     *         class Child {}
     *         }
     *
     *     BaseParent       parent1 = new BaseParent();
     *     BaseParent.Child child1  = new parent1.Child();       // creates a BaseParent.Child
     *
     *     BaseParent       parent2 = new DerivedParent();
     *     BaseParent.Child child2  = new parent2.Child();       // creates a DerivedParent.Child
     *
     * In the example, even though the reference was held in a variable of type BaseParent, the
     * virtual type was used when instantiating the child.
     */
    @RO Boolean virtualChild;

    /**
     * True iff this class is the class of a singleton.
     */
    @RO Boolean singleton;

    /**
     * The type parameters for the class.
     */
    @RO TypeParameter[] typeParams;

    /**
     * If the class is a mixin, this is the class to which it can be applied.
     */
    @RO ClassTemplate! mixesInto;

    /**
     * The classes contained within this class.
     */
    @RO ClassTemplate![] classes;

    /**
     * The properties of this class.
     */
    @RO PropertyTemplate[] properties;

    /**
     * The multi-methods of this class.
     */
    @RO MultiMethodTemplate[] multimethods;

    /**
     * The information that identifies the location of the source code for this class.
     */
    @RO SourceCodeInfo? sourceInfo;
    }
