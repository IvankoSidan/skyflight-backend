package com.wheezy.server.Service

import com.wheezy.server.DTO.*
import com.wheezy.server.Models.Invoice
import com.wheezy.server.Models.Promocode
import com.wheezy.server.Repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val taxRateRepository: TaxRateRepository,
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val promocodeRepository: PromocodeRepository,
    private val invoicePdfService: InvoicePdfService,
    private val emailService: BrevoEmailService,
    private val emailTemplateService: EmailTemplateService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val invoiceNumberFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Transactional
    fun generateInvoice(
        userId: Long,
        bookingId: Long,
        paymentId: Long,
        currency: String = "USD"
    ): Invoice {
        // Проверяем, не существует ли уже инвойс
        val existingInvoice = invoiceRepository.findByBookingId(bookingId)
        if (existingInvoice.isPresent) {
            log.info("Invoice already exists for booking $bookingId")
            return existingInvoice.get()
        }

        // Получаем данные
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { IllegalArgumentException("Booking not found: $bookingId") }

        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }

        val flight = flightRepository.findById(booking.flightId)
            .orElseThrow { IllegalArgumentException("Flight not found: ${booking.flightId}") }

        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        // Получаем налоговую ставку по стране пользователя
        val taxRate = getTaxRateForUser(user.email)

        // Получаем данные о промокоде и рассчитываем скидку
        var discountAmount = BigDecimal.ZERO
        val promocodeId = booking.promocodeId // Сохраняем в локальную переменную

        if (promocodeId != null) {
            val promocode = promocodeRepository.findById(promocodeId).orElse(null)
            if (promocode != null) {
                discountAmount = calculateDiscountAmount(flight.price, booking.seatCount, promocode)
            }
        }

        // Рассчитываем суммы
        val subtotal = flight.price.multiply(BigDecimal(booking.seatCount))
        val amountAfterDiscount = subtotal - discountAmount
        val taxAmount = amountAfterDiscount.multiply(taxRate.taxRate)
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        val totalAmount = amountAfterDiscount + taxAmount

        // Генерируем номер инвойса
        val invoiceNumber = generateInvoiceNumber()

        val invoice = Invoice(
            userId = userId,
            bookingId = bookingId,
            paymentId = paymentId,
            invoiceNumber = invoiceNumber,
            issueDate = LocalDateTime.now(),
            dueDate = LocalDateTime.now(),
            currency = currency,
            subtotal = subtotal,
            discountAmount = discountAmount,
            taxRate = taxRate.taxRate,
            taxAmount = taxAmount,
            totalAmount = totalAmount,
            status = "PAID"
        )

        val savedInvoice = invoiceRepository.save(invoice)
        log.info("Invoice saved: ${savedInvoice.invoiceNumber}")

        // Генерируем PDF и отправляем email
        try {
            generateAndSendInvoicePdf(savedInvoice)
        } catch (e: Exception) {
            log.error("Failed to generate/send PDF for invoice ${savedInvoice.id}", e)
        }

        return savedInvoice
    }

    @Transactional
    fun generateAndSendInvoicePdf(invoice: Invoice) {
        log.info("Generating PDF for invoice: ${invoice.invoiceNumber}")

        val booking = bookingRepository.findById(invoice.bookingId).orElse(null) ?: return
        val flight = flightRepository.findById(booking.flightId).orElse(null) ?: return
        val user = userRepository.findById(invoice.userId).orElse(null) ?: return
        val payment = invoice.paymentId?.let { paymentRepository.findById(it).orElse(null) }

        // Форматируем метод оплаты
        val paymentMethod = payment?.let {
            val last4 = if (it.providerPaymentId.length >= 4) {
                it.providerPaymentId.takeLast(4)
            } else {
                "****"
            }
            "Card •••• $last4"
        } ?: "Credit Card"

        val paymentLast4 = payment?.providerPaymentId?.takeLast(4) ?: "****"
        val transactionId = payment?.providerPaymentId ?: "N/A"

        // Получаем промокод - используем локальную переменную
        var promocodeCode: String? = null
        var discountPercent: Int? = null
        val promocodeId = booking.promocodeId // Сохраняем в локальную переменную

        if (promocodeId != null) {
            val promocode = promocodeRepository.findById(promocodeId).orElse(null)
            promocodeCode = promocode?.code
            discountPercent = promocode?.discountPercent
        }

        // Получаем детали бронирования
        val bookingDetails = getBookingDetails(booking.id)

        // Конвертируем Flight в FlightDTO
        val flightDTO = FlightDTO.fromEntity(flight)

        val invoiceData = InvoicePdfData(
            invoice = invoice,
            booking = bookingDetails,
            flight = flightDTO,
            user = UserResponseDto(user.id!!, user.email, user.name, user.profilePicture),
            paymentMethod = paymentMethod,
            paymentLast4 = paymentLast4,
            transactionId = transactionId,
            promocodeCode = promocodeCode,
            discountPercent = discountPercent
        )

        val pdfStream = invoicePdfService.generateInvoicePdf(invoiceData)
        val pdfPath = invoicePdfService.saveInvoicePdf(invoice, pdfStream)

        invoice.invoicePdfUrl = pdfPath
        invoice.pdfGenerated = true
        invoiceRepository.save(invoice)

        // Отправляем email с PDF
        sendInvoiceEmailWithAttachment(user, invoice, pdfStream)
    }

    private fun getTaxRateForUser(email: String): com.wheezy.server.Models.TaxRate {
        val countryCode = getCountryCodeFromEmail(email)
        return taxRateRepository.findByCountryCode(countryCode)
            .orElseGet {
                taxRateRepository.findByIsDefaultTrue()
                    .orElseThrow { IllegalStateException("No default tax rate found") }
            }
    }

    private fun getCountryCodeFromEmail(email: String): String {
        val domain = email.substringAfter("@").substringAfterLast(".")
        return when (domain) {
            "ru" -> "RU"
            "com", "net", "org" -> "US"
            "uk" -> "GB"
            "de" -> "DE"
            "fr" -> "FR"
            "es" -> "ES"
            "it" -> "IT"
            "ae" -> "AE"
            "sa" -> "SA"
            "in" -> "IN"
            "au" -> "AU"
            "ca" -> "CA"
            "tr" -> "TR"
            "ch" -> "CH"
            "no" -> "NO"
            "jp" -> "JP"
            "cn" -> "CN"
            "br" -> "BR"
            "mx" -> "MX"
            "za" -> "ZA"
            else -> "US"
        }
    }

    private fun calculateDiscountAmount(price: BigDecimal, seatCount: Int, promocode: Promocode): BigDecimal {
        val subtotal = price.multiply(BigDecimal(seatCount))

        // Сохраняем значения в локальные переменные для safe cast
        val discountAmountValue = promocode.discountAmount
        val discountPercentValue = promocode.discountPercent

        return when {
            discountAmountValue != null -> {
                BigDecimal(discountAmountValue).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            }
            discountPercentValue != null && discountPercentValue > 0 -> {
                subtotal.multiply(BigDecimal(discountPercentValue))
                    .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            }
            else -> BigDecimal.ZERO
        }
    }

    private fun generateInvoiceNumber(): String {
        val datePart = LocalDateTime.now().format(invoiceNumberFormatter)
        val maxId = invoiceRepository.getMaxId() ?: 0
        val sequencePart = (maxId + 1).toString().padStart(5, '0')
        return "INV-$datePart-$sequencePart"
    }

    private fun getBookingDetails(bookingId: Long): BookingDetailsDTO {
        val booking = bookingRepository.findById(bookingId).orElseThrow()
        val flight = flightRepository.findById(booking.flightId).orElseThrow()

        return BookingDetailsDTO(
            bookingId = booking.id,
            seatNumbers = booking.seatNumbers,
            seatCount = booking.seatCount,
            status = booking.status,
            bookingDate = booking.bookingDate,
            flightId = booking.flightId,
            airlineName = flight.airlineName,
            airlineLogo = flight.airlineLogo,
            departureCity = flight.departureCity,
            arrivalCity = flight.arrivalCity,
            departureTime = flight.departureTime,
            arriveTime = flight.arriveTime,
            flightDate = flight.flightDate,
            classSeat = flight.classSeat,
            price = flight.price,
            paidAmount = booking.paidAmount,
            promocodeId = booking.promocodeId
        )
    }

    private fun sendInvoiceEmailWithAttachment(
        user: com.wheezy.server.Models.User,
        invoice: Invoice,
        pdfStream: java.io.ByteArrayOutputStream
    ) {
        try {
            val subject = "Your SkyFlight Invoice #${invoice.invoiceNumber}"
            val htmlContent = emailTemplateService.invoiceEmail(user, invoice)

            val success = emailService.sendEmailWithAttachment(
                to = user.email,
                subject = subject,
                htmlContent = htmlContent,
                attachmentName = "${invoice.invoiceNumber}.pdf",
                attachmentData = pdfStream.toByteArray()
            )

            if (success) {
                log.info("Invoice email sent to ${user.email} for invoice ${invoice.invoiceNumber}")
            } else {
                log.error("Failed to send invoice email to ${user.email}")
            }
        } catch (e: Exception) {
            log.error("Failed to send invoice email", e)
        }
    }

    fun getInvoiceByBookingId(userId: Long, bookingId: Long): Invoice? {
        return invoiceRepository.findByBookingId(bookingId)
            .filter { it.userId == userId }
            .orElse(null)
    }

    fun getInvoiceById(userId: Long, invoiceId: Long): Invoice? {
        return invoiceRepository.findById(invoiceId)
            .filter { it.userId == userId }
            .orElse(null)
    }

    fun getUserInvoices(userId: Long, page: Int, size: Int): InvoiceListResponse {
        val pageable = PageRequest.of(page, size, Sort.by("issueDate").descending())
        val pageResult = invoiceRepository.findByUserId(userId, pageable)

        return InvoiceListResponse(
            invoices = pageResult.content.map { invoice ->
                InvoiceResponse.fromInvoice(invoice, "/api/invoices/${invoice.id}/download")
            },
            totalCount = pageResult.totalElements.toInt(),
            totalPages = pageResult.totalPages,
            currentPage = page
        )
    }

    fun resendInvoiceEmail(userId: Long, bookingId: Long): Boolean {
        val invoice = getInvoiceByBookingId(userId, bookingId) ?: return false
        val user = userRepository.findById(userId).orElse(null) ?: return false

        val pdfFile = invoicePdfService.getInvoicePdfFile(invoice.invoiceNumber)
        if (pdfFile == null || !pdfFile.exists()) {
            log.warn("PDF file not found for invoice ${invoice.invoiceNumber}, regenerating...")
            return try {
                generateAndSendInvoicePdf(invoice)
                true
            } catch (e: Exception) {
                log.error("Failed to regenerate PDF", e)
                false
            }
        }

        return try {
            val pdfBytes = pdfFile.readBytes()
            val subject = "Your SkyFlight Invoice #${invoice.invoiceNumber}"
            val htmlContent = emailTemplateService.invoiceEmail(user, invoice)

            emailService.sendEmailWithAttachment(
                to = user.email,
                subject = subject,
                htmlContent = htmlContent,
                attachmentName = "${invoice.invoiceNumber}.pdf",
                attachmentData = pdfBytes
            )
            log.info("Invoice email resent to ${user.email}")
            true
        } catch (e: Exception) {
            log.error("Failed to resend invoice email", e)
            false
        }
    }
}
