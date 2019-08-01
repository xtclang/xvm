// first thing to understand is the "implied" byte code that is generated
// automatically, but because it's implicit, it doesn't show up. for example,
// with a Void method taking no parameters (other than the "this" i.e. target),
// there is a scope for the method:
Void foo()
    {                       // ENTER                ; doesn't actually exist in the byte-code
                            // NVAR this:type this  ; doesn't actually exist in the byte-code
                            // RETURN_0             ; this DOES exist in the byte-code
    }                       // EXIT                 ; doesn't actually exist in the byte-code
// the same goes for named parameters
Void foo(Int i)
    {                       // ENTER                ; doesn't actually exist in the byte-code
                            // NVAR this:type this  ; doesn't actually exist in the byte-code
                            // NVAR Int i           ; doesn't actually exist in the byte-code
                            // RETURN_0             ; this DOES exist in the byte-code
    }                       // EXIT                 ; doesn't actually exist in the byte-code

Void foo()
    {
    return;                 // RETURN_0
    }



// a specific "Int64" constant type in the constant pool
Int foo()
    {
    return 99;              // RETURN_1 -17         ; 99 goes into constant pool as IntConstant #17
    }

// if the return type is ambiguous, the compiler would do this:
Object foo()
    {
    return 99;              // IVAR Int64 -17       ; 99 goes into constant pool as IntConstant #17
                            // RETURN_1 1;          ; register 1
    }

// choice 3: generic approach using return type of the function to figure out what type to convert the "int constant" into
Int foo()
    {
    return 99;              // RETURN_1 -99         ; 99 goes into constant pool
    }

Int foo(Int x)
    {
    return x + 1;           // ADD #2 #1 1          ; x is #1; temp is #2
                            // RETURN_1 #2
    }

// conditional return value example
Int sum(Iterator<Int> iter)     //                  ; iter is #1
    {
    Int sum = 0;                // INVAR Int sum 0  ; #2
    while (Int n : iter.next()) // ENTER
                                // VAR Boolean      ; #3
                                // NVAR Int n       ; #4
                                // INVOKE_0N #1 next() 2 #3 #4
        {                       // JMP_FALSE #3 xxx ; relative address of the RETURN_1 op below
        sum += n;               // ADD_ASN #2 #4    ;
        }                       // EXIT
                                // JMP xxx          ; relative address of while (ENTER op)
    return sum;                 // RETURN_1 sum
    }

// implicit cast resulting from type check
Int foo(Int? x)                         //          ; x is #1
    {
    return x instanceof Int ? x : 0;    // JMP_NTYPE #1 Int xxx
                                        // RETURN_1 x
                                        // RETURN_1 0
    }

// implicit cast resulting from type check
Int foo(Int? x)                 //                  ; x is #1
    {
    if (x instanceof Int)       // JMP_NTYPE #1 Int xxx
        {                       // ENTER            ; in this branch, x is known to be Int
        return x + 1;           // ADD #1 1 #2      ; anon temp var introduced as #2
                                // RETURN_1 #2
        }                       // EXIT

    return 0;                   // RETURN_1 0
    }

// implicit cast resulting from type check
Int foo(Int? x)                 //                  ; x is #1
    {
    if (x != null)              // JMP_NULL #1 xxx
        {                       // ENTER            ; in this branch, x is known to be Int
        return x + 1;           // ADD #1 1 #2      ; anon temp var introduced as #2
                                // RETURN_1 #2
        }                       // EXIT

    return 0;                   // RETURN_1 0
    }

// from http://dynamic-languages-symposium.org/dls-16/program/media/McIlroy_2016_IgnitionJumpStartingAnInterpreterForV8_Dls.pdf
Int f(Int a, Int b, Int c)
    {
    Int local = c - 100;        // SUB #3 100 #4
    return a + local * b;       // MUL #4 #2 #5
                                // ADD #1 #5 #5
                                // RETURN_1 #5
    }

Void foo()
    {
    Class c = HashMap;
    }