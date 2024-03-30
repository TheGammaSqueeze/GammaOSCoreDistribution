/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "CertUtils.h"

#include <android-base/logging.h>
#include <android-base/result.h>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/pkcs7.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>

#include <optional>
#include <vector>

#include "KeyConstants.h"

// Common properties for all of our certificates.
constexpr int kCertLifetimeSeconds = 10 * 365 * 24 * 60 * 60;
const char* const kIssuerCountry = "US";
const char* const kIssuerOrg = "Android";

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;

static Result<bssl::UniquePtr<X509>> loadX509(const std::string& path) {
    X509* rawCert;
    auto f = fopen(path.c_str(), "re");
    if (f == nullptr) {
        return Error() << "Failed to open " << path;
    }
    if (!d2i_X509_fp(f, &rawCert)) {
        fclose(f);
        return Error() << "Unable to decode x509 cert at " << path;
    }
    bssl::UniquePtr<X509> cert(rawCert);

    fclose(f);
    return cert;
}

static X509V3_CTX makeContext(X509* issuer, X509* subject) {
    X509V3_CTX context = {};
    X509V3_set_ctx(&context, issuer, subject, nullptr, nullptr, 0);
    return context;
}

static bool add_ext(X509V3_CTX* context, X509* cert, int nid, const char* value) {
    bssl::UniquePtr<X509_EXTENSION> ex(X509V3_EXT_nconf_nid(nullptr, context, nid, value));
    if (!ex) {
        return false;
    }

    X509_add_ext(cert, ex.get(), -1);
    return true;
}

static void addNameEntry(X509_NAME* name, const char* field, const char* value) {
    X509_NAME_add_entry_by_txt(name, field, MBSTRING_ASC,
                               reinterpret_cast<const unsigned char*>(value), -1, -1, 0);
}

static Result<bssl::UniquePtr<RSA>> getRsaFromModulus(const std::vector<uint8_t>& publicKey) {
    bssl::UniquePtr<BIGNUM> n(BN_new());
    bssl::UniquePtr<BIGNUM> e(BN_new());
    bssl::UniquePtr<RSA> rsaPubkey(RSA_new());
    if (!n || !e || !rsaPubkey || !BN_bin2bn(publicKey.data(), publicKey.size(), n.get()) ||
        !BN_set_word(e.get(), kRsaKeyExponent) ||
        !RSA_set0_key(rsaPubkey.get(), n.get(), e.get(), /*d=*/nullptr)) {
        return Error() << "Failed to create RSA key";
    }
    // RSA_set0_key takes ownership of |n| and |e| on success.
    (void)n.release();
    (void)e.release();

    return rsaPubkey;
}

static Result<bssl::UniquePtr<RSA>>
getRsaFromRsaPublicKey(const std::vector<uint8_t>& rsaPublicKey) {
    auto derBytes = rsaPublicKey.data();
    bssl::UniquePtr<RSA> rsaKey(d2i_RSAPublicKey(nullptr, &derBytes, rsaPublicKey.size()));
    if (rsaKey.get() == nullptr) {
        return Error() << "Failed to parse RsaPublicKey";
    }
    if (derBytes != rsaPublicKey.data() + rsaPublicKey.size()) {
        return Error() << "Key has unexpected trailing data";
    }

    return rsaKey;
}

static Result<bssl::UniquePtr<EVP_PKEY>> modulusToRsaPkey(const std::vector<uint8_t>& publicKey) {
    // "publicKey" corresponds to the raw public key bytes - need to create
    // a new RSA key with the correct exponent.
    auto rsaPubkey = getRsaFromModulus(publicKey);
    if (!rsaPubkey.ok()) {
        return rsaPubkey.error();
    }

    bssl::UniquePtr<EVP_PKEY> public_key(EVP_PKEY_new());
    if (!EVP_PKEY_assign_RSA(public_key.get(), rsaPubkey->release())) {
        return Error() << "Failed to assign key";
    }
    return public_key;
}

