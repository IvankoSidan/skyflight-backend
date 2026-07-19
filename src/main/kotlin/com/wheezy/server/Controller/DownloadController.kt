package com.wheezy.server.Controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView

@Controller
class DownloadController {

    @Value("\${app.download.url}")
    private lateinit var downloadUrl: String

    @GetMapping("/download")
    fun downloadApk(): RedirectView {
        return RedirectView(downloadUrl)
    }

    @GetMapping("/download-page")
    fun downloadPage(): String {
        return "download"
    }
}