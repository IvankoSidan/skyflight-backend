package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "invoices")
data class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "booking_id", nullable = false)
    val bookingId: Long,

    @Column(name = "payment_id")
    val paymentId: Long? = null,

    @Column(name = "invoice_number", nullable = false, unique = true)
    val invoiceNumber: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "issue_date", nullable = false)
    val issueDate: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDateTime,

    @Column(nullable = false, length = 3)
    val currency: String = "USD",

    @Column(nullable = false)
    val subtotal: BigDecimal,

    @Column(name = "discount_amount", nullable = false)
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_rate", nullable = false)
    val taxRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_amount", nullable = false)
    val taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: BigDecimal,

    @Column(nullable = false, length = 20)
    var status: String = "PAID",

    @Column(name = "invoice_pdf_url", length = 500)
    var invoicePdfUrl: String? = null,

    @Column(name = "pdf_generated")
    var pdfGenerated: Boolean = false,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class InvoiceStatus {
    PAID, REFUNDED, PARTIALLY_REFUNDED
}