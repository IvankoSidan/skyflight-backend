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

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val settingsRepository: UserNotificationSettingsRepository
) {

    @Transactional
    fun register(userRegisterDto: UserRegisterDto): UserResponseDto {
        if (userRepository.existsByEmail(userRegisterDto.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user = User(
            email = userRegisterDto.email,
            password = passwordEncoder.encode(userRegisterDto.password),
            name = userRegisterDto.name
        )
        val savedUser = userRepository.save(user)

        val userId = savedUser.id ?: throw IllegalStateException("User ID cannot be null")
        val defaultSettings = NotificationSettingsDTO().toEntity(userId)
        settingsRepository.save(defaultSettings)

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
        var user = userRepository.findByGoogleId(googleUserDto.googleId)

        if (user == null) {
            user = userRepository.findByEmail(googleUserDto.email)
            if (user == null) {
                user = User(
                    email = googleUserDto.email,
                    googleId = googleUserDto.googleId,
                    name = googleUserDto.name,
                    profilePicture = googleUserDto.profilePicture
                )
                user = userRepository.save(user)

                // Создаём настройки для нового пользователя
                val userId = user.id ?: throw IllegalStateException("User ID cannot be null")
                val defaultSettings = NotificationSettingsDTO().toEntity(userId)
                settingsRepository.save(defaultSettings)
            } else {
                // Обновляем существующего пользователя Google ID
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
            profilePicture = user.profilePicture
        )
    }
}
