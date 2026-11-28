package com.github.codeworkscreativehub.mlauncher.mooncat.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.mooncat.drawer.LocalSearch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A file found in an authorized folder. */
data class FoundFile(val tree: Uri, val entry: FileEntry) {
    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
}

/**
 * Searches file names in folders the user chose with Android's folder picker. The launcher holds
 * a read grant for those folders only, needs no storage permission, keeps the index in memory and
 * never reads file contents. Removing a folder releases the grant.
 */
object FileSearchIndex {
    private const val KEY = "MOONCAT_SEARCH_FOLDERS"

    @Volatile private var files: List<FoundFile> = emptyList()
    @Volatile private var indexedAt = 0L
    @Volatile private var indexedFolders: List<Uri> = emptyList()
    @Volatile private var running = false

    /** Folders that are stored and still granted. A restored backup on another phone has no grant. */
    fun folders(context: Context): List<Uri> {
        val granted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        return Prefs(context).prefsNormal.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .filter { it in granted }
            .sortedBy { it.toString() }
    }

    internal fun store(context: Context, folders: List<Uri>) {
        Prefs(context).prefsNormal.edit().putStringSet(KEY, folders.map { it.toString() }.toSet()).apply()
        invalidate()
    }

    fun invalidate() {
        indexedAt = 0L
    }

    /** Rebuilds the index in the background when it is old or the folders changed. */
    fun refreshIfStale(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        val folders = folders(app)
        if (folders.isEmpty()) {
            files = emptyList(); indexedFolders = emptyList(); return
        }
        val fresh = System.currentTimeMillis() - indexedAt < FileIndex.MAX_AGE_MILLIS && folders == indexedFolders
        if (fresh || running) return
        running = true
        scope.launch(Dispatchers.IO) {
            try {
                val found = folders.flatMap { tree -> index(app, tree) }
                files = found
                indexedFolders = folders
                indexedAt = System.currentTimeMillis()
            } finally {
                running = false
            }
        }
    }

    private fun index(context: Context, tree: Uri): List<FoundFile> {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return emptyList()
        val perFolder = FileIndex.MAX_FILES / FileIndex.MAX_FOLDERS
        return FileIndex.walk(rootId, list = { id -> list(context, tree, id) }, maxFiles = perFolder)
            .map { FoundFile(tree, it) }
    }

    private fun list(context: Context, tree: Uri, parentId: String): List<FileEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<FileEntry>()
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val mime = cursor.getString(2).orEmpty()
                result += FileEntry(id, cursor.getString(1).orEmpty(), mime, mime == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return result
    }

    fun search(query: String): List<FoundFile> = LocalSearch.search(query, files) { it.entry.name }

    fun open(context: Context, file: FoundFile) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri, file.entry.mimeType.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.showLongToast(getLocalizedString(R.string.mooncat_files_no_app))
        } catch (_: SecurityException) {
            invalidate()
            context.showLongToast(getLocalizedString(R.string.mooncat_files_unavailable))
        }
    }

    fun folderName(context: Context, tree: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        return runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: docId.substringAfterLast(':').ifBlank { docId }
    }
}

/** Registers the folder picker; it must be created in the activity's onCreate. */
class FileSearchController(private val activity: AppCompatActivity) {
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addFolder(uri)
        showManager()
    }

    private fun addFolder(uri: Uri) {
        try {
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            activity.showLongToast(getLocalizedString(R.string.mooncat_files_unavailable))
            return
        }
        val folders = (FileSearchIndex.folders(activity) + uri).distinct()
        FileSearchIndex.store(activity, folders)
    }

    private fun removeFolder(uri: Uri) {
        runCatching { activity.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        FileSearchIndex.store(activity, FileSearchIndex.folders(activity) - uri)
    }

    fun showManager() {
        val folders = FileSearchIndex.folders(activity)
        val builder = MaterialAlertDialogBuilder(activity).setTitle(R.string.mooncat_files_title)
        if (folders.isEmpty()) {
            builder.setMessage(R.string.mooncat_files_explain)
        } else {
            builder.setItems(folders.map { "▸ " + FileSearchIndex.folderName(activity, it) }.toTypedArray()) { _, which ->
                val folder = folders[which]
                MaterialAlertDialogBuilder(activity)
                    .setMessage(getLocalizedString(R.string.mooncat_files_remove_confirm, FileSearchIndex.folderName(activity, folder)))
                    .setPositiveButton(R.string.mooncat_files_remove) { _, _ -> removeFolder(folder); showManager() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> showManager() }
                    .show()
            }
        }
        if (folders.size < FileIndex.MAX_FOLDERS) {
            builder.setPositiveButton(R.string.mooncat_files_add) { _, _ ->
                try {
                    picker.launch(null)
                } catch (_: ActivityNotFoundException) {
                    activity.showLongToast(getLocalizedString(R.string.mooncat_files_no_picker))
                }
            }
        }
        if (folders.isNotEmpty()) {
            builder.setNeutralButton(R.string.mooncat_files_about) { _, _ ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.mooncat_files_title)
                    .setMessage(R.string.mooncat_files_explain)
                    .setPositiveButton(android.R.string.ok) { _, _ -> showManager() }
                    .show()
            }
        }
        builder.setNegativeButton(R.string.mooncat_prod_close, null).show()
    }
}
