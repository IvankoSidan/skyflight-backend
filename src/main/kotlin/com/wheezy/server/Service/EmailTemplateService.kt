package com.wheezy.server.Service

import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Flight
import com.wheezy.server.Models.Invoice
import com.wheezy.server.Models.User
import org.springframework.stereotype.Service

@Service
class EmailTemplateService {

    fun bookingConfirmation(user: User, booking: Booking, flight: Flight, amount: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white;">
                <h1 style="margin: 0;">✈️ SkyFlight</h1>
                <p>Booking Confirmed!</p>
            </div>
            <div style="padding: 30px;">
                <h2>Hello ${user.name ?: user.email}!</h2>
                <p>Your booking <strong>#${booking.id}</strong> is confirmed.</p>
                <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Flight:</strong></td><td style="padding: 8px;">${flight.departureCity} → ${flight.arrivalCity}</td></tr>
                    <tr><td style="padding: 8px;"><strong>Date:</strong></td><td style="padding: 8px;">${flight.flightDate}</td></tr>
                    <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Time:</strong></td><td style="padding: 8px;">${flight.departureTime}</td></tr>
                    <tr><td style="padding: 8px;"><strong>Seats:</strong></td><td style="padding: 8px;">${booking.seatNumbers}</td></tr>
                    <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Total:</strong></td><td style="padding: 8px; color: #4A6BFF; font-weight: bold;">$${String.format("%.2f", amount / 100.0)}</td></tr>
                </table>
                <p style="text-align: center; margin-top: 30px;">
                    <a href="https://skyflightbooking.ru/my-bookings" style="background: #4A6BFF; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">View My Bookings</a>
                </p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
                © 2026 SkyFlight | <a href="https://skyflightbooking.ru">skyflightbooking.ru</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun paymentSuccess(user: User, bookingId: Long, amount: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #4CAF50, #2E7D32); padding: 30px; text-align: center; color: white;">
                <h1>💳 Payment Successful!</h1>
            </div>
            <div style="padding: 30px;">
                <h2>Hello ${user.name ?: user.email}!</h2>
                <p>Your payment for booking <strong>#$bookingId</strong> was successful.</p>
                <p style="font-size: 24px; color: #4CAF50; text-align: center; margin: 20px;">$${String.format("%.2f", amount / 100.0)}</p>
                <p>Thank you for choosing SkyFlight!</p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
                © 2026 SkyFlight
            </div>
        </body>
        </html>
    """.trimIndent()

    fun invoiceEmail(user: User, invoice: Invoice): String = """
    <!DOCTYPE html>
    <html>
    <head><meta charset="UTF-8"></head>
    <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white;">
            <h1>📄 Your Invoice is Ready!</h1>
        </div>
        <div style="padding: 30px;">
            <h2>Hello ${user.name ?: user.email}!</h2>
            <p>Thank you for booking with SkyFlight!</p>
            <p>Your invoice <strong>${invoice.invoiceNumber}</strong> is attached to this email.</p>
            <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Invoice Number:</strong></td><td style="padding: 8px;">${invoice.invoiceNumber}</td></tr>
                <tr><td style="padding: 8px;"><strong>Date:</strong></td><td style="padding: 8px;">${invoice.issueDate}</td></tr>
                <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Total Amount:</strong></td><td style="padding: 8px; color: #4A6BFF; font-weight: bold;">${invoice.currency} ${invoice.totalAmount}</td></tr>
                <tr><td style="padding: 8px;"><strong>Status:</strong></td><td style="padding: 8px;">${invoice.status}</td></tr>
            </table>
            <p style="text-align: center; margin-top: 30px;">
                <a href="https://skyflightbooking.ru/my-bookings" style="background: #4A6BFF; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">View My Bookings</a>
            </p>
        </div>
        <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
            © 2026 SkyFlight | <a href="https://skyflightbooking.ru">skyflightbooking.ru</a>
        </div>
    </body>
    </html>
""".trimIndent()

    fun bookingCancellation(user: User, bookingId: Long): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #f44336, #c62828); padding: 30px; text-align: center; color: white;">
                <h1>❌ Booking Cancelled</h1>
            </div>
            <div style="padding: 30px;">
                <h2>Hello ${user.name ?: user.email}!</h2>
                <p>Your booking <strong>#$bookingId</strong> has been cancelled.</p>
                <p>If you have any questions, please contact support.</p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
                © 2026 SkyFlight
            </div>
        </body>
        </html>
    """.trimIndent()

    fun thankYouAfterFlight(user: User, bookingId: Long, flight: Flight): String = """
    <!DOCTYPE html>
    <html>
    <head><meta charset="UTF-8"></head>
    <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <div style="background: linear-gradient(135deg, #4CAF50, #2E7D32); padding: 30px; text-align: center; color: white;">
            <h1>✨ Thank You, ${user.name ?: "Traveler"}!</h1>
        </div>
        <div style="padding: 30px;">
            <p>Your flight from <strong>${flight.departureCity}</strong> to <strong>${flight.arrivalCity}</strong> is complete.</p>
            <p>We hope you had a great journey with SkyFlight!</p>
            <p>Use code <strong style="font-size: 20px;">THANKS10</strong> for 10% off your next booking.</p>
            <p style="text-align: center; margin-top: 30px;">
                <a href="https://skyflightbooking.ru" style="background: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Book Again</a>
            </p>
        </div>
    </body>
    </html>
    """.trimIndent()

    fun reminder(user: User, bookingId: Long, flight: Flight): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <div style="background: linear-gradient(135deg, #FF9800, #E65100); padding: 30px; text-align: center; color: white;">
                <h1>⏰ Flight Tomorrow!</h1>
            </div>
            <div style="padding: 30px;">
                <h2>Hello ${user.name ?: user.email}!</h2>
                <p>Your flight is <strong>tomorrow</strong>!</p>
                <table style="width: 100%; margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 8px; background: #f5f5f5;"><strong>From:</strong></td><td style="padding: 8px;">${flight.departureCity}</td></tr>
                    <tr><td style="padding: 8px;"><strong>To:</strong></td><td style="padding: 8px;">${flight.arrivalCity}</td></tr>
                    <tr><td style="padding: 8px; background: #f5f5f5;"><strong>Time:</strong></td><td style="padding: 8px;">${flight.departureTime}</td></tr>
                </table>
                <p>Please arrive 2 hours before departure.</p>
                <p>Have a safe flight! ✈️</p>
            </div>
            <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
                © 2026 SkyFlight
            </div>
        </body>
        </html>
    """.trimIndent()

    fun welcomeEmail(user: User): String = """
    <!DOCTYPE html>
    <html>
    <head><meta charset="UTF-8"></head>
    <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <div style="background: linear-gradient(135deg, #4A6BFF, #6C38FF); padding: 30px; text-align: center; color: white;">
            <h1>✈️ Welcome to SkyFlight!</h1>
        </div>
        <div style="padding: 30px;">
            <h2>Hello ${user.name ?: user.email}!</h2>
            <p>Thank you for joining SkyFlight!</p>
            <p>You can now:</p>
            <ul>
                <li>Search and book flights</li>
                <li>View your booking history</li>
                <li>Receive exclusive offers</li>
            </ul>
            <p>Start your first search now!</p>
        </div>
        <div style="text-align: center; padding: 20px; font-size: 12px; color: #666;">
            © 2026 SkyFlight
        </div>
    </body>
    </html>
""".trimIndent()
}
