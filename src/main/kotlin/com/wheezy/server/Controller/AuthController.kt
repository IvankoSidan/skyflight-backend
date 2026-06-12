package com.wheezy.server.Controller

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.wheezy.server.DTO.*
import com.wheezy.server.Models.User
import com.wheezy.server.Security.JwtUtil
import com.wheezy.server.Service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val jwtUtil: JwtUtil,
    @Value("\${spring.security.oauth2.client.registration.google.client-id}")
    private val googleClientId: String
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/register")
    fun register(@RequestBody userRegisterDto: UserRegisterDto): ResponseEntity<UserResponseDto> {
        return try {
            val userResponse = userService.register(userRegisterDto)
            ResponseEntity.ok(userResponse)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: Exception) {
            logger.error("Error during registration", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody userLoginDto: UserLoginDto): ResponseEntity<Map<String, Any?>> {
        return try {
            val (userResponse, token) = userService.login(userLoginDto)
            ResponseEntity.ok(mapOf<String, Any?>("user" to userResponse, "token" to token))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf<String, Any?>("error" to ex.message))
        } catch (ex: Exception) {
            logger.error("Error during login", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/google")
    fun loginWithGoogle(@RequestBody request: GoogleAuthRequestDto): ResponseEntity<Map<String, Any?>> {
        logger.info("Received Google login request")

        val googleUser = validateAndParseGoogleIdToken(request.id_token)
            ?: return ResponseEntity.badRequest().body(mapOf<String, Any?>("error" to "Invalid Google ID token"))

        return try {
            val (userResponse, token) = userService.loginWithGoogle(googleUser)
            logger.info("Google user authenticated: email=${googleUser.email}")
            ResponseEntity.ok(mapOf<String, Any?>("user" to userResponse, "token" to token))
        } catch (ex: Exception) {
            logger.error("Error during Google login", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun validateAndParseGoogleIdToken(idTokenString: String): GoogleUserDto? {
        return try {
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            val verifier = GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(listOf(googleClientId))
                .build()

            idTokenString.takeIf { it.isNotEmpty() }?.let {
                val idToken: GoogleIdToken? = verifier.verify(it)
                idToken?.payload?.let { payload ->
                    val googleId = payload.subject
                    val email = payload.email
                    val name = payload["name"] as? String
                    val picture = payload["picture"] as? String

                    if (googleId != null && email != null) {
                        GoogleUserDto(googleId = googleId, email = email, name = name, profilePicture = picture)
                    } else {
                        logger.warn("Google token payload missing required fields")
                        null
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to validate Google ID token", ex)
            null
        }
    }

    private fun mapToResponseDto(user: User): UserResponseDto =
        UserResponseDto(id = user.id ?: throw IllegalStateException("User ID cannot be null"), email = user.email, name = user.name, profilePicture = user.profilePicture)

    @GetMapping("/oauth2/success")
    fun oauth2Success(@RequestParam token: String): ResponseEntity<Map<String, Any?>> {
        val user = userService.findByEmail(jwtUtil.extractUsername(token)) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(mapOf<String, Any?>("user" to mapToResponseDto(user), "token" to token))
    }

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): ResponseEntity<AuthResponse> {
        val user = userService.findByEmail(authentication.name) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(AuthResponse(user = mapToResponseDto(user), token = ""))
    }
}
