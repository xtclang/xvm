value Integer32
        implements Integer
    {
//    @ro boolean Signed;

//    Integer32 add(Integer32 n);
    Integer sub(Integer n);
    Integer multiply(Integer n);
    (Integer, Integer) divide(Integer n);
    Integer modulo(Integer n);

    Integer shiftLeft(Integer n);
    Integer shiftRight(Integer n);
    Integer shiftLogicalRight(Integer n);
    Integer rotateLeft(Integer n);
    Integer rotateRight(Integer n);

    Integer32 to<Integer32>()
        {
        return this;
        }
    }
