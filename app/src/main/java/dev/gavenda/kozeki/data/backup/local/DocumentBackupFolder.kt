package dev.gavenda.kozeki.data.backup.local

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException

/** A [BackupFolder] picked with the system's folder picker, reached through its documents provider. */
class DocumentBackupFolder(private val resolver: ContentResolver) : BackupFolder {

    override fun keepAccess(folder: String) {
        resolver.takePersistableUriPermission(Uri.parse(folder), ACCESS)
    }

    override fun releaseAccess(folder: String) {
        resolver.releasePersistableUriPermission(Uri.parse(folder), ACCESS)
    }

    override fun name(folder: String): String? {
        val tree = Uri.parse(folder)
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission }) return null
        return resolver.query(document(tree), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    override fun write(folder: String, fileName: String, content: ByteArray) {
        // Any other type has the provider put an extension of its own on the name.
        val file = DocumentsContract.createDocument(resolver, document(Uri.parse(folder)), "application/octet-stream", fileName)
            ?: throw FileNotFoundException("Cannot create $fileName")
        try {
            resolver.openOutputStream(file, "wt")?.use { it.write(content) } ?: throw FileNotFoundException("Cannot open $fileName")
        } catch (e: Exception) {
            // Half a backup would look like one to restore.
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }
    }

    override fun list(folder: String): List<FolderFile> {
        val tree = Uri.parse(folder)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        return resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    add(FolderFile(uri.toString(), cursor.getString(1).orEmpty()))
                }
            }
        }.orEmpty()
    }

    override fun delete(file: String) {
        DocumentsContract.deleteDocument(resolver, Uri.parse(file))
    }

    override fun read(file: String): ByteArray =
        resolver.openInputStream(Uri.parse(file))?.use { it.readBytes() } ?: throw FileNotFoundException(file)

    private fun document(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private companion object {
        const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