static Result<bssl::UniquePtr<EVP_PKEY>>
rsaPublicKeyToRsaPkey(const std::vector<uint8_t>& rsaPublicKey) {
    // rsaPublicKey contains both modulus and exponent, DER-encoded.
    auto rsaKey = getRsaFromRsaPublicKey(rsaPublicKey);
    if (!rsaKey.ok()) {
        return rsaKey.error();
    }

    bssl::UniquePtr<EVP_PKEY> public_key(EVP_PKEY_new());
    if (!EVP_PKEY_assign_RSA(public_key.get(), rsaKey->release())) {
        return Error() << "Failed to assign key";
    }
    return public_key;
}

Result<void> verifySignature(const std::string& message, const std::string& signature,
                             const std::vector<uint8_t>& publicKey) {
    auto rsaKey = getRsaFromModulus(publicKey);
    if (!rsaKey.ok()) {
        return rsaKey.error();
    }
    uint8_t hashBuf[SHA256_DIGEST_LENGTH];
    SHA256(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(message.c_str())),
           message.length(), hashBuf);

    bool success = RSA_verify(NID_sha256, hashBuf, sizeof(hashBuf),
                              (const uint8_t*)signature.c_str(), signature.length(), rsaKey->get());

    if (!success) {
        return Error() << "Failed to verify signature";
    }
    return {};
}

Result<void> verifyRsaPublicKeySignature(const std::string& message, const std::string& signature,
                                         const std::vector<uint8_t>& rsaPublicKey) {
    auto rsaKey = getRsaFromRsaPublicKey(rsaPublicKey);
    if (!rsaKey.ok()) {
        return rsaKey.error();
    }

    uint8_t hashBuf[SHA256_DIGEST_LENGTH];
    SHA256(reinterpret_cast<const uint8_t*>(message.data()), message.size(), hashBuf);

    bool success = RSA_verify(NID_sha256, hashBuf, sizeof(hashBuf),
                              reinterpret_cast<const uint8_t*>(signature.data()), signature.size(),
                              rsaKey->get());
    if (!success) {
        return Error() << "Failed to verify signature";
    }
    return {};
}

