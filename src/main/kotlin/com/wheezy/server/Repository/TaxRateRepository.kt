package com.wheezy.server.Repository

import com.wheezy.server.Models.TaxRate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TaxRateRepository : JpaRepository<TaxRate, Long> {

    fun findByCountryCode(countryCode: String): Optional<TaxRate>

    fun findByIsDefaultTrue(): Optional<TaxRate>

    @Query("SELECT t FROM TaxRate t WHERE t.isActive = true ORDER BY t.countryCode")
    fun findAllActive(): List<TaxRate>

    fun existsByCountryCode(countryCode: String): Boolean
}
