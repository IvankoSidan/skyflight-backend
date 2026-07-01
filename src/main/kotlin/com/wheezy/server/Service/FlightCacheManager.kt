package com.wheezy.server.Service

import com.fasterxml.jackson.databind.ObjectMapper
import com.wheezy.server.DTO.FlightDTO
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FlightCacheManager(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(FlightCacheManager::class.java)
    private val FLIGHT_PREFIX = "skyflight:flight:"
    private val FLIGHTS_PREFIX = "skyflight:flights:"
    private val TTL_SECONDS = 3600L

    // ⬇️ ПОЛУЧЕНИЕ ОДНОГО РЕЙСА С ПРАВИЛЬНОЙ ТИПИЗАЦИЕЙ
    fun getFlight(id: Long): FlightDTO? {
        val key = "$FLIGHT_PREFIX$id"
        return try {
            val raw = redisTemplate.opsForValue().get(key)
            when (raw) {
                is FlightDTO -> raw
                is Map<*, *> -> {
                    // Если это LinkedHashMap — конвертируем в FlightDTO
                    objectMapper.convertValue(raw, FlightDTO::class.java)
                }
                is String -> {
                    // Если это JSON строка — парсим
                    objectMapper.readValue(raw, FlightDTO::class.java)
                }
                else -> {
                    logger.warn("Unknown cache type for key $key: ${raw?.javaClass}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to deserialize flight from cache: ${e.message}")
            // Если не удалось десериализовать — удаляем битый кеш
            redisTemplate.delete(key)
            null
        }
    }

    // ⬇️ СОХРАНЕНИЕ ОДНОГО РЕЙСА
    fun putFlight(id: Long, flight: FlightDTO) {
        val key = "$FLIGHT_PREFIX$id"
        try {
            redisTemplate.opsForValue().set(key, flight, TTL_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.error("Failed to cache flight $id: ${e.message}")
        }
    }

    // ⬇️ ПОЛУЧЕНИЕ СПИСКА РЕЙСОВ
    fun getFlights(key: String): List<FlightDTO>? {
        val fullKey = "$FLIGHTS_PREFIX$key"
        return try {
            val raw = redisTemplate.opsForValue().get(fullKey)
            when (raw) {
                is List<*> -> {
                    // Конвертируем каждый элемент
                    raw.mapNotNull { item ->
                        when (item) {
                            is FlightDTO -> item
                            is Map<*, *> -> objectMapper.convertValue(item, FlightDTO::class.java)
                            else -> null
                        }
                    }
                }
                is String -> {
                    // Парсим JSON массив
                    objectMapper.readValue(raw, Array<FlightDTO>::class.java).toList()
                }
                else -> {
                    logger.warn("Unknown cache type for key $fullKey: ${raw?.javaClass}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to deserialize flights from cache: ${e.message}")
            redisTemplate.delete(fullKey)
            null
        }
    }

    // ⬇️ СОХРАНЕНИЕ СПИСКА РЕЙСОВ
    fun putFlights(key: String, flights: List<FlightDTO>) {
        val fullKey = "$FLIGHTS_PREFIX$key"
        try {
            redisTemplate.opsForValue().set(fullKey, flights, TTL_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.error("Failed to cache flights for key $key: ${e.message}")
        }
    }

    // ⬇️ ОЧИСТКА ВСЕХ КЕШЕЙ
    fun clearAll() {
        try {
            val keys = redisTemplate.keys("$FLIGHT_PREFIX*") ?: emptySet()
            val keys2 = redisTemplate.keys("$FLIGHTS_PREFIX*") ?: emptySet()
            val allKeys = keys + keys2
            if (allKeys.isNotEmpty()) {
                redisTemplate.delete(allKeys)
                logger.info("Cleared ${allKeys.size} flight cache keys")
            }
        } catch (e: Exception) {
            logger.error("Failed to clear flight cache: ${e.message}")
        }
    }

    // ⬇️ УДАЛЕНИЕ КОНКРЕТНОГО РЕЙСА ИЗ КЕША
    fun evictFlight(id: Long) {
        val key = "$FLIGHT_PREFIX$id"
        try {
            redisTemplate.delete(key)
        } catch (e: Exception) {
            logger.error("Failed to evict flight $id: ${e.message}")
        }
    }
}