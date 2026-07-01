package com.wheezy.server.Exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.wheezy.server.DTO.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.context.request.WebRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import java.time.LocalDateTime

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Resource not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    code = "NOT_FOUND",
                    message = ex.message ?: "Resource not found",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(ex: RuntimeException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    code = "BAD_REQUEST",
                    message = ex.message ?: "Invalid request",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationError(ex: AuthenticationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Authentication error: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    code = "AUTH_001",
                    message = "Invalid credentials. Please login again.",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Access denied: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    code = "AUTH_004",
                    message = "You don't have permission to perform this action.",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: $errors")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    code = "VALIDATION_001",
                    message = "Validation failed: $errors",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Constraint violation: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    code = "VALIDATION_002",
                    message = ex.message ?: "Validation failed",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Data integrity violation: ${ex.message}")
        val message = when {
            ex.message?.contains("unique constraint") == true -> "Duplicate entry. The value already exists."
            ex.message?.contains("foreign key") == true -> "Referenced record not found."
            ex.message?.contains("not null") == true -> "Required field is missing."
            else -> "Data integrity error. Please try again."
        }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    code = "DB_001",
                    message = message,
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Method not supported: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(
                ErrorResponse(
                    code = "HTTP_405",
                    message = "HTTP method not supported. Please use ${ex.supportedMethods?.joinToString()}",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleMediaTypeNotAcceptable(ex: HttpMediaTypeNotAcceptableException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Media type not acceptable: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_ACCEPTABLE)
            .body(
                ErrorResponse(
                    code = "HTTP_406",
                    message = "Media type not acceptable. Please check Accept header.",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentError(ex: PaymentException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Payment error: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.PAYMENT_REQUIRED)
            .body(
                ErrorResponse(
                    code = "PAYMENT_001",
                    message = ex.message ?: "Payment failed. Please try again.",
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGenericError(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error: ${ex.message}", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    code = "SERVER_001",
                    message = "Something went wrong. Please try again later.",
                    details = if (isDevelopment()) ex.message else null,
                    path = request.getDescription(false),
                    timestamp = LocalDateTime.now()
                )
            )
    }

    private fun isDevelopment(): Boolean {
        return System.getenv("SPRING_PROFILES_ACTIVE") == "dev" ||
                System.getProperty("spring.profiles.active") == "dev"
    }
}