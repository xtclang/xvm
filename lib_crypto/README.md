# Cryptographic Library

Folder: `./lib_crypto/`

Status: Prototype, in review stage

* This directory contains the Ecstasy code for the standard `crypto.xtclang.org` module.
* This library is intended to serve the `net.xtclang.org` and `web.xtclang.org` modules,
  but the former is still a prototype, and the latter is still undergoing significant
  refactoring.

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

## Authenticated encryption

Application data should use an authenticated-encryption algorithm whenever both confidentiality and
integrity are required. AES-GCM is available through the `AuthenticatedEncryptor` and
`AuthenticatedDecryptor` capabilities:

    @Inject Algorithms algorithms;

    assert AuthenticatedDecryptor decryptor :=
        algorithms.authenticatedDecryptorFor("AES/GCM/NoPadding", aesKey);

    Byte[] associatedData = "tenant-id:field-name".utf8();
    AuthenticatedCiphertext encrypted = decryptor.seal(secretBytes, associatedData);

    assert Byte[] decrypted := decryptor.open(encrypted, associatedData);
    assert decrypted == secretBytes;

`seal()` generates a fresh 96-bit nonce for every operation. `AuthenticatedCiphertext` carries that
nonce and the ciphertext with its appended 128-bit authentication tag. The associated data is
authenticated but not encrypted and must be supplied unchanged to `open()`.

NIST SP 800-38D limits randomly generated 96-bit nonces to 2^32 invocations under one key. The
runtime enforces that ceiling per encryptor instance; applications must rotate the key before
reaching 2^32 total seals across all instances that use it.

`open()` returns `False` if authentication fails and never returns unauthenticated plaintext. This
includes a wrong key or associated data, and any modification to the nonce, ciphertext, or tag.

The provider-dependent `"AES"` transformation commonly selects ECB mode and is not suitable for
application data. CBC also requires explicit IV handling and separate authentication. Use
`"AES/GCM/NoPadding"` for self-contained, authenticated messages.

## Key wrapping

AES-KW and AES-KWP protect secret keys using an AES key-encryption key. They are available through
the `KeyWrapper` and `KeyUnwrapper` capabilities:

    @Inject Algorithms algorithms;

    assert KeyUnwrapper unwrapper :=
        algorithms.keyUnwrapperFor("AES/KW/NoPadding", keyEncryptionKey);

    WrappedKey wrapped = unwrapper.wrap(applicationKey);

    assert CryptoKey recovered :=
        unwrapper.unwrap(wrapped, "application-key", "AES");

Use `"AES/KW/NoPadding"` for RFC 3394 interoperability when the wrapped key is at least 16 bytes
and its size is a multiple of eight bytes. Use `"AES/KWP/NoPadding"` for RFC 5649 interoperability
when key material has another non-zero size. Both transformations accept 16-, 24-, and 32-byte AES
key-encryption keys and are supplied by the standard JDK provider.

Wrapping is deterministic and is only intended for cryptographic keys; use AES-GCM for application
data. `unwrap()` returns `False` for malformed input, a wrong key-encryption key, or an integrity
failure. A recovered key is opaque. Its name and algorithm must come from trusted, out-of-band
configuration and are deliberately not stored as unauthenticated `WrappedKey` metadata.