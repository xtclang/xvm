/**
 * A ClassTemplate is a representation of the compiled form of an Ecstasy `module`, `package`,
 * `class`, `const`, `enum`, `service`, `mixin`, or `interface`.
 */
interface ClassTemplate
        extends Template
        extends Composition
    {
    // ----- local types ---------------------------------------------------------------------------

    /**
     * TODO
     */
    static interface Composition
        {
        /**
         * The underlying ClassTemplate representing the basis of the class composition.
         */
        @RO ClassTemplate template;

        /**
         * Produce a new composition that represents an annotation of an existing composition.
         *
         * @param annotation  the annotation to apply to this existing composition
         *
         * @return the newly annotated composition
         */
        Composition! annotate(Annotation annotation)
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
        conditional (Annotation, Composition!) deannotate();

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
        Class!<> ensureClass(Type... actualTypes)
            {
            TODO("This Composition has not been loaded into a container");
            }
        }

    /**
     * Represents an annotated form of an existing composition.
     */
    static const AnnotatingComposition(Annotation annotation, Composition composition)
            implements Composition
        {
        @Override
        ClassTemplate template.get()
            {
            return composition.template;
            }

        @Override
        conditional (Annotation, Composition!) deannotate()
            {
            return True, annotation, composition;
            }
        }


    // ----- stuff ----------------------------------------------------------------------------

    /**
     * TODO
     */
    @RO Boolean virtualChild;

    /**
     * TODO
     */
    @RO Boolean singleton;
    }


//    @RO Boolean virtualChild;
//
//    // THIS IS JUST FOR DISCUSSION - not an actual plan
//    Template[] templates;
//
////    // ----- data types ----------------------------------------------------------------------------
////
////    /**
////     * A class exists within a namespace. The namespace can be one of several Ecstasy language
////     * structures.
////     */
////    typedef Module | Package | Class<> | Property | Method | Function Namespace;
//
//    /**
//     * A class is of a given category of Ecstasy language structures. These categories are not
//     * entirely discrete; an Enum, for example, is a Const.
//     */
//    enum Category {MODULE, PACKAGE, CLASS, CONST, ENUM, SERVICE, MIXIN, INTERFACE}
//
////    /**
////     * A class contains other named child structures.
////     */
////    typedef Class<> | MultiMethod | Property | MultiFunction NamedChild;
////
//    /**
//     * An action describes the manner in which one step of the class composition was achieved:
//     * * AnnotatedBy - TODO
//     * * Extends - a class _extends_ (inherits from) another class.
//     * * Implements - a class _implements_ an interface; this verb is also used when one interface
//     *   "extends" another interface.
//     * * Incorporates - a class _incorporates_ a mixin.
//     */
//    enum Action {AnnotatedBy, Extends, Implements, Incorporates}
//
//    /**
//     * A Composition represents a single step in a compositional recipe. A class is composed as a
//     * series of composition steps.
//     */
//    static const Contribution(Action action, Type ingredient);
//
////
////    /**
////     * SourceCodeInfo provides information about the name of the file that contains source code,
////     * and the 0-based line number within that file that the relevant source code begins.
////     */
////    static const SourceCodeInfo(String sourceFile, Int lineNumber);
////
////    // ----- primary state -------------------------------------------------------------------------
////
////    /**
////     * The category of the class.
////     */
////    Category category;
////
////    /**
////     * Every class is contained within a module, and the module is organized as a hierarchy of
////     * named structures, starting with the module itself, which contains a tree of packages,
////     * classes, properties, methods, and functions.
////     */
////    Namespace? parent;
//
//    /**
//     * The simple (unqualified) name of the class.
//     */
//    String name;
//
//    /**
//     * The type parameters for the class.
//     */
//    TypeParameter[] typeParams;
//
////    /**
////     * If the class is a mixin, this is the class to which it can be applied.
////     */
////    Class!<>? appliesTo;
////
////    /**
////     * The ordered steps of composition of this class.
////     */
////    Composition[] composition;
////
////    /**
////     * The child classes of this class.
////     */
////    Class!<>[] classes;
////
////    /**
////     * The child properties of this class.
////     */
////    Property[] properties;
////
////    /**
////     * The child methods of this class.
////     */
////    Method[] methods;
////
////    /**
////     * The child function literals of this class.
////     */
////    Function[] functions;
////
////    /**
////     * Determine if the class is abstract (meaning that it is not instantiable).
////     */
////    Boolean isAbstract;
////
////    /**
////     * Determine if the class defines a singleton (meaning that only one can be instantiated, and is
////     * assumed to be instantiated by the first time that it is requested).
////     */
////    Boolean isSingleton;
////
////    /**
////     * Obtain the singleton instance (throws an exception if _isSingleton_ is false).
////     */
////    PublicType singleton;
////
////    /**
////     * Determine if the class is an inner class, which must be instantiated virtually.
////     *
////     * Consider the following example:
////     *
////     *   class BaseParent
////     *       {
////     *       class Child {}                                     // inner class
////     *
////     *       static class Orphan {}                             // inner class
////     *       }
////     *
////     *   class DerivedParent
////     *      extends BaseParent
////     *     {
////     *     @Override
////     *     class Child {}                                       // inner class
////     *
////     *     @Override
////     *     static class Orphan {}                               // inner class
////     *     }
////     *
////     *   BaseParent parent1 = new BaseParent();
////     *   BaseParent.Child  child1  = new parent1.Child();       // creates a BaseParent.Child
////     *   BaseParent.Orphan orphan1 = new parent1.Orphan();      // creates a BaseParent.Orphan
////     *
////     *   BaseParent parent2 = new DerivedParent();
////     *   BaseParent.Child  child2  = new parent2.Child();       // creates a DerivedParent.Child
////     *   BaseParent.Orphan orphan2 = new parent2.Orphan();      // creates a DerivedParent.Orphan
////     */
////    Boolean isInnerClass;
////
////    /**
////     * The information that identifies the location of the source code for this class.
////     */
////    SourceCodeInfo? sourceInfo;
