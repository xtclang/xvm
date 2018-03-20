/**
 * An internal Ref implementation.
 */
class RefImpl<RefType>
        implements Ref<RefType>
    {
    // --- Ref -----

    @Override
    @RO Boolean assigned;

    @Override
    RefType get();

    @Override
    @RO Type ActualType;

    @Override
    @RO String? name;

    @Override
    @RO Int byteLength;

    @Override
    @RO Boolean selfContained;

    // --- Referent -----

    @Override
    <AsType> AsType maskAs<AsType>();

    @Override
    <AsType> conditional AsType revealAs<AsType>();

    @Override
    Boolean instanceOf(Type type);

    @Override
    Boolean implements_(Class interface_);

    @Override
    Boolean extends_(Class class_);

    @Override
    Boolean incorporates_(Class mixin_);

    @Override
    @RO Boolean service_;

    @Override
    @RO Boolean const_;

    @Override
    @RO Boolean immutable_;
    }
