typedef / import
TypeComposition
    {
    properties
    methods
        {
        properties
        methods
        functions
        constants
        TypeComposition
        ... and code
        }
    functions
        {
        functions
        constants       ??
        TypeComposition ??
        ... and code
        }
    constants
    TypeComposition
    }


TypeComposition
    Modifiers-opt Category Name TypeParams

Modifier
    "public" "protected" "private" "static" ...
Category
    "module" "package" "class" "interface" "service" "const" "enum" "trait" "mixin"


Type
    immutable Type
    Type?               Nullable | Type
    Type[]              Array<Type>
    Type<Params>        parameterized type
    Type | Type         one type of the other (intersection)
    Type + Type         both types (union)
    (Type)              parentheses allow precedence to be explicit
    Type...             Sequence<Type> (or is it also just an array?)
    enum {ValueList}    anonymous enum in a parameter/variable declaration
    Annotation Type     trait/mixin via annotation

    function ReturnValues Identifier ( Params )
    function ReturnValues ( Params ) Identifier

    QualifiedName
    Name
        - from "typedef" ...
        - from "import" ...


question: is "<A, B>" a "tuple type of A and B"? i.e. is "(a, b)" of type "<A, B>"?