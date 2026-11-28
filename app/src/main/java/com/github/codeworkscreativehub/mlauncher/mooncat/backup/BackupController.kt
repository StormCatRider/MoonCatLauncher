package com.github.codeworkscreativehub.mlauncher.mooncat.backup

import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

/** Encrypted manual backup and validated restore. Must be created during Activity.onCreate. */
class BackupController(private val activity: AppCompatActivity) {
    private val repository = BackupRepository(activity)
    private var busy = false

    private val createDocument: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri ->
            if (uri != null) askNewPassword(uri)
        }

    private val openDocument: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) askRestorePassword(uri)
        }

    fun startBackup() {
        if (busy) return
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        createDocument.launch("mooncat-backup-$date.mcbackup")
    }

    fun startRestore() {
        if (busy) return
        openDocument.launch(arrayOf("*/*"))
    }

    // Single-line mode must be set before the password type: setSingleLine() replaces the
    // transformation method and would otherwise show the password in clear text.
    private fun passwordField(hint: Int) = EditText(activity).apply {
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        transformationMethod = PasswordTransformationMethod.getInstance()
        typeface = Typeface.DEFAULT
        setHint(hint)
    }

    // Edge-to-edge windows may keep the keyboard over the dialog buttons, so the keyboard's
    // Done key on the last field confirms the dialog as well.
    private fun AlertDialog.confirmFromKeyboard(last: EditText) = apply {
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        last.imeOptions = EditorInfo.IME_ACTION_DONE
        last.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
            true
        }
    }

    private fun showProgress(): AlertDialog {
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(ProgressBar(activity))
            addView(TextView(activity).apply {
                text = getLocalizedString(R.string.mooncat_backup_working)
                setPadding(padding, 0, 0, 0)
            })
        }
        return MaterialAlertDialogBuilder(activity).setView(content).setCancelable(false).show()
    }

    private fun showResult(message: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        MaterialAlertDialogBuilder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun askNewPassword(uri: Uri) {
        val first = passwordField(R.string.mooncat_backup_password)
        val second = passwordField(R.string.mooncat_backup_password_confirm)
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(first)
            addView(second)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mooncat_backup_create)
            .setMessage(R.string.mooncat_backup_password_warning)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel) { _, _ -> deleteQuietly(uri) }
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(false)
            .show()
            .confirmFromKeyboard(second)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = first.text.toCharArray()
            val confirmation = second.text.toCharArray()
            when {
                password.size < BackupCodec.MIN_PASSWORD_LENGTH -> {
                    first.error = getLocalizedString(R.string.mooncat_backup_password_short)
                        .format(BackupCodec.MIN_PASSWORD_LENGTH)
                    Arrays.fill(password, '\u0000')
                }
                !password.contentEquals(confirmation) -> {
                    second.error = getLocalizedString(R.string.mooncat_backup_password_mismatch)
                    Arrays.fill(password, '\u0000')
                }
                else -> {
                    dialog.dismiss()
                    first.text.clear()
                    second.text.clear()
                    writeBackup(uri, password)
                }
            }
            Arrays.fill(confirmation, '\u0000')
        }
    }

    private fun writeBackup(uri: Uri, password: CharArray) {
        busy = true
        val progress = showProgress()
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val encrypted = BackupCodec.encrypt(repository.collect(), password)
                    val stream = activity.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("No output stream")
                    stream.use { it.write(encrypted) }
                }
                progress.dismiss()
                showResult(R.string.mooncat_backup_created)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                progress.dismiss()
                deleteQuietly(uri)
                showResult(R.string.mooncat_backup_create_failed)
            } finally {
                Arrays.fill(password, '\u0000')
                busy = false
            }
        }
    }

    private fun askRestorePassword(uri: Uri) {
        val field = passwordField(R.string.mooncat_backup_password)
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            setPadding(padding, 0, padding, 0)
            addView(field, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mooncat_backup_restore)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = field.text.toCharArray()
                field.text.clear()
                readBackup(uri, password)
            }
            .show()
            .confirmFromKeyboard(field)
    }

    private fun readBackup(uri: Uri, password: CharArray) {
        busy = true
        val progress = showProgress()
        activity.lifecycleScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    BackupCodec.decrypt(readLimited(uri), password)
                }
                progress.dismiss()
                confirmRestore(payload)
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackupException) {
                progress.dismiss()
                showResult(messageFor(error.reason))
            } catch (_: Exception) {
                progress.dismiss()
                showResult(R.string.mooncat_backup_restore_failed)
            } finally {
                Arrays.fill(password, '\u0000')
                busy = false
            }
        }
    }

    private fun confirmRestore(payload: BackupPayload) {
        val created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(payload.createdAtMillis))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mooncat_backup_restore)
            .setMessage(getLocalizedString(R.string.mooncat_backup_restore_confirm).format(created, payload.appVersion))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.mooncat_backup_restore_apply) { _, _ -> applyBackup(payload) }
            .show()
    }

    private fun applyBackup(payload: BackupPayload) {
        busy = true
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.apply(payload) }
                AppReloader.restartApp(activity)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showResult(R.string.mooncat_backup_restore_failed)
            } finally {
                busy = false
            }
        }
    }

    private fun readLimited(uri: Uri): ByteArray {
        val input = activity.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No input stream")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > BackupCodec.MAX_FILE_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
            }
            return out.toByteArray()
        }
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            android.provider.DocumentsContract.deleteDocument(activity.contentResolver, uri)
        } catch (_: Exception) {
            // The provider may not support deletion; an empty file is left behind.
        }
    }

    private fun messageFor(reason: BackupException.Reason): Int = when (reason) {
        BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED -> R.string.mooncat_backup_wrong_password
        BackupException.Reason.NOT_A_BACKUP, BackupException.Reason.UNSUPPORTED_VERSION -> R.string.mooncat_backup_not_backup
        BackupException.Reason.TOO_LARGE -> R.string.mooncat_backup_too_large
        else -> R.string.mooncat_backup_restore_failed
    }

    // Copies the text without creating an immutable String that would linger in memory.
    private fun android.text.Editable.toCharArray(): CharArray =
        CharArray(length).also { (this as android.text.GetChars).getChars(0, length, it, 0) }

    private companion object {
        const val MIME_TYPE = "application/octet-stream"
    }
}
