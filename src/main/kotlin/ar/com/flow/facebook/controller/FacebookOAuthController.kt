package ar.com.flow.facebook.controller

import ar.com.flow.facebook.config.FacebookConfig
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Controller
class FacebookOAuthController(
    private val facebookConfig: FacebookConfig
) {

    private val webClient = WebClient.builder().build()

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("facebookAppId", facebookConfig.app.id)
        return "index"
    }

    @GetMapping("/oauth/authorize")
    @ResponseBody
    fun getAuthorizationUrl(): Map<String, String> {
        val scope = "public_profile,email"
        val state = generateRandomState()

        val authUrl = "https://www.facebook.com/v18.0/dialog/oauth?" +
                "client_id=${facebookConfig.app.id}" +
                "&redirect_uri=${URLEncoder.encode(facebookConfig.redirect.uri, StandardCharsets.UTF_8)}" +
                "&scope=${URLEncoder.encode(scope, StandardCharsets.UTF_8)}" +
                "&state=$state" +
                "&response_type=code"

        return mapOf(
            "authUrl" to authUrl,
            "appId" to facebookConfig.app.id,
            "redirectUri" to facebookConfig.redirect.uri,
            "scope" to scope
        )
    }

    @GetMapping("/oauth/callback")
    fun handleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) state: String?,
        model: Model
    ): String {
        // Always return the callback template for browser requests
        model.addAttribute("isCallback", true)
        model.addAttribute("code", code)
        model.addAttribute("error", error)
        return "callback"
    }

    @GetMapping("/api/oauth/token")
    @ResponseBody
    fun exchangeCodeForTokenApi(
        @RequestParam code: String,
        @RequestParam(required = false) state: String?
    ): Map<String, Any> {
        return try {
            val accessToken = exchangeCodeForToken(code)
            mapOf(
                "success" to true,
                "accessToken" to accessToken,
                "message" to "Successfully obtained Facebook access token"
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error occurred"),
                "message" to "Failed to obtain access token"
            )
        }
    }

    private fun exchangeCodeForToken(code: String): String {
        val tokenUrl = "https://graph.facebook.com/v18.0/oauth/access_token"

        val formData = "client_id=${facebookConfig.app.id}" +
                "&client_secret=${facebookConfig.app.secret}" +
                "&redirect_uri=${URLEncoder.encode(facebookConfig.redirect.uri, StandardCharsets.UTF_8)}" +
                "&code=$code" +
                "&grant_type=authorization_code"

        val response = try {
            webClient.post()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
        } catch (e: Exception) {
            throw RuntimeException("Facebook API error: ${e.message}", e)
        }

        if (response?.containsKey("error") == true) {
            val error = response["error"] as? Map<*, *>
            val errorMessage = error?.get("message") ?: "Unknown Facebook error"
            val errorType = error?.get("type") ?: "Unknown"
            throw RuntimeException("Facebook OAuth error: $errorType - $errorMessage")
        }

        return response?.get("access_token")?.toString()
            ?: throw RuntimeException("No access token in Facebook response: $response")
    }

    private fun generateRandomState(): String {
        return (1..16)
            .map { ('a'..'z').random() }
            .joinToString("")
    }
}
