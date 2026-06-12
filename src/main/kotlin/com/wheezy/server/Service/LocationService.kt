package com.wheezy.server.Service

import com.wheezy.server.Models.LocationModel
import com.wheezy.server.Repository.LocationRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class LocationService(
    private val locationRepository: LocationRepository
) {

    private val logger = LoggerFactory.getLogger(LocationService::class.java)

    @Cacheable(value = ["locations"], unless = "#result == null || #result.isEmpty()")
    fun getAllLocations(): List<LocationModel> {
        logger.info("🟢 Loading locations from DATABASE (cache miss)")
        return locationRepository.findAll()
    }

    @CacheEvict(value = ["locations"], allEntries = true)
    fun clearLocationsCache() {
        logger.info("🗑️ Locations cache cleared")
    }
}
