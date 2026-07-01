package com.wheezy.server.Service

import com.wheezy.server.DTO.InvoicePdfData
import com.wheezy.server.Models.Invoice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.annotation.PostConstruct

@Service
class InvoicePdfService(
    private val templateEngine: TemplateEngine
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var pdfStoragePath: Path

    @PostConstruct
    fun init() {
        try {
            pdfStoragePath = Paths.get(System.getProperty("user.dir"), "invoices")
            if (!Files.exists(pdfStoragePath)) {
                Files.createDirectories(pdfStoragePath)
            }
        } catch (e: Exception) {
            log.error("Failed to create invoices directory", e)
        }
    }

    fun generateInvoicePdf(invoiceData: InvoicePdfData): ByteArrayOutputStream? {
        return try {
            val context = Context().apply {
                setVariable("invoice", invoiceData.invoice)
                setVariable("booking", invoiceData.booking)
                setVariable("flight", invoiceData.flight)
                setVariable("user", invoiceData.user)
                setVariable("paymentMethod", invoiceData.paymentMethod ?: "Card")
                setVariable("paymentLast4", invoiceData.paymentLast4 ?: "****")
                setVariable("transactionId", invoiceData.transactionId ?: "N/A")
                setVariable("promocodeCode", invoiceData.promocodeCode)
                setVariable("discountPercent", invoiceData.discountPercent)
                setVariable("issueDateFormatted", invoiceData.invoice.issueDate.toString())
                setVariable("dueDateFormatted", invoiceData.invoice.dueDate.toString())
            }

            val htmlContent = templateEngine.process("invoice-template", context)
            val outputStream = ByteArrayOutputStream()
            val renderer = ITextRenderer()
            renderer.setDocumentFromString(htmlContent)
            renderer.layout()
            renderer.createPDF(outputStream)
            outputStream.close()
            outputStream
        } catch (e: Exception) {
            log.error("Failed to generate PDF", e)
            null
        }
    }

    fun saveInvoicePdf(invoice: Invoice, pdfData: ByteArrayOutputStream): String? {
        return try {
            val fileName = "${invoice.invoiceNumber}.pdf"
            val filePath = pdfStoragePath.resolve(fileName)
            Files.write(filePath, pdfData.toByteArray())
            filePath.toString()
        } catch (e: Exception) {
            log.error("Failed to save PDF", e)
            null
        }
    }

    fun getInvoicePdfFile(invoiceNumber: String): File? {
        val filePath = pdfStoragePath.resolve("$invoiceNumber.pdf")
        return if (Files.exists(filePath)) filePath.toFile() else null
    }
}