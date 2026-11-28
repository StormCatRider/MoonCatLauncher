package com.github.codeworkscreativehub.mlauncher.mooncat.backup

import com.github.codeworkscreativehub.mlauncher.mooncat.model.LayoutSnapshot
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** One SharedPreferences value with its original type, so restore never guesses types. */
@JsonClass(generateAdapter = true)
data class PrefEntry(
    val key: String,
    val type: String,
    val value: String? = null,
    val values: List<String>? = null
) {
    fun isValid(): Boolean {
        if (key.isBlank() || key.length > MAX_KEY_LENGTH) return false
        return when (type) {
            STRING -> value != null && value.length <= MAX_VALUE_LENGTH && values == null
            STRING_SET -> value == null && values != null && values.size <= MAX_SET_SIZE &&
                values.all { it.length <= MAX_VALUE_LENGTH } && values.distinct().size == values.size
            BOOLEAN -> values == null && (value == "true" || value == "false")
            INT -> values == null && value?.toIntOrNull() != null
            LONG -> values == null && value?.toLongOrNull() != null
            FLOAT -> values == null && value?.toFloatOrNull()?.isFinite() == true
            else -> false
        }
    }

    fun decoded(): Any = when (type) {
        STRING -> value!!
        STRING_SET -> values!!.toSet()
        BOOLEAN -> value == "true"
        INT -> value!!.toInt()
        LONG -> value!!.toLong()
        FLOAT -> value!!.toFloat()
        else -> throw IllegalStateException("Unknown type")
    }

    companion object {
        const val STRING = "string"
        const val STRING_SET = "string_set"
        const val BOOLEAN = "boolean"
        const val INT = "int"
        const val LONG = "long"
        const val FLOAT = "float"
        private const val MAX_KEY_LENGTH = 512
        private const val MAX_VALUE_LENGTH = 1_000_000
        private const val MAX_SET_SIZE = 10_000

        /** Returns null for types SharedPreferences cannot hold. */
        fun of(key: String, raw: Any?): PrefEntry? = when (raw) {
            is String -> PrefEntry(key, STRING, raw)
            is Boolean -> PrefEntry(key, BOOLEAN, raw.toString())
            is Int -> PrefEntry(key, INT, raw.toString())
            is Long -> PrefEntry(key, LONG, raw.toString())
            is Float -> PrefEntry(key, FLOAT, raw.toString())
            is Set<*> -> PrefEntry(key, STRING_SET, values = raw.filterIsInstance<String>().sorted())
            else -> null
        }
    }
}

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAtMillis: Long,
    val appVersion: String,
    val preferences: List<PrefEntry>,
    val layout: LayoutSnapshot?
) {
    fun isValid(): Boolean =
        schemaVersion == SCHEMA_VERSION &&
            createdAtMillis >= 0 &&
            appVersion.length <= 64 &&
            preferences.size <= MAX_PREFERENCES &&
            preferences.map { it.key }.distinct().size == preferences.size &&
            preferences.all { it.isValid() } &&
            (layout == null || layout.isValid())

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_PREFERENCES = 20_000
    }
}

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, UNSUPPORTED_VERSION, WRONG_PASSWORD_OR_DAMAGED, INVALID_CONTENT, TOO_LARGE, WEAK_PASSWORD }
}

/**
 * Encrypted backup file format, version 1:
 *
 *  magic "MCLB" | version (1 byte) | iterations (int32) | salt (16) | nonce (12) | AES-256-GCM ciphertext+tag
 *
 * The key is derived with PBKDF2-HMAC-SHA256. The header is authenticated as AAD,
 * so changing the iteration count, salt or version makes decryption fail.
 */
object BackupCodec {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'C'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_BITS = 128
    private const val HEADER_LENGTH = 4 + 1 + 4 + SALT_LENGTH + NONCE_LENGTH
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_FILE_BYTES = 8 * 1024 * 1024

    private val adapter = Moshi.Builder().build().adapter(BackupPayload::class.java)
    private val random = SecureRandom()

    fun encrypt(payload: BackupPayload, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        if (password.size < MIN_PASSWORD_LENGTH) throw BackupException(BackupException.Reason.WEAK_PASSWORD)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        if (!payload.isValid()) throw BackupException(BackupException.Reason.INVALID_CONTENT)
        val plain = adapter.toJson(payload).toByteArray(Charsets.UTF_8)
        if (plain.size > MAX_FILE_BYTES - HEADER_LENGTH - TAG_BITS / 8) {
            throw BackupException(BackupException.Reason.TOO_LARGE)
        }
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(nonce).array()
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plain)
        } finally {
            Arrays.fill(key, 0)
            Arrays.fill(plain, 0)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray): BackupPayload {
        if (file.size > MAX_FILE_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
        if (file.size < HEADER_LENGTH + TAG_BITS / 8 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
        val buffer = ByteBuffer.wrap(file)
        buffer.position(4)
        if (buffer.get() != VERSION) throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION)
        val iterations = buffer.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val salt = ByteArray(SALT_LENGTH).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_LENGTH).also { buffer.get(it) }
        val key = deriveKey(password, salt, iterations)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file, 0, HEADER_LENGTH)
            cipher.doFinal(file, HEADER_LENGTH, file.size - HEADER_LENGTH)
        } catch (error: GeneralSecurityException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED, error)
        } finally {
            Arrays.fill(key, 0)
        }
        try {
            val payload = try {
                adapter.fromJson(String(plain, Charsets.UTF_8))
            } catch (error: Exception) {
                throw BackupException(BackupException.Reason.INVALID_CONTENT, error)
            }
            if (payload == null || !payload.isValid()) throw BackupException(BackupException.Reason.INVALID_CONTENT)
            return payload
        } finally {
            Arrays.fill(plain, 0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
