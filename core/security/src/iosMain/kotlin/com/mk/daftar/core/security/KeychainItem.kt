@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)

package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * One generic-password Keychain item. It is device-only (`ThisDeviceOnly`), never synchronized, and readable only
 * after the first unlock once a device passcode is set. Writes replace the item in one call.
 */
internal class KeychainItem(private val account: String) {

    fun read(): Outcome<ByteArray?, SecurityError> = memScoped {
        val query = baseQuery()
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        when (status) {
            errSecSuccess -> {
                val data = result.value?.let { asCFData(it) }
                    ?: return@memScoped Outcome.Failure(SecurityError.CorruptState)
                val bytes = data.toByteArray()
                CFRelease(data)
                Outcome.Success(bytes)
            }

            errSecItemNotFound -> Outcome.Success(null)

            else -> Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }

    /** Creates or replaces the item. */
    fun write(bytes: ByteArray): Outcome<Unit, SecurityError> = memScoped {
        val data = cfData(bytes)
        val update = baseQuery()
        CFDictionarySetValue(update, kSecValueData, data)
        val updateStatus = SecItemUpdate(baseQuery(), update)
        CFRelease(update)
        val status = if (updateStatus == errSecItemNotFound) {
            val add = baseQuery()
            CFDictionarySetValue(add, kSecValueData, data)
            CFDictionarySetValue(add, kSecAttrAccessible, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly)
            val addStatus = SecItemAdd(add, null)
            CFRelease(add)
            addStatus
        } else {
            updateStatus
        }
        CFRelease(data)
        if (status == errSecSuccess) Outcome.Success(Unit) else Outcome.Failure(SecurityError.PersistenceFailure)
    }

    /** Removes the item if present. Idempotent. */
    fun delete(): Outcome<Unit, SecurityError> = memScoped {
        val query = baseQuery()
        val status = SecItemDelete(query)
        CFRelease(query)
        if (status == errSecSuccess || status == errSecItemNotFound) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val query =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        val service = cfString(KEYCHAIN_SERVICE)
        CFDictionarySetValue(query, kSecAttrService, service)
        CFRelease(service)
        val accountName = cfString(account)
        CFDictionarySetValue(query, kSecAttrAccount, accountName)
        CFRelease(accountName)
        CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
        return query
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "com.mk.daftar.security"
    }
}

internal fun cfString(value: String): CFStringRef? = CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)

/** Copies [bytes] into a new CFData owned by the caller. The payload must not be empty. */
internal fun cfData(bytes: ByteArray): CFDataRef? {
    require(bytes.isNotEmpty())
    val unsigned = UByteArray(bytes.size) { bytes[it].toUByte() }
    return unsigned.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0), bytes.size.toLong())
    }
}

internal fun asCFData(pointer: COpaquePointer): CFDataRef = pointer.reinterpret()

internal fun CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    val source = CFDataGetBytePtr(this) ?: return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), source, length.convert())
    }
    return out
}
