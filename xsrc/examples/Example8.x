// the "global" namespace is composed of the union of the top-level namespace and the "inner"
// namespace of each component in the global namespace.
//
// modifiers for "top-level" namespace structures:
// - "top-level" means nested within a file, module, or package structure
// - static means "singleton"
// - public means visible outside of the module
// - protected means t.b.d.
// - private means no visibility outside of the module
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// module       (implicit)                          (implicit)
// package      x           x           x           (implicit)
// class        x           x           x
// interface    x           x           x
// service      x           x           x           x
// const        x           x           x           x
// enum         x           x           x           (implicit)
// trait        x           x           x
// mixin        x           x           x
//
// modifiers for "inner" namespace structures:
// - "inner" means nested within a class
// - static means "no ref to parent, no virtual new"; it only applies to something that can be new'd
//   or init'd from a constant, so it does not apply to interface, trait, or mixin
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// class        x           x           x           x
// interface    x           x           x
// service      x           x           x           x - required if parent is not const or service
// const        x           x           x           x - required if parent is not const
// enum         x           x           x           (implicit)
// - enum val                                       (implicit)
// trait        x           x           x
// mixin        x           x           x

// modifiers for "local" namespace structures:
// - "local" means declared within a method; items declared within a method are not visible outside
//   of (above on the hierarchy) the method
// - static means "no ref to the method frame", i.e. no ability to capture, not even the "this"
//   from the method
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// class                                            x
// interface
// service                                          x
// const                                            x
// enum                                             x
// - enum val
// trait
// mixin
//

Boolean global;
Boolean innerClass;
enum DeclarationZone {TopLevel, Class, Method}
Zone    zone;


Void foo()
    {
    Int i = 4;

    const Point(Int x, Int y)
        {
        Int adjustX()
            {
            return x + i;   // CANNOT CAPTURE "i" from within a const!!!
            }
        }
    }


module MyTopSecretCalc
    {
    package MyApp import:required MyApp;

    static Int calc(Int x, Int y)
        {
        return x * y - MyApp.OFFSET;
        }
    }

module MyApp
    {
    package Calc import:embedded MyTopSecretCalc;

    protected static Int OFFSET = 3;
    }