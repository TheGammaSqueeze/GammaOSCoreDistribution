/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider.crypto;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;

import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.util.Collections;

/** Provides methods for calculating E2EE EIDs and E2E encryption/decryption based on E2EE EIDs. */
public final class E2eeCalculator {

    private static final byte[] TEMP_KEY_PADDING_1 =
            Bytes.toArray(Collections.nCopies(11, (byte) 0xFF));
    private static final byte[] TEMP_KEY_PADDING_2 = new byte[11];
    private static final ECParameterSpec CURVE_SPEC = getCurveSpec();
    private static final BigInteger P = ((ECFieldFp) CURVE_SPEC.getCurve().getField()).getP();
    private static final BigInteger TWO = new BigInteger("2");
    private static final BigInteger THREE = new BigInteger("3");
    private static final int E2EE_EID_IDENTITY_KEY_SIZE = 32;
    private static final int E2EE_EID_SIZE = 20;

    /**
     * Computes the E2EE EID value for the given device clock based time. Note that Eddystone
     * beacons start advertising the new EID at a random time within the window, therefore the
     * currently advertised EID for beacon time <em>t</em> may be either
     * {@code computeE2eeEid(eik, k, t)} or {@code computeE2eeEid(eik, k, t - (1 << k))}.
     *
     * <p>The E2EE EID computation is based on https://goto.google.com/e2ee-eid-computation.
     *
     * @param identityKey        the beacon's 32-byte Eddystone E2EE identity key
     * @param exponent           rotation period exponent as configured on the beacon, must be in
     *                           range the [0,15]
     * @param deviceClockSeconds the value of the beacon's 32-bit seconds time counter (treated as
     *                           an unsigned value)
     * @return E2EE EID value.
     */
    public static ByteString computeE2eeEid(
            ByteString identityKey, int exponent, int deviceClockSeconds) {
        return computePublicKey(computePrivateKey(identityKey, exponent, deviceClockSeconds));
    }

    private static ByteString computePublicKey(BigInteger privateKey) {
        return getXCoordinateBytes(toPoint(privateKey));
    }

    private static BigInteger computePrivateKey(
            ByteString identityKey, int exponent, int deviceClockSeconds) {
        Preconditions.checkArgument(
                Preconditions.checkNotNull(identityKey).size() == E2EE_EID_IDENTITY_KEY_SIZE);
        Preconditions.checkArgument(exponent >= 0 && exponent < 16);

        byte[] exponentByte = new byte[]{(byte) exponent};
        byte[] paddedCounter = Ints.toByteArray((deviceClockSeconds >>> exponent) << exponent);
        byte[] data =
                Bytes.concat(
                        TEMP_KEY_PADDING_1,
                        exponentByte,
                        paddedCounter,
                        TEMP_KEY_PADDING_2,
                        exponentByte,
                        paddedCounter);

        byte[] rTag =
                Crypto.aesEcbNoPaddingEncrypt(identityKey, ByteString.copyFrom(data)).toByteArray();
        return new BigInteger(1, rTag).mod(CURVE_SPEC.getOrder());
    }

    private static ECPoint toPoint(BigInteger privateKey) {
        return multiplyPoint(CURVE_SPEC.getGenerator(), privateKey);
    }

    private static ByteString getXCoordinateBytes(ECPoint point) {
        byte[] unalignedBytes = point.getAffineX().toByteArray();

        // The unalignedBytes may have length < 32 if the leading E2EE EID bytes are zero, or
        // it may be E2EE_EID_SIZE + 1 if the leading bit is 1, in which case the first byte is
        // always zero.
        Verify.verify(
                unalignedBytes.length <= E2EE_EID_SIZE
                        || (unalignedBytes.length == E2EE_EID_SIZE + 1 && unalignedBytes[0] == 0));

        byte[] bytes;
        if (unalignedBytes.length < E2EE_EID_SIZE) {
            bytes = new byte[E2EE_EID_SIZE];
            System.arraycopy(
                    unalignedBytes, 0, bytes, bytes.length - unalignedBytes.length,
                    unalignedBytes.length);
        } else if (unalignedBytes.length == E2EE_EID_SIZE + 1) {
            bytes = new byte[E2EE_EID_SIZE];
            System.arraycopy(unalignedBytes, 1, bytes, 0, E2EE_EID_SIZE);
        } else { // unalignedBytes.length ==  GattE2EE_EID_SIZE
            bytes = unalignedBytes;
        }
        return ByteString.copyFrom(bytes);
    }

