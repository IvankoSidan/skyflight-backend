package com.wheezy.server.Service

import com.wheezy.server.DTO.GoogleUserDto
import com.wheezy.server.DTO.NotificationSettingsDTO
import com.wheezy.server.DTO.UserLoginDto
import com.wheezy.server.DTO.UserRegisterDto
import com.wheezy.server.DTO.UserResponseDto
import com.wheezy.server.Models.User
import com.wheezy.server.Repository.UserNotificationSettingsRepository
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Security.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val settingsRepository: UserNotificationSettingsRepository,
    private val notificationSenderService: NotificationSenderService
) {

    private fun getCountryCodeFromEmail(email: String): String {
        val domain = email.substringAfter("@").substringAfterLast(".")
        return when (domain) {
            "ru", "рф" -> "RU"
            "ua" -> "UA"
            "by" -> "BY"
            "kz" -> "KZ"
            "de" -> "DE"
            "fr" -> "FR"
            "uk" -> "GB"
            "com", "net", "org" -> "US"
            else -> "US"
        }
    }

    private fun getTaxRateForCountry(countryCode: String): BigDecimal {
        return when (countryCode) {
            "RU" -> BigDecimal(20)
            "UA" -> BigDecimal(20)
            "BY" -> BigDecimal(20)
            "KZ" -> BigDecimal(12)
            "DE" -> BigDecimal(19)
            "FR" -> BigDecimal(20)
            "GB" -> BigDecimal(20)
            else -> BigDecimal(0)
        }
    }

    @Transactional
    fun register(userRegisterDto: UserRegisterDto): UserResponseDto {
        if (userRepository.existsByEmail(userRegisterDto.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val countryCode = getCountryCodeFromEmail(userRegisterDto.email)
        val taxRate = getTaxRateForCountry(countryCode)

        val user = User(
            email = userRegisterDto.email,
            password = passwordEncoder.encode(userRegisterDto.password),
            name = userRegisterDto.name,
            countryCode = countryCode,
            taxRate = taxRate
        )
        val savedUser = userRepository.save(user)

        val userId = savedUser.id ?: throw IllegalStateException("User ID cannot be null")
        val defaultSettings = NotificationSettingsDTO().toEntity(userId)
        settingsRepository.save(defaultSettings)

        notificationSenderService.sendWelcomeEmail(userId)

        return mapToResponseDto(savedUser)
    }

    @Transactional
    fun login(userLoginDto: UserLoginDto): Pair<UserResponseDto, String> {
        val user = userRepository.findByEmail(userLoginDto.email)
            ?: throw IllegalArgumentException("User not found with email: ${userLoginDto.email}")

        if (user.password == null || !passwordEncoder.matches(userLoginDto.password, user.password)) {
            throw IllegalArgumentException("Invalid password")
        }

        val token = jwtUtil.generateToken(user.email)

        val userId = user.id ?: throw IllegalStateException("User ID cannot be null")
        if (!settingsRepository.existsByUserId(userId)) {
            val defaultSettings = NotificationSettingsDTO().toEntity(userId)
            settingsRepository.save(defaultSettings)
        }

        return Pair(mapToResponseDto(user), token)
    }

    @Transactional
    fun loginWithGoogle(googleUserDto: GoogleUserDto): Pair<UserResponseDto, String> {
        var user = userRepository.findByEmail(googleUserDto.email)

        if (user == null) {
            val countryCode = getCountryCodeFromEmail(googleUserDto.email)
            val taxRate = getTaxRateForCountry(countryCode)

            user = User(
                email = googleUserDto.email,
                googleId = googleUserDto.googleId,
                name = googleUserDto.name,
                profilePicture = googleUserDto.profilePicture,
                countryCode = countryCode,
                taxRate = taxRate
            )
            user = userRepository.save(user)

            val userId = user.id ?: throw IllegalStateException("User ID cannot be null")
            val defaultSettings = NotificationSettingsDTO().toEntity(userId)
            settingsRepository.save(defaultSettings)

        } else {
            if (user.googleId == null || user.googleId != googleUserDto.googleId) {
                val updatedUser = user.copy(googleId = googleUserDto.googleId)
                user = userRepository.save(updatedUser)
            }
        }

        val userId = user.id ?: throw IllegalStateException("User ID cannot be null")
        if (!settingsRepository.existsByUserId(userId)) {
            val defaultSettings = NotificationSettingsDTO().toEntity(userId)
            settingsRepository.save(defaultSettings)
        }

        val token = jwtUtil.generateToken(user.email)
        return Pair(mapToResponseDto(user), token)
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    private fun mapToResponseDto(user: User): UserResponseDto {
        return UserResponseDto(
            id = user.id ?: throw IllegalStateException("User ID cannot be null"),
            email = user.email,
            name = user.name,
            profilePicture = user.profilePicture,
            countryCode = user.countryCode,
            taxRate = user.taxRate
        )
    }
}