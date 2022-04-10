# Networking Library

Folder: `./lib_net/`

Status: Prototype, in review stage

* This directory contains the Ecstasy code for the standard `net.xtclang.org` module.
* This is not part of the XDK build (yet).
* This library is intended to serve the `web.xtclang.org` module (and anything else that
  needs network communication), but the web module is still undergoing significant
  refactoring.

Example code:

    // the basic idea is that, for the most part, it will be possible to rely on the
    // network implementations to be injected
    @Inject Network network; 
    @Inject Network secureNetwork;      // for SSL/TLS connections 

    assert ServerSocket listenerHTTP  := network.listen((IPv6Any, 80));
    assert ServerSocket listenerHTTPS := secureNetwork.listen((IPv6Any, 443));
