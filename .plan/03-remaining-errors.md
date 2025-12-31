# Remaining Compilation Errors

## Pattern: Change getRawParams()/getRawReturns() to getParams()/getReturns()
- Use .get(i) instead of [i]
- Use .size() instead of .length
- Use .isEmpty() instead of .length == 0
- Use .getFirst() instead of [0]

## Files to Fix

### MethodConstant.java
- [ ] Line 292 - getRawParams/getRawReturns
- [ ] Line 395 - getRawParams

### TypeInfo.java
- [ ] Line 1842 - getRawParams
- [ ] Line 1858 - getRawReturns
- [ ] Line 2151 - getRawParams
- [ ] Line 2161 - getRawReturns

### TypeParameterConstant.java
- [ ] Line 102 - getRawParams

### Frame.java
- [ ] Lines 2557-2559 - getRawParams/getRawReturns

### CallChain.java
- [ ] Line 614 - getRawParams

### AstNode.java
- [ ] Line 1304
- [ ] Line 1573
- [ ] Line 1574
- [ ] Line 1638

### NameResolver.java
- [ ] Line 294

### NewExpression.java
- [ ] Line 710
