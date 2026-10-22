package com.mnnu.ereport.lite

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class ExportSession(private val directory: File) : AutoCloseable {
    companion object { const val MAX_BYTES = 300L * 1024 * 1024; const val MAX_CHUNK_BYTES = 192 * 1024 }
    private var active: Transfer? = null
    data class Completed(val file: File, val name: String, val contentType: String)
    private data class Transfer(val id: String, val file: File, val name: String, val type: String,
        val size: Long, val expectedHash: String, val stream: FileOutputStream,
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256"), var written: Long = 0)

    @Synchronized fun begin(name: String, contentType: String, size: Long, sha256: String): String {
        require(active == null) { "已有文件正在保存，请稍候" }
        require(size in 0..MAX_BYTES) { "文件大小超出限制" }
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "缺少文件校验值" }
        require(contentType.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) { "文件类型无效" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建下载目录" }
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.part")
        active = Transfer(id, file, EndpointPolicy.safeFileName(name), contentType, size, sha256, FileOutputStream(file))
        return id
    }

    @Synchronized fun append(id: String, offset: Long, bytes: ByteArray) {
        val transfer = requireTransfer(id)
        require(offset == transfer.written && bytes.isNotEmpty() && bytes.size <= MAX_CHUNK_BYTES
            && transfer.written + bytes.size <= transfer.size) { "文件分片或顺序无效" }
        transfer.stream.write(bytes)
        transfer.digest.update(bytes)
        transfer.written += bytes.size
    }

    @Synchronized fun complete(id: String): Completed {
        val transfer = requireTransfer(id)
        transfer.stream.close()
        active = null
        try {
            require(transfer.written == transfer.size && transfer.digest.digest().joinToString("") { "%02x".format(it) } == transfer.expectedHash) {
                "文件大小或校验值不一致，请重新下载"
            }
            val file = File(directory, "${transfer.id}-${transfer.name}")
            check(transfer.file.renameTo(file)) { "无法保存已校验文件" }
            return Completed(file, transfer.name, transfer.type)
        } finally { transfer.file.delete() }
    }

    @Synchronized override fun close() {
        active?.let { it.stream.close(); it.file.delete() }
        active = null
    }

    private fun requireTransfer(id: String) = active?.takeIf { it.id == id }
        ?: throw IllegalArgumentException("下载会话不存在或已失效")
}
