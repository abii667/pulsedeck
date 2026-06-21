package com.pulsedeck.app

import java.net.URI
import java.util.Locale

private const val YOUTUBE_RESOLVER_PORT = 8787
private val TRUSTED_YOUTUBE_RESOLVER_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
private val YOUTUBE_RESOLVER_ENDPOINTS = listOf(
    "http://127.0.0.1:8787",
    "http://10.0.2.2:8787",
)

internal fun isTrustedYouTubeResolverEndpoint(rawEndpoint: String): Boolean =
    runCatching {
        val uri = URI(rawEndpoint.trim())
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.rawPath.orEmpty()
        uri.scheme == "http" &&
            host in TRUSTED_YOUTUBE_RESOLVER_HOSTS &&
            uri.port == YOUTUBE_RESOLVER_PORT &&
            uri.rawQuery == null &&
            (path.isBlank() || path == "/")
    }.getOrDefault(false)

internal fun youtubeResolverEndpoints(): List<String> =
    YOUTUBE_RESOLVER_ENDPOINTS
        .filter(::isTrustedYouTubeResolverEndpoint)
        .distinct()
