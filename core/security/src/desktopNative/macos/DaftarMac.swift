// The macOS half of Daftar's desktop key protection, called from the JVM through JNA.
//
// Keys are CryptoKit Secure Enclave P-256 key-agreement keys. The enclave returns an opaque blob that only this Mac's
// enclave can use; the private key never leaves it. Sealing is ECIES to the key's public half: an ephemeral P-256 key,
// ECDH, HKDF-SHA256 (salt: the ephemeral public key, info: a fixed label) and AES-256-GCM with the caller's associated
// data. A biometric key additionally requires Touch ID (current enrollment) for every private-key use.
//
// Every buffer returned is allocated here and freed (zeroed first) by daftar_free. Status codes: see below.

import CryptoKit
import Foundation
import LocalAuthentication

private let ok: Int32 = 0
private let unavailable: Int32 = -1
private let failed: Int32 = -2
private let cancelled: Int32 = -3
private let lockedOut: Int32 = -4
private let invalidated: Int32 = -5
private let notEnrolled: Int32 = -6

private let sealInfo = Data("daftar.desktop.envelope.v1".utf8)
private let ephemeralBytes = 65
private let minimumSealed = 65 + 12 + 16

private func output(
    _ data: Data,
    _ buffer: UnsafeMutablePointer<UnsafeMutablePointer<UInt8>?>,
    _ length: UnsafeMutablePointer<Int32>
) {
    let pointer = UnsafeMutablePointer<UInt8>.allocate(capacity: max(data.count, 1))
    data.copyBytes(to: pointer, count: data.count)
    buffer.pointee = pointer
    length.pointee = Int32(data.count)
}

@_cdecl("daftar_free")
public func daftarFree(_ pointer: UnsafeMutablePointer<UInt8>?, _ length: Int32) {
    guard let pointer else { return }
    pointer.initialize(repeating: 0, count: Int(max(length, 1)))
    pointer.deallocate()
}

/// 1 when this Mac has a Secure Enclave (Apple silicon or T2), else 0.
@_cdecl("daftar_se_available")
public func daftarSeAvailable() -> Int32 { SecureEnclave.isAvailable ? 1 : 0 }

/// 1 when Touch ID is enrolled and usable, 0 when the Mac has Touch ID but no enrolled finger, -1 otherwise.
@_cdecl("daftar_biometry_state")
public func daftarBiometryState() -> Int32 {
    let context = LAContext()
    var error: NSError?
    if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) { return 1 }
    if error?.code == LAError.biometryNotEnrolled.rawValue { return 0 }
    return -1
}

/// Creates an enclave key and returns its device-bound blob. With [biometric] != 0 every use requires Touch ID.
@_cdecl("daftar_se_create")
public func daftarSeCreate(
    _ biometric: Int32,
    _ blob: UnsafeMutablePointer<UnsafeMutablePointer<UInt8>?>,
    _ blobLength: UnsafeMutablePointer<Int32>
) -> Int32 {
    guard SecureEnclave.isAvailable else { return unavailable }
    let flags: SecAccessControlCreateFlags =
        biometric != 0 ? [.privateKeyUsage, .biometryCurrentSet] : [.privateKeyUsage]
    guard let access = SecAccessControlCreateWithFlags(
        nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, flags, nil
    ) else { return failed }
    do {
        let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(accessControl: access)
        output(key.dataRepresentation, blob, blobLength)
        return ok
    } catch {
        return biometric != 0 ? notEnrolled : failed
    }
}

/// Seals to the key's public half. Needs no authentication, so it never prompts.
@_cdecl("daftar_se_seal")
public func daftarSeSeal(
    _ blob: UnsafePointer<UInt8>, _ blobLength: Int32,
    _ aad: UnsafePointer<UInt8>?, _ aadLength: Int32,
    _ plain: UnsafePointer<UInt8>?, _ plainLength: Int32,
    _ sealed: UnsafeMutablePointer<UnsafeMutablePointer<UInt8>?>,
    _ sealedLength: UnsafeMutablePointer<Int32>
) -> Int32 {
    do {
        let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(
            dataRepresentation: Data(bytes: blob, count: Int(blobLength))
        )
        let ephemeral = P256.KeyAgreement.PrivateKey()
        let ephemeralPublic = ephemeral.publicKey.x963Representation
        let secret = try ephemeral.sharedSecretFromKeyAgreement(with: key.publicKey)
        let symmetric = secret.hkdfDerivedSymmetricKey(
            using: SHA256.self, salt: ephemeralPublic, sharedInfo: sealInfo, outputByteCount: 32
        )
        let box = try AES.GCM.seal(
            plain.map { Data(bytes: $0, count: Int(plainLength)) } ?? Data(),
            using: symmetric,
            authenticating: aad.map { Data(bytes: $0, count: Int(aadLength)) } ?? Data()
        )
        guard let combined = box.combined else { return failed }
        output(ephemeralPublic + combined, sealed, sealedLength)
        return ok
    } catch {
        return invalidated
    }
}

/// Opens a sealed value. A biometric key shows the system Touch ID sheet with [reason] and waits for the decision.
@_cdecl("daftar_se_open")
public func daftarSeOpen(
    _ blob: UnsafePointer<UInt8>, _ blobLength: Int32,
    _ reason: UnsafePointer<CChar>?,
    _ aad: UnsafePointer<UInt8>?, _ aadLength: Int32,
    _ sealed: UnsafePointer<UInt8>, _ sealedLength: Int32,
    _ plain: UnsafeMutablePointer<UnsafeMutablePointer<UInt8>?>,
    _ plainLength: UnsafeMutablePointer<Int32>
) -> Int32 {
    let data = Data(bytes: sealed, count: Int(sealedLength))
    guard data.count >= minimumSealed else { return failed }
    let context = LAContext()
    context.localizedFallbackTitle = ""
    if let reason { context.localizedReason = String(cString: reason) }
    defer { context.invalidate() }
    let key: SecureEnclave.P256.KeyAgreement.PrivateKey
    do {
        key = try SecureEnclave.P256.KeyAgreement.PrivateKey(
            dataRepresentation: Data(bytes: blob, count: Int(blobLength)),
            authenticationContext: context
        )
    } catch {
        return invalidated
    }
    do {
        let ephemeralPublic = data.prefix(ephemeralBytes)
        let peer = try P256.KeyAgreement.PublicKey(x963Representation: ephemeralPublic)
        let secret = try key.sharedSecretFromKeyAgreement(with: peer)
        let symmetric = secret.hkdfDerivedSymmetricKey(
            using: SHA256.self, salt: ephemeralPublic, sharedInfo: sealInfo, outputByteCount: 32
        )
        let box = try AES.GCM.SealedBox(combined: data.dropFirst(ephemeralBytes))
        let opened = try AES.GCM.open(
            box, using: symmetric,
            authenticating: aad.map { Data(bytes: $0, count: Int(aadLength)) } ?? Data()
        )
        output(opened, plain, plainLength)
        return ok
    } catch let error as LAError {
        switch error.code {
        case .userCancel, .appCancel, .systemCancel, .userFallback: return cancelled
        case .biometryLockout: return lockedOut
        case .biometryNotEnrolled: return notEnrolled
        case .biometryNotAvailable: return unavailable
        default: return failed
        }
    } catch is CryptoKitError {
        // A key whose Touch ID enrollment changed can no longer agree; AES failures mean tampering.
        return failed
    } catch {
        return failed
    }
}
