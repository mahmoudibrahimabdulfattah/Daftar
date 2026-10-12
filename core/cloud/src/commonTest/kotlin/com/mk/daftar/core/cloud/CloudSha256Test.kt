package com.mk.daftar.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class CloudSha256Test {
    @Test
    fun emptyInputMatchesTheStandardDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            CloudSha256.hexDigest(ByteArray(0)),
        )
    }

    @Test
    fun abcMatchesTheStandardDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CloudSha256.hexDigest("abc".encodeToByteArray()),
        )
    }

    @Test
    fun twoBlockMessageMatchesTheStandardDigest() {
        val message = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            CloudSha256.hexDigest(message),
        )
    }

    @Test
    fun digestIsThirtyTwoBytesAndLowercase() {
        val digest = CloudSha256.digest(ByteArray(100) { it.toByte() })
        assertEquals(32, digest.size)
        assertEquals(64, CloudSha256.hex(digest).length)
        assertEquals(CloudSha256.hex(digest), CloudSha256.hex(digest).lowercase())
    }
}
