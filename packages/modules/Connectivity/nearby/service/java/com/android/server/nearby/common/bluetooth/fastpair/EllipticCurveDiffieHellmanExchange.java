/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.google.common.primitives.Bytes.concat;

import androidx.annotation.Nullable;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;

/**
 * Helper for generating keys based off of the Elliptic-Curve Diffie-Hellman algorithm (ECDH).
 */
public final class EllipticCurveDiffieHellmanExchange {

    public static final int PUBLIC_KEY_LENGTH = 64;
    static final int PRIVATE_KEY_LENGTH = 32;

    private static final String[] PROVIDERS = {"GmsCore_OpenSSL", "AndroidOpenSSL", "SC", "BC"};

    private static final String EC_ALGORITHM = "EC";

    /**
     * Also known as prime256v1 or NIST P-256.
     */
    private static final ECGenParameterSpec EC_GEN_PARAMS = new ECGenParameterSpec("secp256r1");

    @Nullable
    private final ECPublicKey mPublicKey;
    private final ECPrivateKey mPrivateKey;

    /**
     * Creates a new EllipticCurveDiffieHellmanExchange object.
     */
    public static EllipticCurveDiffieHellmanExchange create() throws GeneralSecurityException {
        KeyPair keyPair = generateKeyPair();
        return new EllipticCurveDiffieHellmanExchange(
                (ECPublicKey) keyPair.getPublic(), (ECPrivateKey) keyPair.getPrivate());
    }

    /**
     * Creates a new EllipticCurveDiffieHellmanExchange object.
     */
    public static EllipticCurveDiffieHellmanExchange create(byte[] privateKey)
            throws GeneralSecurityException {
        ECPrivateKey ecPrivateKey = (ECPrivateKey) generatePrivateKey(privateKey);
        return new EllipticCurveDiffieHellmanExchange(/*publicKey=*/ null, ecPrivateKey);
    }

    private EllipticCurveDiffieHellmanExchange(
            @Nullable ECPublicKey publicKey, ECPrivateKey privateKey) {
        this.mPublicKey = publicKey;
        this.mPrivateKey = privateKey;
    }

    /**
     * @param otherPublicKey Another party's public key. See {@link #getPublicKey()} for format.
     * @return The shared secret. Given our public key (and its private key), the other party can
     * generate the same secret. This is a key meant for symmetric encryption.
     */
    public byte[] generateSecret(byte[] otherPublicKey) throws GeneralSecurityException {
        KeyAgreement agreement = keyAgreement();
        agreement.init(mPrivateKey);
        agreement.doPhase(generatePublicKey(otherPublicKey), /*lastPhase=*/ true);
        byte[] secret = agreement.generateSecret();
        // Headsets only support AES with 128-bit keys. So, hash the secret so that the entropy is
        // high and then take only the first 128-bits.
        secret = MessageDigest.getInstance("SHA-256").digest(secret);
        return Arrays.copyOf(secret, 16);
    }

    /**
     * Returns a public point W on the NIST P-256 elliptic curve. First 32 bytes are the X
     * coordinate, next 32 bytes are the Y coordinate. Each coordinate is an unsigned big-endian
     * integer.
     */
    public @Nullable byte[] getPublicKey() {
        if (mPublicKey == null) {
            return null;
        }
        ECPoint w = mPublicKey.getW();
        // See getPrivateKey for why we're resizing.
        byte[] x = resizeWithLeadingZeros(w.getAffineX().toByteArray(), 32);
        byte[] y = resizeWithLeadingZeros(w.getAffineY().toByteArray(), 32);
        return concat(x, y);
    }

    /**
     * Returns a private value S, an unsigned big-endian integer.
     */
    public byte[] getPrivateKey() {
        // Note that BigInteger.toByteArray() returns a signed representation, so it will add an
        // extra zero byte to the front if the first bit is 1.
        // We must remove that leading zero (we know the number is unsigned). We must also add
        // leading zeros if the number is too small.
        return resizeWithLeadingZeros(mPrivateKey.getS().toByteArray(), 32);
    }

    /**
     * Removes or adds leading zeros until we have an array of size {@code n}.
     */
    private static byte[] resizeWithLeadingZeros(byte[] x, int n) {
        if (n < x.length) {
            int start = x.length - n;
            for (int i = 0; i < start; i++) {
                if (x[i] != 0) {
                    throw new IllegalArgumentException(
                            "More than " + n + " non-zero bytes in " + Arrays.toString(x));
                }
            }
            return Arrays.copyOfRange(x, start, x.length);
        }
        return concat(new byte[n - x.length], x);
    }

    /**
     * @param publicKey See {@link #getPublicKey()} for format.
     */
    private static PublicKey generatePublicKey(byte[] publicKey) throws GeneralSecurityException {
        if (publicKey.length != PUBLIC_KEY_LENGTH) {
            throw new GeneralSecurityException("Public key length incorrect: " + publicKey.length);
        }
        byte[] x = Arrays.copyOf(publicKey, publicKey.length / 2);
        byte[] y = Arrays.copyOfRange(publicKey, publicKey.length / 2, publicKey.length);
        return keyFactory()
                .generatePublic(
                        new ECPublicKeySpec(
                                new ECPoint(new BigInteger(/*signum=*/ 1, x),
                                        new BigInteger(/*signum=*/ 1, y)),
                                ecParameterSpec()));
    }

    /**
     * @param privateKey See {@link #getPrivateKey()} for format.
     */
    private static PrivateKey generatePrivateKey(byte[] privateKey)
            throws GeneralSecurityException {
        if (privateKey.length != PRIVATE_KEY_LENGTH) {
            throw new GeneralSecurityException("Private key length incorrect: "
                    + privateKey.length);
        }
        return keyFactory()
                .generatePrivate(
                        new ECPrivateKeySpec(new BigInteger(/*signum=*/ 1, privateKey),
                                ecParameterSpec()));
    }

    private static ECParameterSpec ecParameterSpec() throws GeneralSecurityException {
        // This seems to be the simplest way to get the curve's ECParameterSpec. Verified that it's
        // the same whether you get it from the public or private key, and that it's the same as the
        // raw params in SecAggEcUtil.getNistP256Params().
        return ((ECPublicKey) generateKeyPair().getPublic()).getParams();
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = findProvider(p -> KeyPairGenerator.getInstance(EC_ALGORITHM,
                p));
        generator.initialize(EC_GEN_PARAMS);
        return generator.generateKeyPair();
    }

    private static KeyAgreement keyAgreement() throws NoSuchProviderException {
        return findProvider(p -> KeyAgreement.getInstance("ECDH", p));
    }

    private static KeyFactory keyFactory() throws NoSuchProviderException {
        return findProvider(p -> KeyFactory.getInstance(EC_ALGORITHM, p));
    }

    private interface ProviderConsumer<T> {

        T tryProvider(String provider) throws NoSuchAlgorithmException, NoSuchProviderException;
    }

    private static <T> T findProvider(ProviderConsumer<T> providerConsumer)
            throws NoSuchProviderException {
        for (String provider : PROVIDERS) {
            try {
                return providerConsumer.tryProvider(provider);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                // No-op
            }
        }
        throw new NoSuchProviderException();
    }
}
