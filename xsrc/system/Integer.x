value Integer
        implements Number
    {
    @ro boolean Signed;

    Integer add(Integer n);
    Integer sub(Integer n);
    Integer multiply(Integer n);
    (Integer, Integer) divide(Integer n);
    Integer modulo(Integer n);

    Integer shiftLeft(Integer n);
    Integer shiftRight(Integer n);
    Integer shiftLogicalRight(Integer n);
    Integer rotateLeft(Integer n);
    Integer rotateRight(Integer n);

    Integer to<Integer>()
        {
        return this;
        }
    }
