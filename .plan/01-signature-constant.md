# SignatureConstant Array-to-List Conversion

## Status: In Progress

## Fields
- [x] `m_listConstParams` - List<TypeConstant> (was m_aconstParams)
- [x] `m_listConstReturns` - List<TypeConstant> (was m_aconstReturns)

## Constructors
- [x] Main constructor takes List<TypeConstant>
- [ ] Add @NotNull annotations
- [ ] Add Objects.requireNonNull validation
- [ ] Add no-arg convenience constructor defaulting to List.of()

## Methods Updated
- [x] getParams() returns List
- [x] getReturns() returns List
- [x] resolveGenericTypes()
- [x] resolveAutoNarrowing()
- [x] resolveTypedefs()
- [x] asFunctionType()
- [x] asBjarneLambdaType()
- [x] asMethodType()
- [x] asConstructorType()
- [x] truncateParams()
- [x] getValueString()
- [x] registerConstants()

## Removed Methods
- [x] getRawParams() - removed
- [x] getRawReturns() - removed
