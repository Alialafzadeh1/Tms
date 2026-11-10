package com.example.data.validation

import java.util.regex.Pattern

data class ValidationError(
    val field: String,
    val messageFa: String,
    val messageEn: String
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<ValidationError>) : ValidationResult {
        fun firstMessageFa(): String = errors.firstOrNull()?.messageFa ?: "ورودی نامعتبر است"
        fun firstMessageEn(): String = errors.firstOrNull()?.messageEn ?: "Invalid input"
    }
}

object InputValidator {

    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    private val FQDN_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    )

    private val UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )

    private val COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$")

    /**
     * Validates VPN server infrastructure parameters.
     */
    fun validateServer(
        name: String,
        country: String,
        countryCode: String,
        host: String,
        port: Int,
        protocol: String,
        bandwidthLimitGb: Double = 1000.0
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (name.trim().length !in 3..100) {
            errors.add(
                ValidationError(
                    field = "name",
                    messageFa = "نام سرور باید بین ۳ تا ۱۰۰ کاراکتر باشد",
                    messageEn = "Server name must be between 3 and 100 characters"
                )
            )
        }

        if (country.trim().length !in 2..50) {
            errors.add(
                ValidationError(
                    field = "country",
                    messageFa = "نام کشور نامعتبر است (حداقل ۲ کاراکتر)",
                    messageEn = "Country name must be at least 2 characters"
                )
            )
        }

        val codeUpper = countryCode.trim().uppercase()
        if (!COUNTRY_CODE_PATTERN.matcher(codeUpper).matches()) {
            errors.add(
                ValidationError(
                    field = "countryCode",
                    messageFa = "کد کشور باید دقیقاً ۲ حرف انگلیسی باشد (مانند DE یا FI)",
                    messageEn = "Country code must be exactly 2 uppercase letters (e.g., DE, FI)"
                )
            )
        }

        val trimmedHost = host.trim()
        val isIp = IPV4_PATTERN.matcher(trimmedHost).matches() || trimmedHost.contains(":")
        val isDomain = FQDN_PATTERN.matcher(trimmedHost).matches()
        if (!isIp && !isDomain && trimmedHost != "localhost") {
            errors.add(
                ValidationError(
                    field = "host",
                    messageFa = "آدرس هاست باید یک IP معتبر یا دامنه اینترنتی استاندارد باشد",
                    messageEn = "Host must be a valid IPv4 address or FQDN domain"
                )
            )
        }

        if (port !in 1..65535) {
            errors.add(
                ValidationError(
                    field = "port",
                    messageFa = "پورت سرور باید عددی بین ۱ تا ۶۵۵۳۵ باشد",
                    messageEn = "Port must be in valid range 1-65535"
                )
            )
        }

        if (protocol.trim().length !in 2..32) {
            errors.add(
                ValidationError(
                    field = "protocol",
                    messageFa = "پروتکل سرور نامعتبر است",
                    messageEn = "Server protocol is invalid"
                )
            )
        }

        if (bandwidthLimitGb <= 0 || bandwidthLimitGb > 100_000) {
            errors.add(
                ValidationError(
                    field = "bandwidthLimitGb",
                    messageFa = "محدودیت ترافیک باید بین ۱ تا ۱۰۰،۰۰۰ گیگابایت باشد",
                    messageEn = "Bandwidth limit must be between 1 and 100,000 GB"
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Validates parsed or user-provided VPN configuration profile attributes.
     */
    fun validateVpnConfig(
        protocol: String,
        server: String,
        port: Int,
        uuidOrPassword: String,
        security: String,
        transport: String,
        rawConfig: String
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        val protoLower = protocol.trim().lowercase()
        val validProtocols = setOf("vless", "vmess", "trojan", "shadowsocks")
        if (protoLower !in validProtocols) {
            errors.add(
                ValidationError(
                    field = "protocol",
                    messageFa = "پروتکل نامعتبر است. تنها VLESS, VMess, Trojan و Shadowsocks مجاز هستند",
                    messageEn = "Invalid protocol. Supported: VLESS, VMess, Trojan, Shadowsocks"
                )
            )
        }

        val trimmedHost = server.trim()
        val isIp = IPV4_PATTERN.matcher(trimmedHost).matches() || trimmedHost.contains(":")
        val isDomain = FQDN_PATTERN.matcher(trimmedHost).matches()
        if (!isIp && !isDomain && trimmedHost != "localhost") {
            errors.add(
                ValidationError(
                    field = "server",
                    messageFa = "آدرس سرور کانفیگ باید IP یا دامنه معتبر باشد",
                    messageEn = "Config server address must be a valid IP or domain"
                )
            )
        }

        if (port !in 1..65535) {
            errors.add(
                ValidationError(
                    field = "port",
                    messageFa = "پورت کانفیگ باید در بازه ۱ تا ۶۵۵۳۵ باشد",
                    messageEn = "Config port must be between 1 and 65535"
                )
            )
        }

        if (protoLower == "vless" || protoLower == "vmess") {
            if (!UUID_PATTERN.matcher(uuidOrPassword.trim()).matches()) {
                errors.add(
                    ValidationError(
                        field = "uuidOrPassword",
                        messageFa = "شناسه کاربر در VLESS/VMess باید استاندارد UUID (۳۶ کاراکتر با خط تیره) باشد",
                        messageEn = "VLESS/VMess User ID must be a standard 36-character UUID"
                    )
                )
            }
        } else {
            if (uuidOrPassword.trim().length < 3) {
                errors.add(
                    ValidationError(
                        field = "uuidOrPassword",
                        messageFa = "رمز عبور کانفیگ باید حداقل ۳ کاراکتر باشد",
                        messageEn = "Password must be at least 3 characters"
                    )
                )
            }
        }

        val secLower = security.trim().lowercase()
        if (secLower !in setOf("tls", "reality", "none", "")) {
            errors.add(
                ValidationError(
                    field = "security",
                    messageFa = "لایه امنیتی نامعتبر است (tls, reality, none)",
                    messageEn = "Security layer must be tls, reality, or none"
                )
            )
        }

        val transLower = transport.trim().lowercase()
        if (transLower !in setOf("tcp", "ws", "grpc", "h2", "kcp", "quic", "")) {
            errors.add(
                ValidationError(
                    field = "transport",
                    messageFa = "نوع انتقال (Transport) نامعتبر است (tcp, ws, grpc, h2)",
                    messageEn = "Transport type must be tcp, ws, grpc, or h2"
                )
            )
        }

        if (rawConfig.length > 4096) {
            errors.add(
                ValidationError(
                    field = "rawConfig",
                    messageFa = "طول لینک کانفیگ فراتر از حد مجاز امنیتی است (حداکثر ۴۰۹۶ کاراکتر)",
                    messageEn = "Config payload exceeds maximum length limit"
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Validates Failover Policy configurations.
     */
    fun validateFailoverPolicy(
        name: String,
        primaryServerId: String,
        backupServerId: String,
        thresholdMs: Int
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (name.trim().length !in 3..100) {
            errors.add(
                ValidationError(
                    field = "name",
                    messageFa = "نام سیاست باید بین ۳ تا ۱۰۰ کاراکتر باشد",
                    messageEn = "Policy name must be between 3 and 100 characters"
                )
            )
        }

        if (primaryServerId.isBlank() || backupServerId.isBlank()) {
            errors.add(
                ValidationError(
                    field = "servers",
                    messageFa = "سرور اصلی و سرور پشتیبان باید انتخاب شوند",
                    messageEn = "Both primary and backup servers are required"
                )
            )
        }

        if (primaryServerId == backupServerId && primaryServerId.isNotBlank()) {
            errors.add(
                ValidationError(
                    field = "servers",
                    messageFa = "سرور پشتیبان نمی‌تواند با سرور اصلی یکسان باشد",
                    messageEn = "Primary and backup servers cannot be identical"
                )
            )
        }

        if (thresholdMs !in 50..5000) {
            errors.add(
                ValidationError(
                    field = "thresholdMs",
                    messageFa = "آستانه تاخیر باید بین ۵۰ تا ۵۰۰۰ میلی‌ثانیه باشد",
                    messageEn = "Latency threshold must be between 50 and 5000 ms"
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Validates administrator credentials and settings inputs.
     */
    fun validateAdminPin(pin: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val trimmed = pin.trim()
        if (trimmed.length < 6) {
            errors.add(
                ValidationError(
                    field = "pin",
                    messageFa = "پین امنیتی باید حداقل ۶ رقم یا کاراکتر باشد",
                    messageEn = "Security PIN must be at least 6 digits"
                )
            )
        }
        val trivialPins = setOf("123456", "000000", "111111", "654321", "999999")
        if (trimmed in trivialPins) {
            errors.add(
                ValidationError(
                    field = "pin",
                    messageFa = "پین انتخابی بسیار ساده و قابل پیش‌بینی است",
                    messageEn = "Selected PIN is too simple and predictable"
                )
            )
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
