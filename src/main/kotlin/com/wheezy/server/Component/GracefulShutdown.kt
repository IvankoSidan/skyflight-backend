package com.wheezy.server.Component

import org.slf4j.LoggerFactory
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy

@Component
class GracefulShutdown : ApplicationListener<ContextClosedEvent> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val executorService: ExecutorService = Executors.newFixedThreadPool(10)
    private val activeRequests = java.util.concurrent.atomic.AtomicInteger(0)

    fun registerActiveRequest() {
        activeRequests.incrementAndGet()
    }

    fun unregisterActiveRequest() {
        activeRequests.decrementAndGet()
    }

    fun getActiveRequests(): Int = activeRequests.get()

    override fun onApplicationEvent(event: ContextClosedEvent) {
        log.info("🛑 Graceful shutdown initiated. Active requests: ${activeRequests.get()}")

        // Отмечаем, что приложение больше не принимает новые запросы
        AvailabilityChangeEvent.publish(event.applicationContext, LivenessState.BROKEN)

        // Ждём завершения активных запросов (максимум 30 секунд)
        val startTime = System.currentTimeMillis()
        val maxWaitSeconds = 30L

        while (activeRequests.get() > 0 &&
            (System.currentTimeMillis() - startTime) < maxWaitSeconds * 1000) {
            log.info("Waiting for ${activeRequests.get()} active requests to complete...")
            Thread.sleep(500)
        }

        if (activeRequests.get() > 0) {
            log.warn("⚠️ Force shutdown after $maxWaitSeconds seconds. ${activeRequests.get()} requests still active.")
        } else {
            log.info("✅ All active requests completed. Shutting down.")
        }

        // Завершаем executor service
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    @PreDestroy
    fun preDestroy() {
        log.info("PreDestroy called - cleaning up resources")
        executorService.shutdownNow()
    }
}