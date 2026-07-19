package com.wheezy.server.Controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView

@Controller
class RedirectController {

    @GetMapping("/my-bookings")
    fun myBookings(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/profile")
    fun profile(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/bookings")
    fun bookings(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/settings")
    fun settings(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/payment")
    fun payment(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/success")
    fun success(): RedirectView {
        return RedirectView("/")
    }

    @GetMapping("/cancel")
    fun cancel(): RedirectView {
        return RedirectView("/")
    }
}