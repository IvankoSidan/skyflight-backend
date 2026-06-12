package com.wheezy.server.Controller

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/logo")
class LogoController {

    @GetMapping(
        "/{airline}",
        produces = [MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/svg+xml"]
    )
    fun getLogo(@PathVariable airline: String): ResponseEntity<Resource> {
        val formats = listOf("png", "svg", "jpg", "jpeg")
        val resource = formats
            .map { ClassPathResource("static/uploads/$airline.$it") }
            .firstOrNull { it.exists() }
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                Files.probeContentType(Paths.get(resource.filename))))
            .contentLength(resource.contentLength())
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
            .body(resource)
    }
}
