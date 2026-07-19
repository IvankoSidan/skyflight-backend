package com.wheezy.server.Controller

import com.wheezy.server.DTO.InvoiceListResponse
import com.wheezy.server.DTO.InvoiceResponse
import com.wheezy.server.DTO.TaxRateResponse
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.InvoicePdfService
import com.wheezy.server.Service.InvoiceService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File
import java.security.Principal

@RestController
@RequestMapping("/api/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val invoicePdfService: InvoicePdfService,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/booking/{bookingId}")
    fun getInvoiceByBookingId(
        principal: Principal,
        @PathVariable bookingId: Long
    ): ResponseEntity<InvoiceResponse> {
        val user = userRepository.findByEmail(principal.name) ?: return ResponseEntity.status(401).build()
        val userId = user.id ?: return ResponseEntity.status(500).build()

        val invoice = invoiceService.getInvoiceByBookingId(userId, bookingId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(InvoiceResponse.fromInvoice(invoice, "/api/invoices/${invoice.id}/download"))
    }

    @GetMapping("/my")
    fun getMyInvoices(
        principal: Principal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<InvoiceListResponse> {
        val user = userRepository.findByEmail(principal.name) ?: return ResponseEntity.status(401).build()
        val userId = user.id ?: return ResponseEntity.status(500).build()

        val invoices = invoiceService.getUserInvoices(userId, page, size)
        return ResponseEntity.ok(invoices)
    }

    @GetMapping("/{invoiceId}/download")
    fun downloadInvoice(
        principal: Principal,
        @PathVariable invoiceId: Long
    ): ResponseEntity<FileSystemResource> {
        val user = userRepository.findByEmail(principal.name) ?: return ResponseEntity.status(401).build()
        val userId = user.id ?: return ResponseEntity.status(500).build()

        val invoice = invoiceService.getInvoiceById(userId, invoiceId)
            ?: return ResponseEntity.notFound().build()

        val pdfFile = findInvoicePdfFile(invoice.invoiceNumber)
        if (pdfFile == null) {
            log.warn("PDF not found for invoice: ${invoice.invoiceNumber}")
            return ResponseEntity.notFound().build()
        }

        log.info("Downloading invoice PDF: ${pdfFile.absolutePath} (${pdfFile.length()} bytes)")

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${invoice.invoiceNumber}.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfFile.length())
            .body(FileSystemResource(pdfFile))
    }

    private fun findInvoicePdfFile(invoiceNumber: String): File? {
        val possiblePaths = listOf(
            "/opt/skyflight/invoices/$invoiceNumber.pdf",
            "/opt/skyflight/backups/invoices/$invoiceNumber.pdf",
            "/var/www/skyflight/invoices/$invoiceNumber.pdf",
            "./invoices/$invoiceNumber.pdf"
        )

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                log.debug("PDF found at: $path")
                return file
            }
        }

        val invoicesDirs = listOf(
            File("/opt/skyflight/invoices"),
            File("/var/www/skyflight/invoices"),
            File("./invoices")
        )

        for (dir in invoicesDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name -> name.startsWith(invoiceNumber) && name.endsWith(".pdf") }
                files?.firstOrNull()?.let { return it }
            }
        }

        return null
    }

    @PostMapping("/booking/{bookingId}/resend")
    fun resendInvoiceEmail(
        principal: Principal,
        @PathVariable bookingId: Long
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name) ?: return ResponseEntity.status(401).build()
        val userId = user.id ?: return ResponseEntity.status(500).build()

        val success = invoiceService.resendInvoiceEmail(userId, bookingId)

        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Invoice email resent successfully"))
        } else {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to resend invoice email"))
        }
    }

    @GetMapping("/tax-rates")
    fun getTaxRates(): ResponseEntity<List<TaxRateResponse>> {
        val taxRates = listOf(
            TaxRateResponse("US", "United States", "Sales Tax", java.math.BigDecimal(0)),
            TaxRateResponse("GB", "United Kingdom", "VAT", java.math.BigDecimal(20)),
            TaxRateResponse("DE", "Germany", "VAT", java.math.BigDecimal(19)),
            TaxRateResponse("FR", "France", "VAT", java.math.BigDecimal(20)),
            TaxRateResponse("AE", "UAE", "VAT", java.math.BigDecimal(5)),
            TaxRateResponse("IN", "India", "GST", java.math.BigDecimal(18)),
            TaxRateResponse("AU", "Australia", "GST", java.math.BigDecimal(10)),
            TaxRateResponse("CA", "Canada", "GST/HST", java.math.BigDecimal(5)),
            TaxRateResponse("RU", "Russia", "VAT", java.math.BigDecimal(0))
        )
        return ResponseEntity.ok(taxRates)
    }
}