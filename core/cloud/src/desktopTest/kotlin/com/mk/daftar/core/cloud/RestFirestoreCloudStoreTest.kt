package com.mk.daftar.core.cloud

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudProvider
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The real backup service over the REST store, against an in-memory Firestore that answers the same REST calls and
 * enforces the rule that slots are readable only while the account is active.
 */
class RestFirestoreCloudStoreTest {
    private val uid = "uid-1"
    private val account = CloudAccount(uid, CloudProvider.GOOGLE, email = "owner@example.com")

    private fun bytes(seed: Int, size: Int = 300): ByteArray = ByteArray(size) { (it * 7 + seed).toByte() }

    private suspend fun CloudBackupService.put(seed: Int) = assertNotNull(
        upload(uid, assertNotNull(newBackupId()), bytes(seed), CloudSha256.hexDigest(bytes(seed))).data(),
    )

    @Test
    fun uploadsRotateThroughThreeSlotsAndDownloadVerifiedBytes() = runTest {
        val firestore = FakeFirestore()
        val service = CloudBackupService(RestFirestoreCloudStore(firestore.credentials, firestore), fixedRandom())
        assertTrue(assertNotNull(service.listing(uid, account).data()).isEmpty)
        service.ensureControl(uid)

        val versions = (1..4).map { service.put(it) }
        val listing = assertNotNull(service.listing(uid, account).data())
        assertEquals(3, listing.versions.filterNotNull().size)
        assertEquals(CloudAccountState.ACTIVE, listing.state)
        // The oldest was replaced; the newest downloads byte for byte with a server time.
        val newest = listing.versions.filterNotNull().single { it.backupId == versions.last().backupId }
        assertNotNull(newest.createdAtMillis)
        val download = assertNotNull(service.download(uid, newest).data())
        assertContentEquals(bytes(4), download.ciphertext)
        assertEquals(4L, firestore.revision())
    }

    @Test
    fun anAbortedCommitIsRetriedWithFreshReads() = runTest {
        val firestore = FakeFirestore()
        val service = CloudBackupService(RestFirestoreCloudStore(firestore.credentials, firestore), fixedRandom())
        service.ensureControl(uid)
        service.put(1)
        firestore.abortNextCommits = 2
        service.put(2)
        assertEquals(2, assertNotNull(service.listing(uid, account).data()).versions.filterNotNull().size)
    }

    @Test
    fun deletingTheAccountNeverReadsSlotsAfterward() = runTest {
        val firestore = FakeFirestore()
        val service = CloudBackupService(RestFirestoreCloudStore(firestore.credentials, firestore), fixedRandom())
        service.ensureControl(uid)
        service.put(1)
        assertTrue(service.deleteAllBackups(uid) is Outcome.Success)
        val listing = assertNotNull(service.listing(uid, account).data())
        assertEquals(CloudAccountState.DELETING, listing.state)
        assertEquals(0, firestore.deniedReads)
    }

    @Test
    fun networkAndRefusalAreTyped() = runTest {
        val offline = FakeFirestore().apply { offline = true }
        val failure = CloudBackupService(RestFirestoreCloudStore(offline.credentials, offline), fixedRandom())
            .listing(uid, account)
        assertEquals(CloudError.OFFLINE, (failure as Outcome.Failure).error)

        val signedOut =
            CloudBackupService(RestFirestoreCloudStore(FakeFirestore().signedOut, FakeFirestore()), fixedRandom())
        assertTrue(signedOut.listing(uid, account) is Outcome.Failure)
    }

    private fun <T> Outcome<T, CloudError>.data(): T? = (this as? Outcome.Success)?.data
}

/**
 * An in-memory Firestore that answers like the real REST API for an end user: `beginTransaction` is refused (it needs
 * IAM permissions), reads return each document's update time, and commits enforce write preconditions.
 */
private class FakeFirestore : FirestoreTransport {
    private val documents = mutableMapOf<String, JsonObject>()
    private val updateTimes = mutableMapOf<String, String>()
    private var clock = 0L
    var abortNextCommits = 0
    var offline = false
    var deniedReads = 0

