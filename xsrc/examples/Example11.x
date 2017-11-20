// resolving outer class names and references
// - it's as if as we walk up the AST tree, that there are 2 pieces of information that we're
//   tracking: (1) what AST node we're on, and (2) if there is still an "instance link" from where
//   we started (once lost, it's lost)
class Outer
    {
    Int prop;

    class InnerInstance
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer.this is available
            }
        }

    static class InnerStatic
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // compiler error because Outer.this is NOT available
            }
        }
    }

// although there is a theoretical exception to this when we know the outer reference because it's
// a singleton
static service Outer
    {
    Int prop;

    class InnerInstance
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer.this is available
            }
        }

    static class InnerStatic
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer (singleton instance) is available
            }
        }
    }

// walking down is similar in that it has to keep both (1) an identity and (2) the knowledge of
// whether or not a reference is possible:
// - a "this"
// - an outer "this"
// - a singleton (const / service)
// - value of a property
// - return from a method

// so basically any type expression / class name / etc. that

// lambdas need a way to resolve names and “capture” references:
class Outer
    {
    Int iProp;

    class Inner
        {
        Int iProp2;

        Void foo(Int iParam)
            {
            Int iLocal = 4;

            function Int() f1 = () -> iLocal;   // adds & passes in a hidden parameter for iLocal
            // actually creates:
            // function Int() f1Unbound = (Int iLocalCopy) -> iLocalCopy;
            // function Int() f1 = f1Unbound(iLocal)!;
            //  FBIND f1Unbound 1 0:iLocal

            function Int() f2 = () -> iParam;   // adds & passes in a hidden parameter for iParam

            function Int() f3 = () -> iProp;    // adds & passes in a hidden parameter for Outer.this
            function Int() f4 = () -> iProp2;   // adds & passes in a hidden parameter for Inner.this
            }
        }
    }