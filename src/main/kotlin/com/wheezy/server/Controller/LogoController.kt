package com.wheezy.server.Controller

import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/logo")
class LogoController {

    companion object {
        private const val LOGO_PATH = "/opt/skyflight/static/uploads/"
        private val extensions = listOf("png", "svg", "jpg", "jpeg", "webp")
    }

    @GetMapping
    fun getDefaultLogo(): ResponseEntity<FileSystemResource> {
        val defaultNames = listOf("default", "skyflight", "logo")
        for (name in defaultNames) {
            for (ext in extensions) {
                val file = File("$LOGO_PATH$name.$ext")
                if (file.exists()) {
                    return buildResponse(file, ext)
                }
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/{airline}")
    fun getLogo(@PathVariable airline: String): ResponseEntity<FileSystemResource> {
        for (ext in extensions) {
            val file = File("$LOGO_PATH$airline.$ext")
            if (file.exists()) {
                return buildResponse(file, ext)
            }
        }

        val baseName = airline.substringBeforeLast('.')
        if (baseName.isNotEmpty() && baseName != airline) {
            for (ext in extensions) {
                val file = File("$LOGO_PATH$baseName.$ext")
                if (file.exists()) {
                    return buildResponse(file, ext)
                }
            }
        }

        val fileNoExt = File("$LOGO_PATH$airline")
        if (fileNoExt.exists()) {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileNoExt.length())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(FileSystemResource(fileNoExt))
        }

        if (baseName.isNotEmpty() && baseName != airline) {
            val fallbackFile = File("$LOGO_PATH$baseName")
            if (fallbackFile.exists()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fallbackFile.length())
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .body(FileSystemResource(fallbackFile))
            }
        }

        return ResponseEntity.notFound().build()
    }

    private fun buildResponse(file: File, ext: String): ResponseEntity<FileSystemResource> {
        val mediaType = when (ext) {
            "png" -> MediaType.IMAGE_PNG
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "svg" -> MediaType.parseMediaType("image/svg+xml")
            "webp" -> MediaType.parseMediaType("image/webp")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(file.length())
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .body(FileSystemResource(file))
    }
}