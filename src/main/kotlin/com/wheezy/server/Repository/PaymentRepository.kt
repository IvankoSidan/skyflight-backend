package com.wheezy.server.Repository

import com.wheezy.server.Models.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByBookingId(bookingId: Long): List<Payment>
    fun findByStripePaymentId(stripePaymentId: String): Payment?
    @Transactional
    @Modifying
    fun deleteByBookingId(bookingId: Long)
}
