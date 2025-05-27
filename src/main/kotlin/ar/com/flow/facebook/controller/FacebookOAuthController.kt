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
        
        return mapOf("authUrl" to authUrl)
    }

    @GetMapping("/oauth/callback")
    @ResponseBody
    fun handleCallback(
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
        val tokenUrl = "https://graph.facebook.com/v18.0/oauth/access_token?" +
                "client_id=${facebookConfig.app.id}" +
                "&client_secret=${facebookConfig.app.secret}" +
                "&redirect_uri=${URLEncoder.encode(facebookConfig.redirect.uri, StandardCharsets.UTF_8)}" +
                "&code=$code"

        val response = webClient.get()
            .uri(tokenUrl)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        return response?.get("access_token")?.toString() 
            ?: throw RuntimeException("Failed to get access token from Facebook")
    }

    private fun generateRandomState(): String {
        return (1..16)
            .map { ('a'..'z').random() }
            .joinToString("")
    }
}
