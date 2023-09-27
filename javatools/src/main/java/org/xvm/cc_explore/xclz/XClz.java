package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.xrun.*;

// Some kind of base class for a Java class that implements an XTC Module
public abstract class XClz {
  private final byte _mutability;
  public XClz() { _mutability=0; }
  public XClz(byte mut) { _mutability=mut; }
  // Default mutability
  public Mutability mutability$get() { return Mutability.VALUES[_mutability]; }

  public enum Mutability {
    Constant,                   // Deeply immutable
    Persistant,                 // Odd name, but shallow immutable
    Fixed;                      // Tuples and arrays are fixed length, but mutable
    static final Mutability[] VALUES = values();
  }    
}
