package org.xvm.cc_explore;


/**
   A Container: a self-contained set of types
*/
public abstract class Container {
  final Container _par;         // Parent container
  final XEC.ModRepo _repo;      // Where to find more types

  Container( Container par, XEC.ModRepo repo ) {
    _par = par;
    _repo = repo;
  }
  
}
