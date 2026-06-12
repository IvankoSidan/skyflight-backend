package com.wheezy.server.Repository

import com.wheezy.server.Models.User

interface UserDetailsLoader {
    fun findByEmail(email: String): User?
}
