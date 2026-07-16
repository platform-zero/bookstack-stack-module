package org.webservices.testrunner.suites

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import org.webservices.testrunner.framework.*

suspend fun TestRunner.bookStackProductivityTests() = suite("BookStack Productivity Tests") {

suspend fun getBookStackResponse(path: String, attempts: Int = 12, delayMs: Long = 5000): HttpResponse? {
        val suffix = if (path.startsWith("/")) path else "/$path"
        val url = "${env.endpoints.bookstack}$suffix"

        repeat(attempts) { attempt ->
            try {
                return client.getRawResponse(url)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val retryable = msg.contains("Connection refused", ignoreCase = true) ||
                    msg.contains("ConnectException", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)
                if (!retryable || attempt == attempts - 1) {
                    println("      ℹ️  BookStack request failed at $suffix: ${e.message}")
                    return null
                }
                if (attempt == 0) {
                    println("      ℹ️  Waiting for BookStack to become reachable...")
                }
                delay(delayMs)
            }
        }

        return null
    }

test("BookStack web interface loads") {
        val response = getBookStackResponse("/")
            ?: fail("BookStack unavailable after retries while loading web interface")
        requireOkOrRedirectResponse(response, "BookStack web interface")
    }

    test("BookStack API endpoint is accessible") {
        val response = getBookStackResponse("/api/docs")
            ?: fail("BookStack unavailable after retries while checking API docs")
        requireOkOrRedirectResponse(response, "BookStack API docs")
    }

}
