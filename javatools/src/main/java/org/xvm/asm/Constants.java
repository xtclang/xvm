package org.xvm.asm;


/**
 * Constant values used by the XVM for its various VM structures.
 */
public interface Constants
    {
    // ----- file header ---------------------------------------------------------------------------

    /**
     * The special sequence of bytes that identifies an XVM FileStructure.
     */
    int FILE_MAGIC = 0xEC57A5EE;

    /**
     * The current major version of the XVM FileStructure. This is the newest
     * version that can be read and/or written by this implementation.
     *
     * By convention, version 0 is the pre-production version: The language and tool-chain are still
     * in development.
     */
    int VERSION_MAJOR_CUR = 0;

    /**
     * The current minor version of the XVM File structure. This is the newest
     * version that can be written by this implementation. (Newer minor versions
     * can be safely read.)
     *
     * By convention, as long as VERSION_MAJOR_CUR == 0, whenever a change is made to Ecstasy that
     * changes the persistent structure of an ".xtc" file in a manner that isn't both forwards and
     * backwards compatible, the minor version will be updated to the 8-digit ISO 8601 date (i.e.
     * the date string with the "-" characters having been removed). The result is that an error
     * will be displayed if there is a version mismatch, which should save some frustration -- since
     * otherwise the resulting error(s) can be very hard to diagnose.
     */
    int VERSION_MINOR_CUR = 20230724;


    // ----- names ---------------------------------------------------------------------------------

    /**
     * The qualified name of the Ecstasy core module. This is the only module that has no external
     * dependencies (other than a conceptual dependency in the compiler on the prototype module,
     * due to the "turtles" problem of Ref.x having properties which are themselves refs).
     */
    String ECSTASY_MODULE = "ecstasy.xtclang.org";

    /**
     * The name of the package within every module that imports the Ecstasy core module.
     */
    String X_PKG_IMPORT = "ecstasy";

    /**
     * The qualified name of the Java-based prototype runtime module.
     */
    String TURTLE_MODULE = "mack.xtclang.org";

    /**
     * The qualified name of the Java-based prototype runtime module.
     */
    String NATIVE_MODULE = "_native.xtclang.org";


    // ----- accessibility levels ------------------------------------------------------------------

    /**
     * The Access enumeration refers to the level of accessibility to a class that a reference will
     * have:
     * <ul>
     * <li>{@link #STRUCT STRUCT} - direct access to the underlying data structure (but only to the
     *     data structure);</li>
     * <li>{@link #PUBLIC PUBLIC} - access to the public members of the object's class;</li>
     * <li>{@link #PROTECTED PROTECTED} - access to the protected members of the object's class;</li>
     * <li>{@link #PRIVATE PRIVATE} - access to the private members of the object's class;</li>
     * </ul>
     */
    enum Access
        {
        STRUCT(0),
        PUBLIC(Component.ACCESS_PUBLIC),
        PROTECTED(Component.ACCESS_PROTECTED),
        PRIVATE(Component.ACCESS_PRIVATE);

        Access(int flags)
            {
            this.FLAGS = flags;
            }

        public boolean canSee(Access that)
            {
            if (this == that || this == STRUCT || this == PRIVATE)
                {
                return true;
                }

            return this == PROTECTED && that == PUBLIC;
            }

        public boolean isAsAccessibleAs(Access that)
            {
            return !isLessAccessibleThan(that);
            }

        public boolean isMoreAccessibleThan(Access that)
            {
            // struct access is not comparable
            assert this != STRUCT;
            assert that != STRUCT;

            return this.compareTo(that) < 0;
            }

        public boolean isLessAccessibleThan(Access that)
            {
            // struct access is not comparable
            assert this != STRUCT;
            assert that != STRUCT;

            return this.compareTo(that) > 0;
            }

        /**
         * Compare this access with that access, and return the one that has LESS accessibility.
         *
         * @param that  an Access specifier
         *
         * @return the lesser (i.e. the more constrained) of the two Access specifiers
         */
        public Access minOf(Access that)
            {
            if (this == that)
                {
                return this;
                }

            if (this == STRUCT || that == STRUCT)
                {
                throw new IllegalStateException("cannot compare struct to other access levels");
                }

            return this.isLessAccessibleThan(that)
                    ? this
                    : that;
            }

        /**
         * Compare this access with that access, and return the one that has MORE accessibility.
         *
         * @param that  an Access specifier
         *
         * @return the greater (i.e. the more accessible) of the two Access specifiers
         */
        public Access maxOf(Access that)
            {
            if (this == that)
                {
                return this;
                }

            if (this == STRUCT || that == STRUCT)
                {
                throw new IllegalStateException("cannot compare struct to other access levels");
                }

            return this.isMoreAccessibleThan(that)
                    ? this
                    : that;
            }

        /**
         * Look up an Access enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Access enum for the specified ordinal
         */
        public static Access valueOf(int i)
            {
            return VALUES[i];
            }

        /**
         * All of the Access enums.
         */
        private static final Access[] VALUES = Access.values();

        /**
         * The Ecstasy keyword associated with the Access enum.
         */
        public final String KEYWORD = name().toLowerCase();

        /**
         * The integer flags used to encode the access enum.
         *
         * @see Component#ACCESS_MASK
         * @see Component#ACCESS_SHIFT
         * @see Component#ACCESS_PUBLIC
         * @see Component#ACCESS_PROTECTED
         * @see Component#ACCESS_PRIVATE
         */
        public final int FLAGS;
        }


    // ----- error codes ---------------------------------------------------------------------------

    /**
     * Unknown error. {0}
     */
    String VE_UNKNOWN                             = "VERIFY-01";
    /**
     * {0} does not have type parameters, but type parameters were provided.
     */
    String VE_TYPE_PARAMS_UNEXPECTED              = "VERIFY-02";
    /**
     * {0} requires {1} type parameters, but {2} type parameters were provided.
     */
    String VE_TYPE_PARAMS_WRONG_NUMBER            = "VERIFY-03";
    /**
     * {0} type parameter {1} must be of type {2}, but has been overridden as {3} by {4}.
     */
    String VE_TYPE_PARAM_INCOMPATIBLE_CONSTRAINT  = "VERIFY-04";
    /**
     * {0} type parameter {1} must be of type {2}, but has been specified as {3} by {4}.
     */
    String VE_TYPE_PARAM_INCOMPATIBLE_TYPE        = "VERIFY-05";
    /**
     * {0} type parameter {1} is specified as two different types ({2} and {3}) by {4}.
     */
    String VE_TYPE_PARAM_CONFLICTING_TYPES        = "VERIFY-06";
    /**
     * {0} is annotated by type {1}, but it is not an explicit class identity.
     */
    String VE_ANNOTATION_NOT_CLASS                = "VERIFY-07";
    /**
     * Unexpected "extends" {0} on {1}; an "extends" specifier cannot occur on interfaces (or on the
     * root Object), there must be only one, and it must occur first (after any annotations, and
     * after the "into" for a mixin).
     */
    String VE_EXTENDS_UNEXPECTED                  = "VERIFY-08";
    /**
     * {0} is missing "extends".
     */
    String VE_EXTENDS_EXPECTED                    = "VERIFY-09";
    /**
     * {0} "extends" {1}, but it is not an explicit class identity.
     */
    String VE_EXTENDS_NOT_CLASS                   = "VERIFY-10";
    /**
     * Class {0} contains a cyclical {1} contribution.
     */
    String VE_CYCLICAL_CONTRIBUTION               = "VERIFY-11";
    /**
     * {0} "implements" {1}, but it is not an explicit class identity.
     */
    String VE_IMPLEMENTS_NOT_CLASS                = "VERIFY-12";
    /**
     * Unexpected annotation {0} on {1}; annotations can only appear in the beginning of the
     * contribution list.
     */
    String VE_ANNOTATION_UNEXPECTED               = "VERIFY-13";
    /**
     * Unexpected "into" {0} on {1}; an "into" specifier can only occur on a mixin, there must be
     * only one, and it must occur first (after any annotations).
     */
    String VE_INTO_UNEXPECTED                     = "VERIFY-14";
    /**
     * Unexpected "incorporates" {0} on {1}; an "incorporates" specifier cannot occur on an
     * interface.
     */
    String VE_INCORPORATES_UNEXPECTED             = "VERIFY-15";
    /**
     * {0} is incorporated by type {1}, but it is not an explicit class identity.
     */
    String VE_INCORPORATES_NOT_CLASS              = "VERIFY-16";
    /**
     * {0} is incorporated by type {1}, but it is not a mixin.
     */
    String VE_INCORPORATES_NOT_MIXIN              = "VERIFY-17";
    /**
     * {0} incorporates {1}, but {2} is not compatible with the "into" specifier: {3}.
     */
    String VE_INCORPORATES_INCOMPATIBLE           = "VERIFY-18";
    /**
     * {0} is delegated by type {1}, but it is not an interface type.
     */
    String VE_DELEGATES_NOT_INTERFACE             = "VERIFY-19";
    /**
     * {0} is implemented by type {1}, but it is not an interface type.
     */
    String VE_IMPLEMENTS_NOT_INTERFACE            = "VERIFY-20";
    /**
     * Unexpected "delegates" {0} on {1}; a "delegates" specifier cannot occur on an
     * interface.
     */
    String VE_DELEGATES_UNEXPECTED                = "VERIFY-21";
    /**
     * Unexpected formal type name {0} encountered while resolving {1}.
     */
    String VE_FORMAL_NAME_UNKNOWN                 = "VERIFY-22";
    /**
     * {0}, which is a {1}, illegally extends {2}, which is a {3}.
     */
    String VE_EXTENDS_INCOMPATIBLE                = "VERIFY-23";
    /**
     * Service type "{0}" cannot be treated as an immutable type.
     */
    String VE_IMMUTABLE_SERVICE_ILLEGAL           = "VERIFY-24";
    /**
     * Warning: Redundant immutable type specification.
     */
    String VE_IMMUTABLE_REDUNDANT                 = "VERIFY-25";
    /**
     * Type "{0}" cannot be annotated because it does not specify a class or interface.
     */
    String VE_ANNOTATION_ILLEGAL                  = "VERIFY-26";
    /**
     * {0} is not a mixin, and thus cannot be used in an annotation.
     */
    String VE_ANNOTATION_NOT_MIXIN                = "VERIFY-27";
    /**
     * The annotation @{0} is repeated.
     */
    String VE_ANNOTATION_REDUNDANT                = "VERIFY-28";
    /**
     * Type "{0}" cannot have accessibility defined because it does not specify a class or interface.
     */
    String VE_ACCESS_TYPE_ILLEGAL                 = "VERIFY-29";
    /**
     * {0} is extended by mixin type {1}, but it is not a mixin.
     */
    String VE_EXTENDS_NOT_MIXIN                   = "VERIFY-30";
    /**
     * {0} is not a type that can be parameterized.
     */
    String VE_PARAM_TYPE_ILLEGAL                  = "VERIFY-31";
    /**
     * {0} is annotated by {1}, but is not compatible with the required "into": {2}.
     */
    String VE_ANNOTATION_INCOMPATIBLE             = "VERIFY-32";
    /**
     * {0} type parameter {1} is of type {2}, which conflicts with the type parameter contribution
     * from {3} of type {4}.
     */
    String VE_TYPE_PARAM_INCOMPATIBLE_CONTRIB     = "VERIFY-33";
    /**
     * {0} type parameter {1} is not specified, which conflicts with the type parameter contribution
     * from {2} of type {3}.
     */
    String VE_TYPE_PARAM_CONTRIB_HAS_SPEC         = "VERIFY-34";
    /**
     * {0} type parameter {1} is of type {2}, which conflicts with the type parameter contribution
     * from {3} of the unspecified type.
     */
    String VE_TYPE_PARAM_CONTRIB_NO_SPEC          = "VERIFY-35";
    /**
     * {0} contains a property {1} which collides with a type parameter of the same name.
     */
    String VE_TYPE_PARAM_PROPERTY_COLLISION       = "VERIFY-36";
    /**
     * {0} missing a property {1} for the type parameter of the same name.
     */
    String VE_TYPE_PARAM_PROPERTY_MISSING         = "VERIFY-37";
    /**
     * {0} has a misconfigured property {1} for the type parameter of the same name.
     */
    String VE_TYPE_PARAM_PROPERTY_INCOMPATIBLE    = "VERIFY-38";
    /**
     * The {0} property on {1} is annotated by {2}, which is an incompatible mixin for a property.
     */
    String VE_PROPERTY_ANNOTATION_INCOMPATIBLE    = "VERIFY-39";
    /**
     * The "get()" method on the {1} property on {0} is ambiguous.
     */
    String VE_PROPERTY_GET_AMBIGUOUS              = "VERIFY-40";
    /**
     * The "get()" method on the {1} property on {0} does not match the property type.
     */
    String VE_PROPERTY_GET_INCOMPATIBLE           = "VERIFY-41";
    /**
     * The "set()" method on the {1} property on {0} is ambiguous.
     */
    String VE_PROPERTY_SET_AMBIGUOUS              = "VERIFY-42";
    /**
     * The "set()" method on the {1} property on {0} does not match the property type.
     */
    String VE_PROPERTY_SET_INCOMPATIBLE           = "VERIFY-43";
    /**
     * Interface {0} contains an illegal property declaration for {1}: Interface properties cannot include an implementation.
     */
    String VE_INTERFACE_PROPERTY_IMPLEMENTED      = "VERIFY-44";
    /**
     * Interface {0} contains an illegal property declaration for {1}: Interface properties cannot include Ref or Var annotations.
     */
    String VE_INTERFACE_PROPERTY_ANNOTATED        = "VERIFY-45";
    /**
     * Interface {0} contains an illegal property declaration for {1}: Interface properties must not specify @Inject.
     */
    String VE_INTERFACE_PROPERTY_INJECTED         = "VERIFY-46";
    /**
     * Property {0} overrides the property {1}, so it must specify @Override.
     */
    String VE_PROPERTY_OVERRIDE_REQUIRED          = "VERIFY-47";
    /**
     * {0} contains an @RO property {1} that has a "set()" that uses "super", or a Var annotation.
     */
    String VE_PROPERTY_READONLY_NOT_VAR           = "VERIFY-48";
    /**
     * {0} contains an @Inject property {1} that also implements "get()", "set()", or has a Ref or Var annotation.
     */
    String VE_PROPERTY_INJECT_NOT_OVERRIDEABLE    = "VERIFY-49";
    /**
     * {0} contains an @RO property {1} that does not have a "get()" and is not annotated with "@Abstract", "@Override", or "@Inject".
     */
    String VE_PROPERTY_READONLY_NO_SPEC           = "VERIFY-50";
    /**
     * {0} contains an @Override property {1}, but no property declaration exists to override.
     */
    String VE_PROPERTY_OVERRIDE_NO_SPEC           = "VERIFY-51";
    /**
     * {0} contains a duplicate annotation: {1}.
     */
    String VE_DUP_ANNOTATION                      = "VERIFY-52";
    /**
     * {0} contains a duplicate incorporates clause: {1}.
     */
    String VE_DUP_INCORPORATES                    = "VERIFY-53";
    /**
     * {0} contains a duplicate delegates clause: {1}.
     */
    String VE_DUP_DELEGATES                       = "VERIFY-54";
    /**
     * {0} contains a duplicate implements clause: {1}.
     */
    String VE_DUP_IMPLEMENTS                      = "VERIFY-55";
    /**
     * The property {1} on {0} contains a duplicate initializer function.
     */
    String VE_DUP_INITIALIZER                     = "VERIFY-56";
    /**
     * The constant property {1} on {0} contains custom code.
     */
    String VE_CONST_CODE_ILLEGAL                  = "VERIFY-57";
    /**
     * The access for the {1} property on {0} is illegally specified as "struct".
     */
    String VE_PROPERTY_ACCESS_STRUCT              = "VERIFY-58";
    /**
     * The Ref access is more restricted than the Var access for the {1} property on {0}.
     */
    String VE_PROPERTY_ACCESS_ILLEGAL             = "VERIFY-59";
    /**
     * The constant property {1} on {0} does not have an initial value.
     */
    String VE_CONST_VALUE_REQUIRED                = "VERIFY-60";
    /**
     * The constant property {1} on {0} has more than one initial value.
     */
    String VE_CONST_VALUE_REDUNDANT               = "VERIFY-61";
    /**
     * The constant property {1} on {0} is declared as @Abstract.
     */
    String VE_CONST_ABSTRACT_ILLEGAL              = "VERIFY-62";
    /**
     * The constant property {1} on {0} is declared as a read/write property.
     */
    String VE_CONST_READWRITE_ILLEGAL             = "VERIFY-63";
    /**
     * The declaration of the {1} property on {0} implies both a read-only and a read/write property.
     */
    String VE_PROPERTY_READWRITE_READONLY         = "VERIFY-64";
    /**
     * The super method for {0} is ambiguous.
     */
    String VE_SUPER_AMBIGUOUS                     = "VERIFY-65";
    /**
     * Property information for {0} contains both a regular property and a type parameter.
     */
    String VE_PROPERTY_ILLEGAL                    = "VERIFY-66";
    /**
     * Property information for {0} contains conflicting types {1} and {2}.
     */
    String VE_PROPERTY_TYPES_INCOMPATIBLE         = "VERIFY-67";
    /**
     * Property information for {0} contains conflicting constant information.
     */
    String VE_CONST_INCOMPATIBLE                  = "VERIFY-68";
    /**
     * {0} contains multiple methods that attempt to narrow {1}, including {2}; only a single
     * narrowing method can exist, unless the method being narrowed is also extended without
     * being narrowed, such that it remains accessible.
     */
    String VE_METHOD_NARROWING_AMBIGUOUS          = "VERIFY-69";
    /**
     * A super method for {0} on {1} is indicated by the "@Override" annotation, but cannot be found.
     */
    String VE_SUPER_MISSING                       = "VERIFY-70";
    /**
     * The interface property {1} on {0} is declared as @Abstract; that annotation is not permitted.
     */
    String VE_INTERFACE_PROPERTY_ABSTRACT_ILLEGAL = "VERIFY-71";
    /**
     * An annotation on the constant {1} on {0} is illegal: Constants cannot include Ref or Var annotations.
     */
    String VE_CONST_ANNOTATION_ILLEGAL            = "VERIFY-72";
    /**
     * The @Override annotation on the constant {1} on {0} is illegal.
     */
    String VE_CONST_OVERRIDE_ILLEGAL              = "VERIFY-73";
    /**
     * The function {0} contains a property {1}; functions must not contain properties.
     */
    String VE_FUNCTION_CONTAINS_PROPERTY          = "VERIFY-74";
    /**
     * The annotation {2} on property {1} on {0} duplicates an annotation that is already present from the base property; the annotation on the derived property is ignored.
     */
    String VE_DUP_ANNOTATION_IGNORED              = "VERIFY-75";
    /**
     * The annotation {2} on property {1} on {0} is a super-class of the annotation {3} that is already present from the base property; the annotation on the derived property is ignored.
     */
    String VE_SUP_ANNOTATION_IGNORED              = "VERIFY-76";
    /**
     * The property {1} on {0} attempts to declare a Var property, but the setter on the base is private.
     */
    String VE_PROPERTY_SET_PRIVATE_SUPER          = "VERIFY-77";
    /**
     * Interface {0} contains an illegal property declaration for {1}: An interface property must be declared as @RO to specify a default get() implementation.
     */
    String VE_INTERFACE_PROPERTY_GET_REQUIRES_RO  = "VERIFY-78";
    /**
     * The access for the {1} property on {0} is defined more restrictively than its base.
     */
    String VE_PROPERTY_ACCESS_LESSENED            = "VERIFY-79";
    /**
     * {0} attempts to override the method {1} on {2}, but the method cannot be overridden.
     */
    String VE_METHOD_OVERRIDE_ILLEGAL             = "VERIFY-80";
    /**
     * {0} attempts to override the method {1} on {2}, but does not specify @Override.
     */
    String VE_METHOD_OVERRIDE_REQUIRED            = "VERIFY-81";
    /**
     * Unexpected auto-narrowing contribution "{0}" for type "{1}".
     */
    String VE_UNEXPECTED_AUTO_NARROW              = "VERIFY-82";
    /**
     * A virtual child {0} cannot be found on {1}.
     */
    String VE_VIRTUAL_CHILD_MISSING               = "VERIFY-83";
    /**
     * {0} contains more than one component with the name {1}.
     */
    String VE_NAME_COLLISION                      = "VERIFY-84";
    /**
     * While resolving {0}, a child with the name {1} from the contribution {2} collided with an
     * existing child {3}.
     */
    String VE_CHILD_COLLISION                     = "VERIFY-85";
    /**
     * {0} is not an instantiable type because it doesn't implement a virtual constructor {1}.
     */
    String VE_NEW_VIRTUAL_CONSTRUCT               = "VERIFY-86";
    /**
     * The access for the method {1} on {0} is defined more restrictively than its base.
     */
    String VE_METHOD_ACCESS_LESSENED              = "VERIFY-87";
    /**
     * The type modifier is not permitted for contribution {1} on {0}.
     */
    String VE_TYPE_MODIFIER_ILLEGAL = "VERIFY-88";

    // ----- miscellaneous -------------------------------------------------------------------------

    /**
     * Compile-time debug flag.
     */
    boolean DEBUG = true;
    }