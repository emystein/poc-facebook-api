package ar.com.flow.facebook.controller

import ar.com.flow.facebook.config.FacebookConfig
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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

    // Simple in-memory token storage (in production, use database or session)
    private var storedAccessToken: String? = null

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
            // Store the token for later use
            storedAccessToken = accessToken
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

    @GetMapping("/friends")
    fun friendsPage(model: Model): String {
        model.addAttribute("hasToken", storedAccessToken != null)
        model.addAttribute("facebookAppId", facebookConfig.app.id)
        return "friends"
    }

    @GetMapping("/posts")
    fun postsPage(model: Model): String {
        model.addAttribute("hasToken", storedAccessToken != null)
        model.addAttribute("facebookAppId", facebookConfig.app.id)
        return "posts"
    }

    @GetMapping("/post")
    fun createPostPage(model: Model): String {
        model.addAttribute("hasToken", storedAccessToken != null)
        model.addAttribute("facebookAppId", facebookConfig.app.id)
        return "post"
    }

    @GetMapping("/api/friends")
    @ResponseBody
    fun getFriends(): Map<String, Any> {
        return if (storedAccessToken != null) {
            try {
                val friends = fetchFacebookFriends(storedAccessToken!!)
                mapOf(
                    "success" to true,
                    "friends" to friends,
                    "message" to "Successfully retrieved friends list"
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error occurred"),
                    "message" to "Failed to retrieve friends list"
                )
            }
        } else {
            mapOf(
                "success" to false,
                "error" to "No access token available",
                "message" to "Please authenticate with Facebook first"
            )
        }
    }

    @GetMapping("/api/posts")
    @ResponseBody
    fun getPosts(): Map<String, Any> {
        return if (storedAccessToken != null) {
            try {
                val posts = fetchFacebookPosts(storedAccessToken!!)
                mapOf(
                    "success" to true,
                    "posts" to posts,
                    "message" to "Successfully retrieved posts list"
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error occurred"),
                    "message" to "Failed to retrieve posts list"
                )
            }
        } else {
            mapOf(
                "success" to false,
                "error" to "No access token available",
                "message" to "Please authenticate with Facebook first"
            )
        }
    }

    @PostMapping("/api/post")
    @ResponseBody
    fun createPost(@RequestBody postData: Map<String, String>): Map<String, Any> {
        return if (storedAccessToken != null) {
            try {
                val message = postData["message"] ?: ""
                if (message.isBlank()) {
                    return mapOf(
                        "success" to false,
                        "error" to "Post message cannot be empty",
                        "message" to "Please enter some content for your post"
                    )
                }

                val result = createFacebookPost(storedAccessToken!!, message)
                mapOf(
                    "success" to true,
                    "postId" to (result["id"] ?: ""),
                    "message" to "Successfully created Facebook post"
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error occurred"),
                    "message" to "Failed to create post"
                )
            }
        } else {
            mapOf(
                "success" to false,
                "error" to "No access token available",
                "message" to "Please authenticate with Facebook first"
            )
        }
    }

    @GetMapping("/api/token/status")
    @ResponseBody
    fun getTokenStatus(): Map<String, Any> {
        return mapOf(
            "hasToken" to (storedAccessToken != null),
            "token" to (storedAccessToken ?: "")
        )
    }

    private fun fetchFacebookFriends(accessToken: String): List<Map<String, Any>> {
        val friendsUrl = "https://graph.facebook.com/v18.0/me/friends?access_token=$accessToken"

        val response = webClient.get()
            .uri(friendsUrl)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        if (response?.containsKey("error") == true) {
            val error = response["error"] as? Map<*, *>
            val errorMessage = error?.get("message") ?: "Unknown Facebook error"
            throw RuntimeException("Facebook API error: $errorMessage")
        }

        @Suppress("UNCHECKED_CAST")
        val data = response?.get("data") as? List<Map<String, Any>> ?: emptyList()
        return data
    }

    private fun fetchFacebookPosts(accessToken: String): List<Map<String, Any>> {
        val postsUrl = "https://graph.facebook.com/v18.0/me/posts?fields=id,message,story,created_time,updated_time,likes.summary(true),comments.summary(true),shares&limit=25&access_token=$accessToken"

        val response = webClient.get()
            .uri(postsUrl)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        if (response?.containsKey("error") == true) {
            val error = response["error"] as? Map<*, *>
            val errorMessage = error?.get("message") ?: "Unknown Facebook error"
            throw RuntimeException("Facebook API error: $errorMessage")
        }

        @Suppress("UNCHECKED_CAST")
        val data = response?.get("data") as? List<Map<String, Any>> ?: emptyList()
        return data
    }

    private fun createFacebookPost(accessToken: String, message: String): Map<String, Any> {
        // Facebook has restricted posting permissions for security reasons.
        // Personal feed posting requires special permissions that need Facebook app review.
        // For demonstration purposes, we'll attempt the API call and provide helpful error messages.

        try {
            return postToUserFeed(accessToken, message)
        } catch (e: Exception) {
            // Provide a helpful error message explaining Facebook's restrictions
            throw RuntimeException(
                "Facebook posting failed: ${e.message}. " +
                "Note: Facebook requires special permissions and app review for posting to user feeds. " +
                "This is a security measure to prevent spam and unauthorized posting. " +
                "For production apps, you would need to submit your app for Facebook review " +
                "and request 'publish_actions' or similar permissions.", e
            )
        }
    }

    private fun postToUserFeed(accessToken: String, message: String): Map<String, Any> {
        val postUrl = "https://graph.facebook.com/v18.0/me/feed"

        val formData = "message=${URLEncoder.encode(message, StandardCharsets.UTF_8)}" +
                "&access_token=$accessToken"

        val response = try {
            webClient.post()
                .uri(postUrl)
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
            throw RuntimeException("Facebook API error: $errorType - $errorMessage")
        }

        @Suppress("UNCHECKED_CAST")
        return response as? Map<String, Any> ?: throw RuntimeException("Invalid response from Facebook API")
    }



    private fun generateRandomState(): String {
        return (1..16)
            .map { ('a'..'z').random() }
            .joinToString("")
    }
}
