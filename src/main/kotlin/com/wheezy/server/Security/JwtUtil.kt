package com.wheezy.server.Security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*

@Component
class JwtUtil(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration}") private val defaultExpiration: Long,
    @Value("\${jwt.expiration.remember}") private val rememberExpiration: Long
) {
    private val logger: Logger = LoggerFactory.getLogger(JwtUtil::class.java)

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
        if (token.isBlank()) return false
        return try {
            val extractedUser = extractUsername(token)
            (extractedUser == username) && !isTokenExpired(token)
        } catch (ex: Exception) {
            logger.warn("Invalid JWT token: {}", ex.message)
            false
        }
    }

    fun extractUsername(token: String): String? {
        if (token.isBlank()) return null
        return try {
            getClaimFromToken(token) { it.subject }
        } catch (e: Exception) {
            logger.debug("Failed to extract username from token: ${e.message}")
            null
        }
    }

    private fun isTokenExpired(token: String): Boolean {
        if (token.isBlank()) return true
        return try {
            val expiration = getClaimFromToken(token) { it.expiration }
            expiration.before(Date())
        } catch (e: Exception) {
            true
        }
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

    fun extractExpiration(token: String): Date? {
        if (token.isBlank()) return null
        return try {
            getClaimFromToken(token) { it.expiration }
        } catch (e: Exception) {
            null
        }
    }

    fun isTokenValid(token: String): Boolean {
        if (token.isBlank()) return false
        return try {
            val username = extractUsername(token)
            username != null && validateToken(token, username)
        } catch (e: Exception) {
            false
        }
    }
}