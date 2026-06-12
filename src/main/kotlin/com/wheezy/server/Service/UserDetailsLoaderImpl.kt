package com.wheezy.server.Service

import com.wheezy.server.Models.User
import com.wheezy.server.Repository.UserDetailsLoader
import com.wheezy.server.Repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserDetailsLoaderImpl(private val userRepository: UserRepository) : UserDetailsLoader {
    override fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }
}
