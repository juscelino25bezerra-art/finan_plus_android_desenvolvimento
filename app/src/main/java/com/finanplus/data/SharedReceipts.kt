// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import com.finanplus.core.Ids
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Comprovantes (nota fiscal, recibo) das despesas compartilhadas.
 *
 * Ficam só neste aparelho, cifrados com AES-256-GCM e uma chave própria do Android Keystore
 * (como o arquivo de dados em [SecureStore]); o backup JSON guarda apenas o nome do arquivo.
 * Formato: "FNR1" | tamanho do IV (1 byte) | IV | texto cifrado + tag GCM.
 *
 * Para abrir ou compartilhar, uma cópia decifrada vai para a pasta temporária (cache/shared), que é
 * esvaziada sempre que a tela de despesas compartilhadas abre. O mesmo vale para os PDFs gerados.
 */
object SharedReceipts {
    private const val DIR = "shared-receipts"
    private const val TEMP = "shared"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "finanplus_receipts_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val MAGIC = "FNR1"
    /** 15 MB: uma foto de celular ou um PDF de nota fiscal cabem com folga */
    const val MAX_BYTES = 15 * 1024 * 1024

    class TooBig : Exception()

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }
    private fun tempDir(ctx: Context) = File(ctx.cacheDir, TEMP).apply { mkdirs() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    /** Guarda o arquivo escolhido (cifrado). Retorna (nome, tipo). Lança [TooBig] acima de [MAX_BYTES]. */
    fun save(ctx: Context, uri: Uri): Pair<String, String> {
        val type = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) throw TooBig()
            }
            out.toByteArray()
        }
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(bytes)
        val name = Ids.new()
        File(dir(ctx), name).outputStream().use { o ->
            o.write(MAGIC.toByteArray(Charsets.US_ASCII)); o.write(iv.size); o.write(iv); o.write(ct)
        }
        return name to type
    }

    fun exists(ctx: Context, name: String) = name.isNotEmpty() && File(dir(ctx), name).isFile

    /** Conteúdo decifrado, ou null se o arquivo não está neste aparelho (ex.: backup restaurado em outro). */
    fun read(ctx: Context, name: String): ByteArray? {
        val f = File(dir(ctx), name)
        if (name.isEmpty() || !f.isFile) return null
        val b = f.readBytes()
        if (b.size <= 5 || String(b, 0, 4, Charsets.US_ASCII) != MAGIC) return null
        val ivLen = b[4].toInt()
        if (ivLen !in 12..16 || b.size <= 5 + ivLen) return null
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val k = ks.getKey(ALIAS, null) as? SecretKey ?: return null
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, b.copyOfRange(5, 5 + ivLen)))
        return c.doFinal(b, 5 + ivLen, b.size - 5 - ivLen)
    }

    fun delete(ctx: Context, name: String) { if (name.isNotEmpty()) File(dir(ctx), name).delete() }

    /** Apaga comprovantes que nenhuma despesa usa mais (com mais de 1 dia: um anexo recém-escolhido num formulário fica). */
    fun cleanup(ctx: Context, inUse: Set<String>) {
        val old = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir(ctx).listFiles()?.forEach { if (it.name !in inUse && it.lastModified() < old) it.delete() }
    }

    /** Esvazia a pasta temporária (cópias decifradas e PDFs já enviados). */
    fun clearTemp(ctx: Context) { tempDir(ctx).listFiles()?.forEach { it.delete() } }

    /** Grava [bytes] na pasta temporária e devolve um link content:// para abrir ou compartilhar. */
    fun tempUri(ctx: Context, fileName: String, bytes: ByteArray): Uri {
        val f = File(tempDir(ctx), fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        f.writeBytes(bytes)
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".arquivos", f)
    }

    /** Extensão para a cópia temporária, pelo tipo. */
    fun extension(type: String) = when {
        type == "application/pdf" -> "pdf"
        type == "image/png" -> "png"
        type == "image/webp" -> "webp"
        type.startsWith("image/") -> "jpg"
        else -> "bin"
    }

    /** "Apagar todos os dados": comprovantes, cópias temporárias e a chave. */
    fun wipe(ctx: Context) {
        File(ctx.filesDir, DIR).deleteRecursively()
        File(ctx.cacheDir, TEMP).deleteRecursively()
        try { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) } catch (_: Exception) { }
    }
}
