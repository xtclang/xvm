module Test
    {
    class Fubar
        {
        Int[] numbers;
        Object... params;

// problem #1 - is the solution a SubstitutableTypeConstant that takes the place of each instance of "T"?
        <T> conditional T foo(T t)          // compiled as "(Boolean*, T) foo(Type<Object> T*, T t)"
            {
            return t;
            }

// problem #2 ".Type" resolution ... kind of like problem #2 ... hmmm ...
        Fubar! fn(String s)
            {
            return this;
            }
        }

    Void foo(Int i) {}

// problem #3 - functions as types
    (function (Int, Int) (String, String)) fn; /* TODO ((Int, Int) f(String, String))
        {
        return f;
        }*/

//  class List<ElementType extends Int>
//      {
//      Void add(ElementType value);
//      }
    }