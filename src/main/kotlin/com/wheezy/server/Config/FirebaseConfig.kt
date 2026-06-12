package com.wheezy.server.Config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.FileInputStream

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${fcm.enabled:true}")
    private var fcmEnabled: Boolean = true

    @Value("\${fcm.config-path:firebase-service-account.json}")
    private lateinit var configPath: String

    @PostConstruct
    fun init() {
        if (!fcmEnabled) {
            logger.warn("FCM is disabled via config")
            return
        }

        try {
            val resource = ClassPathResource(configPath)

            if (!resource.exists()) {
                val file = java.io.File(configPath)
                if (file.exists()) {
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(file.inputStream()))
                        .build()
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options)
                        logger.info("Firebase initialized successfully from file: $configPath")
                    }
                    return
                } else {
                    logger.warn("Firebase config file not found at: $configPath")
                    return
                }
            }

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(resource.inputStream))
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                logger.info("Firebase initialized successfully")
            }

        } catch (e: Exception) {
            logger.error("Firebase initialization failed", e)
        }
    }
}