    /** Returns a secp160r1 curve spec. */
    private static ECParameterSpec getCurveSpec() {
        final BigInteger p = new BigInteger("ffffffffffffffffffffffffffffffff7fffffff", 16);
        final BigInteger n = new BigInteger("0100000000000000000001f4c8f927aed3ca752257", 16);
        final BigInteger a = new BigInteger("ffffffffffffffffffffffffffffffff7ffffffc", 16);
        final BigInteger b = new BigInteger("1c97befc54bd7a8b65acf89f81d4d4adc565fa45", 16);
        final BigInteger gx = new BigInteger("4a96b5688ef573284664698968c38bb913cbfc82", 16);
        final BigInteger gy = new BigInteger("23a628553168947d59dcc912042351377ac5fb32", 16);
        final int h = 1;
        ECFieldFp fp = new ECFieldFp(p);
        EllipticCurve spec = new EllipticCurve(fp, a, b);
        ECPoint g = new ECPoint(gx, gy);
        return new ECParameterSpec(spec, g, n, h);
    }

    /** Returns the scalar multiplication result of k*p in Fp. */
    private static ECPoint multiplyPoint(ECPoint p, BigInteger k) {
        ECPoint r = ECPoint.POINT_INFINITY;
        ECPoint s = p;
        BigInteger kModP = k.mod(P);
        int length = kModP.bitLength();
        for (int i = 0; i <= length - 1; i++) {
            if (kModP.mod(TWO).byteValue() == 1) {
                r = addPoint(r, s);
            }
            s = doublePoint(s);
            kModP = kModP.divide(TWO);
        }
        return r;
    }

    /** Returns the point addition r+s in Fp. */
    private static ECPoint addPoint(ECPoint r, ECPoint s) {
        if (r.equals(s)) {
            return doublePoint(r);
        } else if (r.equals(ECPoint.POINT_INFINITY)) {
            return s;
        } else if (s.equals(ECPoint.POINT_INFINITY)) {
            return r;
        }
        BigInteger slope =
                r.getAffineY()
                        .subtract(s.getAffineY())
                        .multiply(r.getAffineX().subtract(s.getAffineX()).modInverse(P))
                        .mod(P);
        BigInteger x =
                slope.modPow(TWO, P).subtract(r.getAffineX()).subtract(s.getAffineX()).mod(P);
        BigInteger y = s.getAffineY().negate().mod(P);
        y = y.add(slope.multiply(s.getAffineX().subtract(x))).mod(P);
        return new ECPoint(x, y);
    }

    /** Returns the point doubling 2*r in Fp. */
    private static ECPoint doublePoint(ECPoint r) {
        if (r.equals(ECPoint.POINT_INFINITY)) {
            return r;
        }
        BigInteger slope = r.getAffineX().pow(2).multiply(THREE);
        slope = slope.add(CURVE_SPEC.getCurve().getA());
        slope = slope.multiply(r.getAffineY().multiply(TWO).modInverse(P));
        BigInteger x = slope.pow(2).subtract(r.getAffineX().multiply(TWO)).mod(P);
        BigInteger y =
                r.getAffineY().negate().add(slope.multiply(r.getAffineX().subtract(x))).mod(P);
        return new ECPoint(x, y);
    }

    private E2eeCalculator() {
    }
}
