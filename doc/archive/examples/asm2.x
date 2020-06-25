Void foo()
    {
    return;             // RETURN_0
    }

Int foo()
    {
    return 1;           // RETURN_1 constantOf(1)
    }

Int foo()
    {
    Int i = 1;          // INVAR Int, "i", constantOf(1)
    return i;           // RETURN_1 i
    }

Int foo()
    {
    Int i = 1;          // INVAR Int, "i", constantOf(1)
    Int j = i;          // NVAR Int, "j"
                        // MOV i, j
    return j;           // RETURN_1 j
    }

