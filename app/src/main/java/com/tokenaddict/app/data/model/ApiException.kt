package com.tokenaddict.app.data.model

sealed class ApiException(message: String) : RuntimeException(message) {
    class Unauthorized(message: String = "Session expired or invalid") : ApiException(message)
    class Forbidden(message: String = "Access denied") : ApiException(message)
    class RateLimited(message: String = "Rate limited") : ApiException(message)
    class NetworkError(message: String) : ApiException(message)
    class ParseError(message: String) : ApiException(message)
    class ServiceChanged(message: String = "Service response changed") : ApiException(message)
}
