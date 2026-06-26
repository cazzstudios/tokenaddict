package com.tokenaddict.app.data

sealed class ClaudeApiException(message: String) : RuntimeException(message) {
    class Unauthorized(message: String = "Session expired or invalid") : ClaudeApiException(message)
    class Forbidden(message: String = "Access denied") : ClaudeApiException(message)
    class RateLimited(message: String = "Rate limited") : ClaudeApiException(message)
    class NetworkError(message: String) : ClaudeApiException(message)
    class ParseError(message: String) : ClaudeApiException(message)
}
