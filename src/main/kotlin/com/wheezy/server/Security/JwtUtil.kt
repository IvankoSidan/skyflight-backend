package com.wheezy.server.Security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import org.springframework.stereotype.Component
import java.util.*
import io.jsonwebtoken.security.Keys
import java.security.Key
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

@Component
class JwtUtil(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration}") private val defaultExpiration: Long,
    @Value("\${jwt.expiration.remember}") private val rememberExpiration: Long
) {
    private val logger: Logger = LoggerFactory.getLogger(JwtUtil::class.java)

    // Декодируем Base64 и создаём Key для HS256
    private val signingKey: Key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    fun generateToken(email: String, rememberMe: Boolean = false): String {
        val ttl = if (rememberMe) rememberExpiration else defaultExpiration
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(Date(now))
            .setExpiration(Date(now + ttl))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact()
    }

    fun validateToken(token: String, username: String): Boolean {
        return try {
            val extractedUser = extractUsername(token)
            (extractedUser == username) && !isTokenExpired(token)
        } catch (ex: Exception) {
            logger.warn("Invalid JWT token: {}", ex.message)
            false
        }
    }

    fun extractUsername(token: String): String =
        getClaimFromToken(token) { it.subject }

    private fun isTokenExpired(token: String): Boolean {
        val expiration = getClaimFromToken(token) { it.expiration }
        return expiration.before(Date())
    }

    private fun <T> getClaimFromToken(token: String, claimsResolver: (Claims) -> T): T {
        val claims = parseAllClaims(token)
        return claimsResolver(claims)
    }

    private fun parseAllClaims(token: String): Claims {
        if (token.isBlank()) throw IllegalArgumentException("JWT token is missing")
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .body
    }
}
