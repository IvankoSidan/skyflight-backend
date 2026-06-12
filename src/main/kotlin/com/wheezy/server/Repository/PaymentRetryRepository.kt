package com.wheezy.server.Repository

import com.wheezy.server.Models.Payment
import com.wheezy.server.Models.PaymentRetry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRetryRepository : JpaRepository<PaymentRetry, Long> {
    fun findByPaymentId(paymentId: Long) : PaymentRetry?
}
