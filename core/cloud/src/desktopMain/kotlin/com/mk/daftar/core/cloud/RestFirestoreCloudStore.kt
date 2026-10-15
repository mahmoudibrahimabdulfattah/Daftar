package com.mk.daftar.core.cloud

import com.mk.daftar.core.account.desktop.DesktopFirebaseSession
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The desktop Firestore transport: the REST API with the signed-in user's ID token, under the same security rules as
 * Android. Like the Firebase SDKs, a transaction is optimistic: it reads the control document and, while the account is
 * active, the three slots; runs the body on those reads; and commits its writes atomically, each guarded by a
 * precondition that the document is unchanged since it was read (its update time, or that it still does not exist).
 * A failed precondition means another device wrote in between, and the attempt is retried with fresh reads. Server-side
 * transactions (`beginTransaction`) are not used: they need IAM permissions an end user's token does not carry.
 */
internal class RestFirestoreCloudStore(
    private val session: FirestoreCredentials?,
    private val transport: FirestoreTransport = HttpFirestoreTransport(),
) : CloudStore {

    override suspend fun <T> runTransaction(uid: String, body: (CloudTransaction) -> T): T {
        val active = session ?: throw CloudStoreException(CloudStoreFailure.UNAVAILABLE)
        val base = "https://firestore.googleapis.com/v1/projects/${active.projectId}/databases/(default)/documents"
        val root = "projects/${active.projectId}/databases/(default)/documents/$USERS/$uid"
        repeat(MAX_ATTEMPTS) {
            val token = try {
                active.idToken()
            } catch (failure: IOException) {
                throw CloudStoreException(CloudStoreFailure.OFFLINE)
            } ?: throw CloudStoreException(CloudStoreFailure.DENIED)
            val api = Api(base, token)
            val reads = read(api, root)
            val writes = mutableListOf<PendingWrite>()
            val result = body(RestCloudTransaction(reads, root, writes))
            // A read-only body (a listing) has nothing to commit.
            if (writes.isEmpty() || api.commit(writes.map { it.guarded(reads) })) return result
        }
        throw CloudStoreException(CloudStoreFailure.UNKNOWN_OUTCOME)
    }

    /** Reads the control document, and the slots only while the account is active, as the rules allow. */
    private suspend fun read(api: Api, root: String): Reads {
        val control = api.get(listOf(root))
        val state = control[root]?.fields?.text("state")
        val slots = if (state == "active") api.get((0 until SLOTS).map { "$root/$BACKUPS/$it" }) else emptyMap()
        return Reads(root, control + slots, slotsRead = state == "active")
    }

    private inner class Api(private val base: String, private val token: String) {
        /** Posts to [path] and returns the JSON answer. Maps HTTP failures to typed store failures. */
        suspend fun call(path: String, payload: JsonElement, committing: Boolean): JsonElement = run {
            val response = try {
                transport.post(base + path, token, payload.toString())
            } catch (failure: IOException) {
                // A commit that lost its answer may or may not have been applied.
                throw CloudStoreException(
                    if (committing) CloudStoreFailure.UNKNOWN_OUTCOME else CloudStoreFailure.OFFLINE,
                )
            }
            when (val status = response.status) {
                200 -> Json.parseToJsonElement(response.body)

                409 -> throw Aborted

                // A failed write precondition: another device changed a document after it was read.
                400 if committing && response.body.contains("FAILED_PRECONDITION") -> throw Aborted

                401, 403 -> throw CloudStoreException(CloudStoreFailure.DENIED)

                429 -> throw CloudStoreException(CloudStoreFailure.QUOTA)

                503, 504 -> throw CloudStoreException(
                    if (committing) CloudStoreFailure.UNKNOWN_OUTCOME else CloudStoreFailure.OFFLINE,
                )

                else -> throw CloudStoreException(
                    if (committing ||
                        status >= 500
                    ) {
                        CloudStoreFailure.UNKNOWN_OUTCOME
                    } else {
                        CloudStoreFailure.DENIED
                    },
                )
            }
        }

        /** Reads [names]. Every name is in the answer: a [Document] when it exists, null when it does not. */
        suspend fun get(names: List<String>): Map<String, Document?> {
            val answer = call(
                ":batchGet",
                buildJsonObject { putJsonArray("documents") { names.forEach { add(JsonPrimitive(it)) } } },
                committing = false,
            )
            val found = answer.jsonArray.mapNotNull { entry ->
                val document = entry.jsonObject["found"]?.jsonObject ?: return@mapNotNull null
                document.plain("name") to Document(
                    fields = document["fields"]?.jsonObject ?: JsonObject(emptyMap()),
                    updateTime = document.plain("updateTime"),
                )
            }.toMap()
            return names.associateWith { found[it] }
        }

        /** Commits [writes] atomically. False when a precondition failed, which the caller retries. */
        suspend fun commit(writes: List<JsonObject>): Boolean = try {
            call(":commit", buildJsonObject { put("writes", JsonArray(writes)) }, committing = true)
            true
        } catch (aborted: Aborted) {
            false
        }
    }

    private object Aborted : Exception() {
        private fun readResolve(): Any = Aborted
    }

    private companion object {
        const val USERS = "users"
        const val BACKUPS = "backups"
        const val SLOTS = 3
        const val MAX_ATTEMPTS = 5
    }
}