static Result<void> createCertificate(
    const CertSubject& subject, EVP_PKEY* publicKey,
    const std::function<android::base::Result<std::string>(const std::string&)>& signFunction,
    const std::optional<std::string>& issuerCertPath, const std::string& path) {

    // If an issuer cert is specified, we are signing someone else's key.
    // Otherwise we are signing our key - a self-signed certificate.
    bool selfSigned = !issuerCertPath;

    bssl::UniquePtr<X509> x509(X509_new());
    if (!x509) {
        return Error() << "Unable to allocate x509 container";
    }
    X509_set_version(x509.get(), 2);
    X509_gmtime_adj(X509_get_notBefore(x509.get()), 0);
    X509_gmtime_adj(X509_get_notAfter(x509.get()), kCertLifetimeSeconds);
    ASN1_INTEGER_set(X509_get_serialNumber(x509.get()), subject.serialNumber);

    bssl::UniquePtr<X509_ALGOR> algor(X509_ALGOR_new());
    if (!algor ||
        !X509_ALGOR_set0(algor.get(), OBJ_nid2obj(NID_sha256WithRSAEncryption), V_ASN1_NULL,
                         NULL) ||
        !X509_set1_signature_algo(x509.get(), algor.get())) {
        return Error() << "Unable to set x509 signature algorithm";
    }

    if (!X509_set_pubkey(x509.get(), publicKey)) {
        return Error() << "Unable to set x509 public key";
    }

    X509_NAME* subjectName = X509_get_subject_name(x509.get());
    if (!subjectName) {
        return Error() << "Unable to get x509 subject name";
    }
    addNameEntry(subjectName, "C", kIssuerCountry);
    addNameEntry(subjectName, "O", kIssuerOrg);
    addNameEntry(subjectName, "CN", subject.commonName);

    if (selfSigned) {
        if (!X509_set_issuer_name(x509.get(), subjectName)) {
            return Error() << "Unable to set x509 issuer name";
        }
    } else {
        X509_NAME* issuerName = X509_get_issuer_name(x509.get());
        if (!issuerName) {
            return Error() << "Unable to get x509 issuer name";
        }
        addNameEntry(issuerName, "C", kIssuerCountry);
        addNameEntry(issuerName, "O", kIssuerOrg);
        addNameEntry(issuerName, "CN", kRootSubject.commonName);
    }

    // Beware: context contains a pointer to issuerCert, so we need to keep it alive.
    bssl::UniquePtr<X509> issuerCert;
    X509V3_CTX context;

    if (selfSigned) {
        context = makeContext(x509.get(), x509.get());
    } else {
        auto certStatus = loadX509(*issuerCertPath);
        if (!certStatus.ok()) {
            return Error() << "Unable to load issuer cert: " << certStatus.error();
        }
        issuerCert = std::move(certStatus.value());
        context = makeContext(issuerCert.get(), x509.get());
    }

    // If it's a self-signed cert we use it for signing certs, otherwise only for signing data.
    const char* basicConstraints = selfSigned ? "CA:TRUE" : "CA:FALSE";
    const char* keyUsage =
        selfSigned ? "critical,keyCertSign,cRLSign,digitalSignature" : "critical,digitalSignature";

    add_ext(&context, x509.get(), NID_basic_constraints, basicConstraints);
    add_ext(&context, x509.get(), NID_key_usage, keyUsage);
    add_ext(&context, x509.get(), NID_subject_key_identifier, "hash");
    add_ext(&context, x509.get(), NID_authority_key_identifier, "keyid:always");

    // Get the data to be signed
    unsigned char* to_be_signed_buf(nullptr);
    size_t to_be_signed_length = i2d_re_X509_tbs(x509.get(), &to_be_signed_buf);

    auto signed_data = signFunction(
        std::string(reinterpret_cast<const char*>(to_be_signed_buf), to_be_signed_length));
    if (!signed_data.ok()) {
        return signed_data.error();
    }

    if (!X509_set1_signature_value(x509.get(),
                                   reinterpret_cast<const uint8_t*>(signed_data->data()),
                                   signed_data->size())) {
        return Error() << "Unable to set x509 signature";
    }

    auto f = fopen(path.c_str(), "wbe");
    if (f == nullptr) {
        return ErrnoError() << "Failed to open " << path;
    }
    i2d_X509_fp(f, x509.get());
    if (fclose(f) != 0) {
        return ErrnoError() << "Failed to close " << path;
    }

    return {};
}

Result<void> createSelfSignedCertificate(
    const std::vector<uint8_t>& publicKey,
    const std::function<Result<std::string>(const std::string&)>& signFunction,
    const std::string& path) {
    auto rsa_pkey = modulusToRsaPkey(publicKey);
    if (!rsa_pkey.ok()) {
        return rsa_pkey.error();
    }

    return createCertificate(kRootSubject, rsa_pkey.value().get(), signFunction, {}, path);
}

android::base::Result<void> createLeafCertificate(
    const CertSubject& subject, const std::vector<uint8_t>& rsaPublicKey,
    const std::function<android::base::Result<std::string>(const std::string&)>& signFunction,
    const std::string& issuerCertPath, const std::string& path) {
    auto rsa_pkey = rsaPublicKeyToRsaPkey(rsaPublicKey);
    if (!rsa_pkey.ok()) {
        return rsa_pkey.error();
    }

    return createCertificate(subject, rsa_pkey.value().get(), signFunction, issuerCertPath, path);
}

Result<std::vector<uint8_t>> extractPublicKey(EVP_PKEY* pkey) {
    if (pkey == nullptr) {
        return Error() << "Failed to extract public key from x509 cert";
    }

    if (EVP_PKEY_id(pkey) != EVP_PKEY_RSA) {
        return Error() << "The public key is not an RSA key";
    }

    RSA* rsa = EVP_PKEY_get0_RSA(pkey);
    auto num_bytes = BN_num_bytes(RSA_get0_n(rsa));
    std::vector<uint8_t> pubKey(num_bytes);
    int res = BN_bn2bin(RSA_get0_n(rsa), pubKey.data());

    if (!res) {
        return Error() << "Failed to convert public key to bytes";
    }

    return pubKey;
}