    val credentials = object : FirestoreCredentials {
        override val projectId = "demo"

        override suspend fun idToken() = "token"
    }

    val signedOut = object : FirestoreCredentials {
        override val projectId = "demo"

        override suspend fun idToken(): String? = null
    }

    fun revision(): Long = documents.entries.single { !it.key.contains("/backups/") }.value["revision"]!!
        .jsonObject["integerValue"]!!.jsonPrimitive.content.toLong()

    override suspend fun post(url: String, token: String, json: String): FirestoreResponse {
        if (offline) throw IOException("offline")
        val request = Json.parseToJsonElement(json).jsonObject
        return when (url.substringAfterLast(':')) {
            "beginTransaction", "rollback" -> FirestoreResponse(403, PERMISSION_DENIED)

            "batchGet" -> {
                val names = request["documents"]!!.jsonArray.map { it.jsonPrimitive.content }
                if (names.any { it.contains("/backups/") } && !active(names.first())) {
                    deniedReads += 1
                    return FirestoreResponse(403, "{}")
                }
                ok(
                    JsonArray(
                        names.map { name ->
                            val fields = documents[name]
                            if (fields == null) {
                                buildJsonObject { put("missing", name) }
                            } else {
                                buildJsonObject {
                                    put(
                                        "found",
                                        buildJsonObject {
                                            put("name", name)
                                            put("fields", fields)
                                            put("updateTime", updateTimes.getValue(name))
                                        },
                                    )
                                }
                            }
                        },
                    ),
                )
            }

            "commit" -> {
                if (request.containsKey("transaction")) return FirestoreResponse(400, "{}")
                val writes = request["writes"]!!.jsonArray.map { it.jsonObject }
                if (abortNextCommits > 0) {
                    // Another device commits first: every document the attempt read now has a newer update time.
                    abortNextCommits -= 1
                    updateTimes.keys.forEach { updateTimes[it] = next() }
                }
                if (writes.any { !preconditionHolds(it) }) return FirestoreResponse(400, FAILED_PRECONDITION)
                writes.forEach { apply(it) }
                ok(buildJsonObject { })
            }

            else -> FirestoreResponse(404, "{}")
        }
    }

    private fun active(slotName: String): Boolean {
        val control = documents[slotName.substringBefore("/backups/")] ?: return false
        return control["state"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content == "active"
    }

    private fun next(): String = Instant.ofEpochSecond(1_800_000_000L + ++clock).toString()

    private fun preconditionHolds(write: JsonObject): Boolean {
        val precondition = write["currentDocument"]?.jsonObject ?: return true
        val name = write["delete"]?.jsonPrimitive?.content
            ?: write["update"]!!.jsonObject["name"]!!.jsonPrimitive.content
        precondition["exists"]?.let { return (documents[name] != null) == it.jsonPrimitive.content.toBoolean() }
        return updateTimes[name] == precondition["updateTime"]?.jsonPrimitive?.content
    }

    private fun apply(write: JsonObject) {
        write["delete"]?.let {
            documents.remove(it.jsonPrimitive.content)
            updateTimes.remove(it.jsonPrimitive.content)
            return
        }
        val update = write["update"]!!.jsonObject
        val name = update["name"]!!.jsonPrimitive.content
        val fields = update["fields"]!!.jsonObject.toMutableMap()
        write["updateTransforms"]?.jsonArray?.forEach { transform ->
            val path = transform.jsonObject["fieldPath"]!!.jsonPrimitive.content
            fields[path] = buildJsonObject { put("timestampValue", Instant.now().toString()) }
        }
        documents[name] = JsonObject(fields)
        updateTimes[name] = next()
    }

    private fun ok(body: Any) = FirestoreResponse(200, body.toString())

    private companion object {
        const val PERMISSION_DENIED = "{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}"
        const val FAILED_PRECONDITION = "{\"error\":{\"code\":400,\"status\":\"FAILED_PRECONDITION\"}}"
    }
}
