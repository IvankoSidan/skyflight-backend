package com.wheezy.server.Service

import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Flight
import com.wheezy.server.Models.Invoice
import com.wheezy.server.Models.User
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class EmailTemplateService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun bookingConfirmation(user: User, booking: Booking, flight: Flight, amount: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">✈️ SkyFlight</h1>
                <p style="margin: 5px 0 0;">Booking Confirmed!</p>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Your booking <strong>#${booking.id}</strong> is confirmed.</p>
                <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Flight:</strong></td><td style="padding: 10px;">${flight.departureCity} → ${flight.arrivalCity}</td></tr>
                    <tr><td style="padding: 10px;"><strong>Date:</strong></td><td style="padding: 10px;">${flight.flightDate}</td></tr>
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Time:</strong></td><td style="padding: 10px;">${flight.departureTime}</td></tr>
                    <tr><td style="padding: 10px;"><strong>Seats:</strong></td><td style="padding: 10px;">${booking.seatNumbers}</td></tr>
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Total:</strong></td><td style="padding: 10px; color: #4A6BFF; font-weight: bold;">$${String.format("%.2f", amount / 100.0)}</td></tr>
                </table>
                <p style="text-align: center; margin-top: 30px; color: #666; font-size: 14px;">
                    Thank you for choosing SkyFlight!
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun invoiceEmail(user: User, invoice: Invoice): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Your Invoice</title>
        </head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">✈️ SkyFlight</h1>
                <p style="margin: 5px 0 0;">Your Invoice is Ready!</p>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Thank you for booking with SkyFlight!</p>
                <p>Your invoice <strong>${invoice.invoiceNumber}</strong> is ready.</p>
                <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Invoice Number:</strong></td><td style="padding: 10px;">${invoice.invoiceNumber}</td></tr>
                    <tr><td style="padding: 10px;"><strong>Date:</strong></td><td style="padding: 10px;">${invoice.issueDate.format(dateFormatter)}</td></tr>
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Total Amount:</strong></td><td style="padding: 10px; color: #4A6BFF; font-weight: bold;">${invoice.currency} ${invoice.totalAmount}</td></tr>
                    <tr><td style="padding: 10px;"><strong>Status:</strong></td><td style="padding: 10px; color: #28a745; font-weight: bold;">${invoice.status}</td></tr>
                </table>
                <div style="text-align: center; margin-top: 20px;">
                    <a href="https://skyflightbooking.ru/api/invoices/${invoice.id}/download" 
                       style="background: #6C38FF; color: white; padding: 12px 30px; text-decoration: none; border-radius: 25px; display: inline-block; margin: 5px; font-weight: bold;">
                        📄 Download Invoice PDF
                    </a>
                </div>
                <hr style="margin: 30px 0; border: 1px solid #eee;">
                <p style="text-align: center; color: #666; font-size: 14px; margin: 0;">
                    Need help? Contact us at <a href="mailto:booking@skyflightbooking.ru" style="color: #4A6BFF;">booking@skyflightbooking.ru</a>
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
                <br>
                <small>SkyFlight • booking@skyflightbooking.ru</small>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun paymentSuccess(user: User, bookingId: Long, amount: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #28a745, #20c997); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">💳 Payment Successful!</h1>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Your payment for booking <strong>#$bookingId</strong> was successful.</p>
                <p style="font-size: 28px; color: #28a745; text-align: center; margin: 20px;">$${String.format("%.2f", amount / 100.0)}</p>
                <p style="text-align: center; margin-top: 30px; color: #666; font-size: 14px;">
                    Thank you for choosing SkyFlight!
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun bookingCancellation(user: User, bookingId: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #dc3545, #c82333); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">❌ Booking Cancelled</h1>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Your booking <strong>#$bookingId</strong> has been cancelled.</p>
                <p>If you have any questions, please contact support.</p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun thankYouAfterFlight(user: User, bookingId: Long, flight: Flight): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #28a745, #20c997); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">✨ Thank You, ${user.name ?: "Traveler"}!</h1>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <p>Your flight from <strong>${flight.departureCity}</strong> to <strong>${flight.arrivalCity}</strong> is complete.</p>
                <p>We hope you had a great journey with SkyFlight!</p>
                <p>Use code <strong style="font-size: 20px; color: #4A6BFF;">THANKS10</strong> for 10% off your next booking.</p>
                <p style="text-align: center; margin-top: 30px; color: #666; font-size: 14px;">
                    We look forward to seeing you again!
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun reminder(user: User, bookingId: Long, flight: Flight): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #FF9800, #E65100); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">⏰ Flight Tomorrow!</h1>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Your flight is <strong>tomorrow</strong>!</p>
                <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>From:</strong></td><td style="padding: 10px;">${flight.departureCity}</td></tr>
                    <tr><td style="padding: 10px;"><strong>To:</strong></td><td style="padding: 10px;">${flight.arrivalCity}</td></tr>
                    <tr><td style="padding: 10px; background: #f5f7fa;"><strong>Time:</strong></td><td style="padding: 10px;">${flight.departureTime}</td></tr>
                </table>
                <p>Please arrive 2 hours before departure.</p>
                <p style="text-align: center; margin-top: 30px; color: #666; font-size: 14px;">
                    Have a safe flight! ✈️
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun welcomeEmail(user: User): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f7fa;">
            <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white; border-radius: 16px 16px 0 0;">
                <h1 style="margin: 0;">✈️ Welcome to SkyFlight!</h1>
            </div>
            <div style="background: white; padding: 30px; border-radius: 0 0 16px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
                <h2 style="color: #333; margin-top: 0;">Hello ${user.name ?: user.email}!</h2>
                <p>Thank you for joining SkyFlight!</p>
                <p>You can now:</p>
                <ul>
                    <li>Search and book flights</li>
                    <li>View your booking history</li>
                    <li>Receive exclusive offers</li>
                </ul>
                <p style="text-align: center; margin-top: 30px; color: #666; font-size: 14px;">
                    Start your journey with us today!
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666; background: #f5f7fa; border-radius: 0 0 16px 16px; margin-top: -4px;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru" style="color: #4A6BFF;">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()
}