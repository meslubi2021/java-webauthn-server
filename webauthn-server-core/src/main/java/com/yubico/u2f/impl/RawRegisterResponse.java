/*
 * Copyright 2014 Yubico.
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file or at
 * https://developers.google.com/open-source/licenses/bsd
 */

package com.yubico.u2f.impl;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.yubico.u2f.exceptions.U2fBadInputException;
import com.yubico.util.ByteInputStream;
import com.yubico.util.CertificateParser;
import com.yubico.util.U2fB64Encoding;
import com.yubico.webauthn.Crypto;
import com.yubico.webauthn.impl.BouncyCastleCrypto;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * The register response produced by the token/key
 */
@EqualsAndHashCode
@ToString
public class RawRegisterResponse {
    public static final byte REGISTRATION_RESERVED_BYTE_VALUE = (byte) 0x05;
    public static final byte REGISTRATION_SIGNED_RESERVED_BYTE_VALUE = (byte) 0x00;

    @EqualsAndHashCode.Exclude
    private transient final Crypto crypto;

    /**
     * The (uncompressed) x,y-representation of a curve point on the P-256
     * NIST elliptic curve.
     */
    final byte[] userPublicKey;

    /**
     * A handle that allows the U2F token to identify the generated key pair.
     */
    final byte[] keyHandle;
    final X509Certificate attestationCertificate;

    /**
     * A ECDSA signature (on P-256)
     */
    final byte[] signature;

    public RawRegisterResponse(byte[] userPublicKey,
                               byte[] keyHandle,
                               X509Certificate attestationCertificate,
                               byte[] signature) {
        this(userPublicKey, keyHandle, attestationCertificate, signature, new BouncyCastleCrypto());
    }

    public RawRegisterResponse(byte[] userPublicKey,
                               byte[] keyHandle,
                               X509Certificate attestationCertificate,
                               byte[] signature,
                               Crypto crypto) {
        this.userPublicKey = userPublicKey;
        this.keyHandle = keyHandle;
        this.attestationCertificate = attestationCertificate;
        this.signature = signature;
        this.crypto = crypto;
    }

    public static RawRegisterResponse fromBase64(String rawDataBase64, Crypto crypto) throws U2fBadInputException {
        ByteInputStream bytes = new ByteInputStream(U2fB64Encoding.decode(rawDataBase64));
        try {
            byte reservedByte = bytes.readSigned();
            if (reservedByte != REGISTRATION_RESERVED_BYTE_VALUE) {
                throw new U2fBadInputException(
                        "Incorrect value of reserved byte. Expected: " + REGISTRATION_RESERVED_BYTE_VALUE +
                                ". Was: " + reservedByte
                );
            }

            return new RawRegisterResponse(
                    bytes.read(65),
                    bytes.read(bytes.readUnsigned()),
                    CertificateParser.parseDer(bytes),
                    bytes.readAll(),
                    crypto
            );
        } catch (CertificateException e) {
            throw new U2fBadInputException("Malformed attestation certificate", e);
        } catch (IOException e) {
            throw new U2fBadInputException("Truncated registration data", e);
        }
    }

    public boolean verifySignature(byte[] appIdHash, byte[] clientDataHash) {
        byte[] signedBytes = packBytesToSign(appIdHash, clientDataHash, keyHandle, userPublicKey);
        return crypto.verifySignature(attestationCertificate, signedBytes, signature);
    }

    public static byte[] packBytesToSign(byte[] appIdHash, byte[] clientDataHash, byte[] keyHandle, byte[] userPublicKey) {
        ByteArrayDataOutput encoded = ByteStreams.newDataOutput();
        encoded.write(REGISTRATION_SIGNED_RESERVED_BYTE_VALUE);
        encoded.write(appIdHash);
        encoded.write(clientDataHash);
        encoded.write(keyHandle);
        encoded.write(userPublicKey);
        return encoded.toByteArray();
    }

}
