package com.github.codeworkscreativehub.mlauncher.mooncat.backup

import com.github.codeworkscreativehub.mlauncher.mooncat.model.ItemType
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LauncherItem
import com.github.codeworkscreativehub.mlauncher.mooncat.model.LayoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val password = "luna creciente".toCharArray()
    private val folder = LauncherItem("f", "Juegos", ItemType.FOLDER, "home", position = 0)
    private val app = LauncherItem("a", "Reloj", ItemType.APPLICATION, "home", "f", 0, "com.clock", "com.clock.Main", 0)

    private val payload = BackupPayload(
        createdAtMillis = 1_700_000_000_000,
        appVersion = "0.2.0",
        preferences = listOf(
            PrefEntry.of("text", "hola")!!,
            PrefEntry.of("flag", true)!!,
            PrefEntry.of("count", 7)!!,
            PrefEntry.of("big", 7L)!!,
            PrefEntry.of("scale", 1.25f)!!,
            PrefEntry.of("hidden", setOf("b", "a"))!!
        ),
        layout = LayoutSnapshot(items = listOf(folder, app))
    )

    private fun expectFailure(reason: BackupException.Reason, block: () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (error: BackupException) {
            assertEquals(reason, error.reason)
        }
    }

    @Test fun roundTripKeepsTypesAndLayout() {
        val restored = BackupCodec.decrypt(BackupCodec.encrypt(payload, password.copyOf()), password.copyOf())
        assertEquals(payload, restored)
        val values = restored.preferences.associate { it.key to it.decoded() }
        assertEquals(7, values["count"])
        assertEquals(7L, values["big"])
        assertEquals(1.25f, values["scale"])
        assertEquals(true, values["flag"])
        assertEquals(setOf("a", "b"), values["hidden"])
    }

    @Test fun outputIsEncryptedAndSalted() {
        val first = BackupCodec.encrypt(payload, password.copyOf())
        val second = BackupCodec.encrypt(payload, password.copyOf())
        assertFalse(String(first, Charsets.ISO_8859_1).contains("Juegos"))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("hola"))
        assertNotEquals(first.toList(), second.toList())
    }

    @Test fun wrongPasswordIsRejected() {
        val file = BackupCodec.encrypt(payload, password.copyOf())
        expectFailure(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) {
            BackupCodec.decrypt(file, "luna menguante".toCharArray())
        }
    }

    @Test fun tamperedHeaderOrBodyIsRejected() {
        val file = BackupCodec.encrypt(payload, password.copyOf())
        for (index in listOf(10, file.size / 2, file.size - 1)) {
            val damaged = file.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            expectFailure(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) { BackupCodec.decrypt(damaged, password.copyOf()) }
        }
        expectFailure(BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED) {
            BackupCodec.decrypt(file.copyOf(file.size - 3), password.copyOf())
        }
    }

    @Test fun foreignOrUnsupportedFilesAreRejected() {
        expectFailure(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decrypt("{\"a\":1}".toByteArray(), password) }
        expectFailure(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decrypt(ByteArray(0), password) }
        val file = BackupCodec.encrypt(payload, password.copyOf())
        expectFailure(BackupException.Reason.UNSUPPORTED_VERSION) {
            BackupCodec.decrypt(file.copyOf().also { it[4] = 9 }, password)
        }
        expectFailure(BackupException.Reason.NOT_A_BACKUP) {
            BackupCodec.decrypt(file.copyOf().also { it[5] = 0x7f }, password)
        }
        expectFailure(BackupException.Reason.TOO_LARGE) {
            BackupCodec.decrypt(ByteArray(BackupCodec.MAX_FILE_BYTES + 1), password)
        }
    }

    @Test fun weakPasswordAndInvalidContentAreNotWritten() {
        expectFailure(BackupException.Reason.WEAK_PASSWORD) { BackupCodec.encrypt(payload, "short".toCharArray()) }
        val corrupt = payload.copy(layout = LayoutSnapshot(items = listOf(app)))
        expectFailure(BackupException.Reason.INVALID_CONTENT) { BackupCodec.encrypt(corrupt, password.copyOf()) }
        val duplicate = payload.copy(preferences = payload.preferences + PrefEntry.of("text", "otra")!!)
        expectFailure(BackupException.Reason.INVALID_CONTENT) { BackupCodec.encrypt(duplicate, password.copyOf()) }
    }

    @Test fun entryValidationRejectsMismatchedTypes() {
        assertTrue(PrefEntry("k", PrefEntry.INT, "12").isValid())
        assertFalse(PrefEntry("k", PrefEntry.INT, "1.5").isValid())
        assertFalse(PrefEntry("k", PrefEntry.BOOLEAN, "yes").isValid())
        assertFalse(PrefEntry("k", PrefEntry.FLOAT, "NaN").isValid())
        assertFalse(PrefEntry("k", PrefEntry.STRING_SET, values = listOf("a", "a")).isValid())
        assertFalse(PrefEntry("k", "object", "{}").isValid())
        assertFalse(PrefEntry("", PrefEntry.STRING, "x").isValid())
    }
}
