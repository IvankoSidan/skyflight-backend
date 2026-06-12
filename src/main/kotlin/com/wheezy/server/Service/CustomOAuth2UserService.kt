package com.wheezy.server.Service

import com.wheezy.server.Models.User
import com.wheezy.server.Repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(userRequest)
        val attributes = oauth2User.attributes

        val email = attributes["email"] as String
        val googleId = attributes["sub"] as String
        val name = attributes["name"] as String
        val profilePicture = attributes["picture"] as String?
        var user = userRepository.findByEmail(email)
        if (user == null) {
            user = User(
                email = email,
                googleId = googleId,
                name = name,
                profilePicture = profilePicture
            )
            userRepository.save(user)
        } else if (user.googleId == null) {
            user = user.copy(googleId = googleId)
            userRepository.save(user)
        }

        return oauth2User
    }
}
