package org.xvm.cc_explore.xrun;

public class NativeContainer extends Container {

  final Console _console;
  
  public NativeContainer( ) {
    super(null,null);
    _console = new Console();
  }

  @Override public Console console() { return _console; }
  
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
