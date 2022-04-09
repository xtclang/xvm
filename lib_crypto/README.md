# Cryptographic Library

Folder: `./lib_crypto/`

Status: Prototype, in review stage

* This directory contains the Ecstasy code for the standard `crypto.xtclang.org` module.
* This is not part of the build yet, and is unlikely to compile without the various
  minor edits that one would expect to have to perform to get new code to compile.
* This library is intended to serve the `net.xtclang.org` and `web.xtclang.org` modules,
  but the former is not yet pushed to the public repo, and the latter is still undergoing
  significant refactoring.

Example code:

    // the basic idea is that, for the most part, it will be possible to rely on the
    // cryptographic implementations (and possibly the cryptographic material) to be
    // injected
    @Inject Algorithms algorithms; 

    // the algorithms are a repository of supported cryptographic algorithms for
    // signing/verifying (aka "cryptographic hashes" aka "message digest algorithms")
    // and encrypting/decrypting messages
    Encryptor encryptor  = algorithms.encryptorFor("ChaCha20-Poly1305", publicKeyBytes);
    Byte[] encryptedData = encryptor.encrypt(messageBytes);

    Decryptor decryptor = algorithms.decryptorFor("ChaCha20-Poly1305", keyPairBytes);
    Byte[] decryptedData = decryptor.decrypt(encryptedData);

    // assuming everything works, this should not blow up
    assert messageBytes == decryptedData;

    // signing example:
    Signer signer = algorithms.signerFor("MD5", keyPairBytes);
    Signature sig = signer.sign(messageData);
    assert signer.verify(sig, decryptedData);
                                                     
Like the `Encryptor`/`Decryptor`, there is also a `Verifier`/`Signer`. The `Decryptor`
extends the `Encryptor`, just like the `Signer` extends the `Verifier`. The `Verifier`
and the `Encryptor` only need a public key when a public/private key pair algorithm is
used.