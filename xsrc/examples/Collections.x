public interface Set<E extends Value> // needs equality: either via Value or Hasher
        extends Collection<E>
    {
    }

public interface List<E>
        extends Collection<E>
    {
    @ro public E get(int index);

    // @return -1 for not found or a boolean?
    @ro public int indexOf(E el);
    @ro public int lastIndexOf(E el);

    public E insert(int index, E el);
    public E replace(int index, E el);
    public E remove(int index);
    }
