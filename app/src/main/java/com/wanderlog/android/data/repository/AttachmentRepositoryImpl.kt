package com.wanderlog.android.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.wanderlog.android.data.local.dao.AttachmentDao
import com.wanderlog.android.data.local.dao.ItineraryItemAttachmentLinkDao
import com.wanderlog.android.data.local.entity.AttachmentEntity
import com.wanderlog.android.data.sync.SyncMetadataStamp
import com.wanderlog.android.domain.model.Attachment
import com.wanderlog.android.domain.model.normalizeAttachmentTags
import com.wanderlog.android.domain.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class AttachmentRepositoryImpl @Inject constructor(
    private val dao: AttachmentDao,
    private val linkDao: ItineraryItemAttachmentLinkDao,
    private val syncMetadataStamp: SyncMetadataStamp,
    @ApplicationContext private val context: Context
) : AttachmentRepository {

    override fun getAttachmentsForTrip(tripId: String): Flow<List<Attachment>> =
        dao.getAttachmentsForTrip(tripId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Attachment? =
        dao.getById(id)?.toDomain()

    override suspend fun importFromUri(tripId: String, uri: Uri, label: String?, tags: List<String>): Attachment =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = resolveDisplayName(uri, mimeType)

            val dir = File(context.filesDir, "attachments/$tripId").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(dir, "${id}_$safeName")

            resolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }

            val relativePath = "attachments/$tripId/${target.name}"
            val now = syncMetadataStamp.now()
            val deviceId = syncMetadataStamp.currentDeviceId()
            val attachment = Attachment(
                id = id,
                tripId = tripId,
                displayName = displayName,
                mimeType = mimeType,
                localPath = relativePath,
                label = label,
                tags = normalizeAttachmentTags(tags),
                sizeBytes = target.length(),
                createdAt = now
            )
            dao.insert(
                AttachmentEntity.fromDomain(
                    attachment = attachment,
                    updatedAt = now,
                    lastModifiedByDeviceId = deviceId
                )
            )
            attachment
        }

    override suspend fun update(attachment: Attachment) = withContext(Dispatchers.IO) {
        val existing = dao.getByIdIncludingDeleted(attachment.id) ?: return@withContext
        val now = maxOf(syncMetadataStamp.now(), existing.updatedAt + 1)
        val deviceId = syncMetadataStamp.currentDeviceId()
        dao.update(
            AttachmentEntity.fromDomain(
                attachment = attachment.copy(
                    localPath = existing.localPath,
                    sizeBytes = existing.sizeBytes,
                    createdAt = existing.createdAt,
                    tags = normalizeAttachmentTags(attachment.tags)
                ),
                updatedAt = now,
                deletedAt = existing.deletedAt,
                lastModifiedByDeviceId = deviceId
            )
        )
    }

    override suspend fun delete(attachment: Attachment) = withContext(Dispatchers.IO) {
        val now = syncMetadataStamp.now()
        val deviceId = syncMetadataStamp.currentDeviceId()
        getFile(attachment).delete()
        linkDao.markDeletedForAttachment(
            attachmentId = attachment.id,
            deletedAt = now,
            lastModifiedByDeviceId = deviceId
        )
        dao.markDeleted(
            attachmentId = attachment.id,
            deletedAt = now,
            lastModifiedByDeviceId = deviceId
        )
    }

    override suspend fun readText(attachment: Attachment): String = withContext(Dispatchers.IO) {
        getFile(attachment).readText()
    }

    override fun getFile(attachment: Attachment): File =
        File(context.filesDir, attachment.localPath)

    private fun resolveDisplayName(uri: Uri, mimeType: String): String {
        val providerName = queryDisplayName(uri)?.cleanDisplayName()
        val pathCandidate = uri.lastPathSegment
            ?.let(Uri::decode)
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.cleanDisplayName()

        val preferredName = when {
            providerName.isUsefulImportedName() -> providerName
            pathCandidate.isUsefulImportedName() -> pathCandidate
            providerName != null -> providerName
            pathCandidate != null -> pathCandidate
            else -> defaultNameFor(mimeType)
        } ?: defaultNameFor(mimeType)

        return preferredName.withInferredExtension(mimeType)
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return it.getString(idx)
        }
    }

    private fun defaultNameFor(mimeType: String): String = when {
        mimeType.startsWith("text/markdown") -> "note.md"
        mimeType.startsWith("text/") -> "note.txt"
        mimeType == "application/pdf" -> "document.pdf"
        mimeType.startsWith("image/jpeg") -> "image.jpg"
        mimeType.startsWith("image/png") -> "image.png"
        mimeType.startsWith("image/") -> "image"
        else -> "attachment"
    }
}

private fun String.cleanDisplayName(): String? =
    trim()
        .takeIf { it.isNotBlank() }
        ?.replace(Regex("[\\r\\n]+"), " ")

private fun String?.isUsefulImportedName(): Boolean {
    val value = this ?: return false
    val normalized = value.lowercase()
    if (normalized in setOf("download", "downloads", "document", "attachment", "file")) {
        return false
    }
    if (normalized.matches(Regex("[0-9]+"))) {
        return false
    }
    return true
}

private fun String.withInferredExtension(mimeType: String): String {
    if (contains('.')) {
        return this
    }

    val extension = when {
        mimeType == "text/markdown" -> "md"
        mimeType == "application/pdf" -> "pdf"
        mimeType == "image/jpeg" -> "jpg"
        mimeType == "image/png" -> "png"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    } ?: return this

    return "$this.$extension"
}
