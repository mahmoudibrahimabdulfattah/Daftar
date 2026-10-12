package com.mk.daftar.core.network

import io.ktor.client.request.HttpRequestData
import kotlin.time.Clock
import kotlin.time.Instant

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal val FETCHED_AT: Instant = Instant.parse("2026-10-12T04:33:50Z")

/** Records every request the mock engine sees, in order. */
internal class RequestLog {
    val urls = mutableListOf<String>()
    val requests = mutableListOf<HttpRequestData>()

    fun record(request: HttpRequestData) {
        requests += request
        urls += request.url.toString()
    }
}
