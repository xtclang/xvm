
// concurrency, CAS, STM
// exception handling


Block
    {
    run()
    }

ExecutionFrame
    {

    }

enumeration Order<T>
    {
    LessThan,
    Equal,
    GreaterThan

    Order compare(T)
    Void compare(Order, Block lessthan, Block equal, Block greaterthan)


    }

class Void
    {
    }

class Number
    {

    }

enumeration Boolean
    {
    True,
    False

    if(Block ifTrue)
    if(Block ifTrue, Block ifFalse)
    else(Block ifFalse)
    }

enumeration Sign
    {
    Negative,
    Zero,
    Positive
    }

class Integer
    {
    Boolean signed
    }

class FloatingPoint
    {
    Integer radix

    }

class Int

class Date
 {
 }
--

properties

class Person {
  public int X
    {
    public get();
    private set(int value) {}
    }
}