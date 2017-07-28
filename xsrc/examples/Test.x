module Test
    {
    class Fubar
        {
        Int[] numbers;
        Object... params;

// problem #1 - is the solution a SubstitutableTypeConstant that takes the place of each instance of "T"?
//      <T> T foo(T t)
//          {
//          return t;
//          }

// problem #2 ".Type" resolution ... kind of like problem #2 ... hmmm ...
        Fubar.Type fn()
            {
            return this;
            }
        }

//    Void foo(Int i) {}

//    class List<ElementType extends Int>
//        {
//        Void add(ElementType value);
//        }
    }