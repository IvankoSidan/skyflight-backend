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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

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
    fun register(@RequestBody userRegisterDto: UserRegisterDto): ResponseEntity<AuthResponse> {
        return try {
            val userResponse = userService.register(userRegisterDto)
            ResponseEntity.ok(AuthResponse(user = userResponse, token = ""))
        } catch (ex: IllegalArgumentException) {
            logger.warn("Registration failed: ${ex.message}")
            ResponseEntity.badRequest().build()
        } catch (ex: Exception) {
            logger.error("Error during registration", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody userLoginDto: UserLoginDto): ResponseEntity<AuthResponse> {
        return try {
            val (userResponse, token) = userService.login(userLoginDto)
            ResponseEntity.ok(AuthResponse(user = userResponse, token = token))
        } catch (ex: IllegalArgumentException) {
            logger.warn("Login failed: ${ex.message}")
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } catch (ex: Exception) {
            logger.error("Error during login", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/google")
    fun loginWithGoogle(@RequestBody request: GoogleAuthRequestDto): ResponseEntity<AuthResponse> {
        val googleUser = validateAndParseGoogleIdToken(request.id_token)
        if (googleUser == null) {
            logger.warn("Invalid Google ID token")
            return ResponseEntity.badRequest().build()
        }

        return try {
            val (userResponse, token) = userService.loginWithGoogle(googleUser)
            ResponseEntity.ok(AuthResponse(user = userResponse, token = token))
        } catch (ex: IllegalStateException) {
            logger.error("Failed to save Google user: ${ex.message}", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
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
                        GoogleUserDto(
                            googleId = googleId,
                            email = email,
                            name = name,
                            profilePicture = picture
                        )
                    } else {
                        logger.warn("Google token payload missing required fields")
                        null
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to validate Google ID token: ${ex.message}", ex)
            null
        }
    }

    @GetMapping("/oauth2/success")
    fun oauth2Success(@RequestParam token: String): ResponseEntity<AuthResponse> {
        val username = jwtUtil.extractUsername(token)
        if (username == null) {
            logger.warn("Invalid token")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userService.findByEmail(username)
        if (user == null) {
            logger.warn("User not found: $username")
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        return ResponseEntity.ok(AuthResponse(user = mapToResponseDto(user), token = token))
    }

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication?): ResponseEntity<AuthResponse> {
        if (authentication == null) {
            logger.warn("Authentication is null")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val userName = authentication.name
        if (userName.isNullOrBlank()) {
            logger.warn("Authentication name is empty")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userService.findByEmail(userName)
        if (user == null) {
            logger.warn("User not found: $userName")
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        return ResponseEntity.ok(AuthResponse(user = mapToResponseDto(user), token = ""))
    }

    private fun mapToResponseDto(user: User): UserResponseDto =
        UserResponseDto(
            id = user.id ?: throw IllegalStateException("User ID cannot be null"),
            email = user.email,
            name = user.name,
            profilePicture = user.profilePicture,
            countryCode = user.countryCode,
            taxRate = user.taxRate
        )
}
