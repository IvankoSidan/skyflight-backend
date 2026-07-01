package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import com.wheezy.server.Models.Invoice
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class InvoiceResponse(
    val id: Long,
    val invoiceNumber: String,
    val bookingId: Long,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val issueDate: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val dueDate: String,

    val currency: String,
    val subtotal: BigDecimal,
    val discountAmount: BigDecimal,
    val taxRate: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val status: String,
    val pdfUrl: String?,
    val downloadUrl: String?
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

        fun fromInvoice(invoice: Invoice, downloadUrl: String? = null): InvoiceResponse {
            return InvoiceResponse(
                id = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                bookingId = invoice.bookingId,
                issueDate = invoice.issueDate.format(dateFormatter),
                dueDate = invoice.dueDate.format(dateFormatter),
                currency = invoice.currency,
                subtotal = invoice.subtotal,
                discountAmount = invoice.discountAmount,
                taxRate = invoice.taxRate,
                taxAmount = invoice.taxAmount,
                totalAmount = invoice.totalAmount,
                status = invoice.status,
                pdfUrl = invoice.invoicePdfUrl,
                downloadUrl = downloadUrl
            )
        }
    }
}

data class InvoiceListResponse(
    val invoices: List<InvoiceResponse>,
    val totalCount: Int,
    val totalPages: Int,
    val currentPage: Int
)

data class InvoiceGenerateRequest(
    val bookingId: Long,
    val paymentId: Long,
    val currency: String = "USD",
    val subtotal: BigDecimal,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val taxRate: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal
)

data class InvoicePdfData(
    val invoice: Invoice,
    val booking: BookingDetailsDTO,
    val flight: FlightDTO,
    val user: UserResponseDto,
    val paymentMethod: String? = null,
    val paymentLast4: String? = null,
    val transactionId: String? = null,
    val promocodeCode: String? = null,
    val discountPercent: Int? = null
)

data class TaxRateResponse(
    val countryCode: String,
    val countryName: String,
    val taxName: String,
    val taxRate: BigDecimal
)
