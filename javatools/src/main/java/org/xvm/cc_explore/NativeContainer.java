package org.xvm.cc_explore;

class NativeContainer extends Container {
  NativeContainer( ) { super(null,null); }

  // Initialize default things into the container?
  void init() {
    // +++ temporal.LocalClock
    // +++ temporal.NanosTimer
    // +++ io.Console
    // +++ numbers.Random
    // +++ fs.OSFileStore etc.
    // +++ net:Network
    // +++ crypto:KeyStore
    // +++ crypto:Algorithms
    // +++ web:Client
    // +++ web:WebServer
    // +++ web:Authenticator (Nullable|Authenticator)
    // +++ mgmt.Linker
    // +++ mgmt.ModuleRepository
    // +++ lang.src.Compiler
    // +++ xvmProperties
  }

}