/** The project and the signed-in user's token, from the desktop Firebase session. */
internal interface FirestoreCredentials {
    val projectId: String

    /** A valid ID token, or null when signed out. Throws [IOException] when it cannot be refreshed offline. */
    suspend fun idToken(): String?
}

internal fun DesktopFirebaseSession.credentials(): FirestoreCredentials = object : FirestoreCredentials {
    override val projectId: String get() = this@credentials.projectId

    override suspend fun idToken(): String? = this@credentials.idToken()
}

internal class FirestoreResponse(val status: Int, val body: String)

/** One authenticated JSON POST. Throws [IOException] when no answer arrives. */
internal fun interface FirestoreTransport {
    suspend fun post(url: String, token: String, json: String): FirestoreResponse
}

internal class HttpFirestoreTransport : FirestoreTransport {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    override suspend fun post(url: String, token: String, json: String): FirestoreResponse =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI(url))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            FirestoreResponse(response.statusCode(), response.body())
        }
}

private class Document(val fields: JsonObject, val updateTime: String)

/**
 * What one attempt read: every requested name, with null for a document that does not exist. [slotsRead] is false when
 * the account was not active, so the rules would have refused slot reads.
 */
private class Reads(val root: String, val documents: Map<String, Document?>, val slotsRead: Boolean) {
    val control: Document? get() = documents[root]

    fun slot(index: Int): Document? = documents["$root/backups/$index"]
}

/** A write and the document it targets. Its precondition comes from what the attempt read about that document. */
private class PendingWrite(val name: String, val write: JsonObject) {
    fun guarded(reads: Reads): JsonObject {
        if (!reads.documents.containsKey(name)) return write
        val seen = reads.documents[name]
        return JsonObject(
            write + (
                "currentDocument" to buildJsonObject {
                    if (seen == null) put("exists", false) else put("updateTime", seen.updateTime)
                }
                ),
        )
    }
}

/** The body's view of one attempt: reads from the prefetched documents, writes collected for the commit. */
private class RestCloudTransaction(
    private val reads: Reads,
    private val root: String,
    private val writes: MutableList<PendingWrite>,
) : CloudTransaction {
    override fun readControl(): ControlRecord? {
        val fields = reads.control?.fields ?: return null
        return ControlRecord(state = fields.text("state").orEmpty(), revision = fields.long("revision") ?: 0L)
    }

    override fun readSlot(slot: Int): SlotRecord? {
        // The rules refuse slot reads unless the account is active, exactly as the SDK would report.
        if (!reads.slotsRead) throw CloudStoreException(CloudStoreFailure.DENIED)
        val fields = reads.slot(slot)?.fields ?: return null
        return SlotRecord(
            backupId = fields.text("backupId").orEmpty(),
            formatVersion = fields.long("formatVersion") ?: -1L,
            size = fields.long("size") ?: -1L,
            sha256 = fields.text("sha256").orEmpty(),
            createdAtMillis = fields.timestamp("createdAt"),
            ciphertext = fields.bytes("ciphertext"),
        )
    }

    override fun writeControl(record: ControlRecord) {
        writes += set(
            name = root,
            fields = buildJsonObject {
                put("schemaVersion", integer(1))
                put("state", string(record.state))
                put("revision", integer(record.revision))
            },
            serverTime = "updatedAt",
        )
    }

    override fun writeSlot(slot: Int, record: SlotRecord) {
        val ciphertext = checkNotNull(record.ciphertext) { "A backup write needs ciphertext" }
        writes += set(
            name = "$root/backups/$slot",
            fields = buildJsonObject {
                put("backupId", string(record.backupId))
                put("formatVersion", integer(record.formatVersion))
                put("size", integer(record.size))
                put("sha256", string(record.sha256))
                put("ciphertext", buildJsonObject { put("bytesValue", Base64.getEncoder().encodeToString(ciphertext)) })
            },
            serverTime = "createdAt",
        )
    }

    override fun deleteSlot(slot: Int) {
        val name = "$root/backups/$slot"
        writes += PendingWrite(name, buildJsonObject { put("delete", name) })
    }

    /** A full replace of [name] with [fields], with [serverTime] stamped by the server at commit. */
    private fun set(name: String, fields: JsonObject, serverTime: String): PendingWrite = PendingWrite(
        name,
        buildJsonObject {
            putJsonObject("update") {
                put("name", name)
                put("fields", fields)
            }
            put(
                "updateTransforms",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("fieldPath", serverTime)
                            put("setToServerValue", "REQUEST_TIME")
                        },
                    )
                },
            )
        },
    )

    private fun integer(value: Number) = buildJsonObject { put("integerValue", value.toString()) }

    private fun string(value: String) = buildJsonObject { put("stringValue", value) }
}

/** A top-level string of a REST answer (not a Firestore field value). */
private fun JsonElement.plain(key: String): String = jsonObject[key]!!.jsonPrimitive.content

private fun JsonObject.text(field: String): String? =
    this[field]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content

private fun JsonObject.long(field: String): Long? =
    this[field]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()

private fun JsonObject.timestamp(field: String): Long? =
    this[field]?.jsonObject?.get("timestampValue")?.jsonPrimitive?.content
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun JsonObject.bytes(field: String): ByteArray? =
    this[field]?.jsonObject?.get("bytesValue")?.jsonPrimitive?.content
        ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