Result<std::vector<uint8_t>>
extractPublicKeyFromSubjectPublicKeyInfo(const std::vector<uint8_t>& keyData) {
    auto keyDataBytes = keyData.data();
    bssl::UniquePtr<EVP_PKEY> public_key(d2i_PUBKEY(nullptr, &keyDataBytes, keyData.size()));

    return extractPublicKey(public_key.get());
}

Result<std::vector<uint8_t>> extractPublicKeyFromX509(const std::vector<uint8_t>& derCert) {
    auto derCertBytes = derCert.data();
    bssl::UniquePtr<X509> decoded_cert(d2i_X509(nullptr, &derCertBytes, derCert.size()));
    if (decoded_cert.get() == nullptr) {
        return Error() << "Failed to decode X509 certificate.";
    }
    bssl::UniquePtr<EVP_PKEY> decoded_pkey(X509_get_pubkey(decoded_cert.get()));

    return extractPublicKey(decoded_pkey.get());
}

Result<std::vector<uint8_t>> extractPublicKeyFromX509(const std::string& path) {
    auto cert = loadX509(path);
    if (!cert.ok()) {
        return cert.error();
    }
    return extractPublicKey(X509_get_pubkey(cert.value().get()));
}

static Result<std::vector<uint8_t>> extractRsaPublicKey(EVP_PKEY* pkey) {
    RSA* rsa = EVP_PKEY_get0_RSA(pkey);
    if (rsa == nullptr) {
        return Error() << "The public key is not an RSA key";
    }

    uint8_t* out = nullptr;
    int size = i2d_RSAPublicKey(rsa, &out);
    if (size < 0 || !out) {
        return Error() << "Failed to convert to RSAPublicKey";
    }

    bssl::UniquePtr<uint8_t> buffer(out);
    std::vector<uint8_t> result(out, out + size);
    return result;
}

Result<CertInfo> verifyAndExtractCertInfoFromX509(const std::string& path,
                                                  const std::vector<uint8_t>& publicKey) {
    auto public_key = modulusToRsaPkey(publicKey);
    if (!public_key.ok()) {
        return public_key.error();
    }

    auto cert = loadX509(path);
    if (!cert.ok()) {
        return cert.error();
    }
    X509* x509 = cert.value().get();

    // Make sure we signed it.
    if (X509_verify(x509, public_key.value().get()) != 1) {
        return Error() << "Failed to verify certificate.";
    }

    bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(x509));
    auto subject_key = extractRsaPublicKey(pkey.get());
    if (!subject_key.ok()) {
        return subject_key.error();
    }

    // The pointers here are all owned by x509, and each function handles an
    // error return from the previous call correctly.
    X509_NAME* name = X509_get_subject_name(x509);
    int index = X509_NAME_get_index_by_NID(name, NID_commonName, -1);
    X509_NAME_ENTRY* entry = X509_NAME_get_entry(name, index);
    ASN1_STRING* asn1cn = X509_NAME_ENTRY_get_data(entry);
    unsigned char* utf8cn;
    int length = ASN1_STRING_to_UTF8(&utf8cn, asn1cn);
    if (length < 0) {
        return Error() << "Failed to read subject CN";
    }

    bssl::UniquePtr<unsigned char> utf8owner(utf8cn);
    std::string cn(reinterpret_cast<char*>(utf8cn), static_cast<size_t>(length));

    CertInfo cert_info{std::move(cn), std::move(subject_key.value())};
    return cert_info;
}

