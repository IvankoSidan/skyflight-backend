package com.wheezy.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAsync
@EnableScheduling
@EnableCaching
@SpringBootApplication
class SpringBootIntelliJSeApplication

fun main(args: Array<String>) {
    runApplication<SpringBootIntelliJSeApplication>(*args)
}
