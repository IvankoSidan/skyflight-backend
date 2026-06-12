package com.wheezy.server.Service

import com.wheezy.server.DTO.InvoicePdfData
import com.wheezy.server.Models.Invoice
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct

@Service
class InvoicePdfService(
    private val templateEngine: TemplateEngine
) {

    private lateinit var pdfStoragePath: Path
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val dateFormatterUs = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    @PostConstruct
    fun init() {
        pdfStoragePath = Paths.get(System.getProperty("user.dir"), "invoices")
        if (!Files.exists(pdfStoragePath)) {
            Files.createDirectories(pdfStoragePath)
        }
    }

    fun generateInvoicePdf(invoiceData: InvoicePdfData): ByteArrayOutputStream {
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
            setVariable("issueDateFormatted", formatDate(invoiceData.invoice.issueDate, invoiceData.user.email))
            setVariable("dueDateFormatted", formatDate(invoiceData.invoice.dueDate, invoiceData.user.email))
        }

        val htmlContent = templateEngine.process("invoice-template", context)

        val outputStream = ByteArrayOutputStream()
        val renderer = ITextRenderer()
        renderer.setDocumentFromString(htmlContent)
        renderer.layout()
        renderer.createPDF(outputStream)

        return outputStream
    }

    fun saveInvoicePdf(invoice: Invoice, pdfData: ByteArrayOutputStream): String {
        val fileName = "${invoice.invoiceNumber}.pdf"
        val filePath = pdfStoragePath.resolve(fileName)
        Files.write(filePath, pdfData.toByteArray())
        return filePath.toString()
    }

    fun getInvoicePdfFile(invoiceNumber: String): File? {
        val filePath = pdfStoragePath.resolve("$invoiceNumber.pdf")
        return if (Files.exists(filePath)) filePath.toFile() else null
    }

    fun deleteInvoicePdf(invoiceNumber: String): Boolean {
        val filePath = pdfStoragePath.resolve("$invoiceNumber.pdf")
        return if (Files.exists(filePath)) {
            Files.deleteIfExists(filePath)
        } else false
    }

    private fun formatDate(date: java.time.LocalDateTime, userEmail: String): String {
        return if (userEmail.contains(".ru") || userEmail.contains(".by")) {
            date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } else {
            date.format(dateFormatterUs)
        }
    }
}
