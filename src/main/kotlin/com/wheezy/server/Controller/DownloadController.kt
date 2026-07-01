package com.wheezy.server.Controller

import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
class DownloadController {

    @GetMapping("/download")
    fun downloadApk(): ResponseEntity<FileSystemResource> {
        val apkFile = File("/var/www/skyflight/apk/app-release.apk")

        if (!apkFile.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"SkyFlight.apk\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(FileSystemResource(apkFile))
    }
}