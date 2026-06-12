package com.wheezy.server.Repository

import com.wheezy.server.Models.Invoice
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface InvoiceRepository : JpaRepository<Invoice, Long> {

    fun findByBookingId(bookingId: Long): Optional<Invoice>

    fun findByInvoiceNumber(invoiceNumber: String): Optional<Invoice>

    fun findByUserId(userId: Long): List<Invoice>

    fun findByUserId(userId: Long, pageable: Pageable): Page<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.userId = :userId ORDER BY i.issueDate DESC")
    fun findAllByUserIdOrderByIssueDateDesc(userId: Long): List<Invoice>

    @Query("SELECT i FROM Invoice i WHERE i.userId = :userId AND i.status = :status")
    fun findByUserIdAndStatus(userId: Long, status: String): List<Invoice>

    fun existsByBookingId(bookingId: Long): Boolean

    @Query("SELECT MAX(i.id) FROM Invoice i")
    fun getMaxId(): Long?
}