Result<std::vector<uint8_t>> createPkcs7(const std::vector<uint8_t>& signed_digest,
                                         const CertSubject& signer) {
    CBB out, outer_seq, wrapped_seq, seq, digest_algos_set, digest_algo, null;
    CBB content_info, issuer_and_serial, signer_infos, signer_info, sign_algo, signature;
    uint8_t *pkcs7_data, *name_der;
    size_t pkcs7_data_len, name_der_len;
    BIGNUM* serial = BN_new();
    int sig_nid = NID_rsaEncryption;

    X509_NAME* issuer_name = X509_NAME_new();
    if (!issuer_name) {
        return Error() << "Unable to create x509 subject name";
    }
    X509_NAME_add_entry_by_txt(issuer_name, "C", MBSTRING_ASC,
                               reinterpret_cast<const unsigned char*>(kIssuerCountry), -1, -1, 0);
    X509_NAME_add_entry_by_txt(issuer_name, "O", MBSTRING_ASC,
                               reinterpret_cast<const unsigned char*>(kIssuerOrg), -1, -1, 0);
    X509_NAME_add_entry_by_txt(issuer_name, "CN", MBSTRING_ASC,
                               reinterpret_cast<const unsigned char*>(kRootSubject.commonName), -1,
                               -1, 0);

    BN_set_word(serial, signer.serialNumber);
    name_der_len = i2d_X509_NAME(issuer_name, &name_der);
    CBB_init(&out, 1024);

    if (!CBB_add_asn1(&out, &outer_seq, CBS_ASN1_SEQUENCE) ||
        !OBJ_nid2cbb(&outer_seq, NID_pkcs7_signed) ||
        !CBB_add_asn1(&outer_seq, &wrapped_seq,
                      CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
        // See https://tools.ietf.org/html/rfc2315#section-9.1
        !CBB_add_asn1(&wrapped_seq, &seq, CBS_ASN1_SEQUENCE) ||
        !CBB_add_asn1_uint64(&seq, 1 /* version */) ||
        !CBB_add_asn1(&seq, &digest_algos_set, CBS_ASN1_SET) ||
        !CBB_add_asn1(&digest_algos_set, &digest_algo, CBS_ASN1_SEQUENCE) ||
        !OBJ_nid2cbb(&digest_algo, NID_sha256) ||
        !CBB_add_asn1(&digest_algo, &null, CBS_ASN1_NULL) ||
        !CBB_add_asn1(&seq, &content_info, CBS_ASN1_SEQUENCE) ||
        !OBJ_nid2cbb(&content_info, NID_pkcs7_data) ||
        !CBB_add_asn1(&seq, &signer_infos, CBS_ASN1_SET) ||
        !CBB_add_asn1(&signer_infos, &signer_info, CBS_ASN1_SEQUENCE) ||
        !CBB_add_asn1_uint64(&signer_info, 1 /* version */) ||
        !CBB_add_asn1(&signer_info, &issuer_and_serial, CBS_ASN1_SEQUENCE) ||
        !CBB_add_bytes(&issuer_and_serial, name_der, name_der_len) ||
        !BN_marshal_asn1(&issuer_and_serial, serial) ||
        !CBB_add_asn1(&signer_info, &digest_algo, CBS_ASN1_SEQUENCE) ||
        !OBJ_nid2cbb(&digest_algo, NID_sha256) ||
        !CBB_add_asn1(&digest_algo, &null, CBS_ASN1_NULL) ||
        !CBB_add_asn1(&signer_info, &sign_algo, CBS_ASN1_SEQUENCE) ||
        !OBJ_nid2cbb(&sign_algo, sig_nid) || !CBB_add_asn1(&sign_algo, &null, CBS_ASN1_NULL) ||
        !CBB_add_asn1(&signer_info, &signature, CBS_ASN1_OCTETSTRING) ||
        !CBB_add_bytes(&signature, signed_digest.data(), signed_digest.size()) ||
        !CBB_finish(&out, &pkcs7_data, &pkcs7_data_len)) {
        return Error() << "Failed to create PKCS7 certificate.";
    }

    return std::vector<uint8_t>(&pkcs7_data[0], &pkcs7_data[pkcs7_data_len]);
}
