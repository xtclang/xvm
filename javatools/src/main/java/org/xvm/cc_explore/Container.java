package org.xvm.cc_explore;


/**
   A Container: a self-contained set of types
*/
public class Container {
  final XEC.ModRepo _repo;      // Where to find more types
  final ModPart _mod;           // Starting module

  Container( XEC.ModRepo repo, ModPart mod ) {
    _repo = repo;
    _mod = mod;
  }
  
}
