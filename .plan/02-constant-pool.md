# ConstantPool List API Updates

## Status: In Progress

## New List-based Methods (Primary)
- [x] ensureParameterizedTypeConstant(TypeConstant, List<TypeConstant>)
- [x] ensureTupleType(List<TypeConstant>)
- [x] buildFunctionType(List<TypeConstant>, List<TypeConstant>)
- [x] buildMethodType(TypeConstant, List<TypeConstant>, List<TypeConstant>)
- [x] ensureSignatureConstant(String, List<TypeConstant>, List<TypeConstant>)

## Deprecated Array Methods (delegate to List versions)
- [x] ensureParameterizedTypeConstant(TypeConstant, TypeConstant...)
- [x] buildFunctionType(TypeConstant[], TypeConstant...)
- [x] buildMethodType(TypeConstant, TypeConstant[], TypeConstant...)

## Usage Updates
- [x] sigValidator() - uses List.of()
