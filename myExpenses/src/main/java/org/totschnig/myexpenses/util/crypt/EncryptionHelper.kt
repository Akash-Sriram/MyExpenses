package org.totschnig.myexpenses.util.crypt

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Credits: https://github.com/andOTP/andOTP
object EncryptionHelper {
    const val ENCRYPTION_IV_LENGTH = 12
    const val ALGORITHM_SYMMETRIC = "AES/GCM/NoPadding"
    const val MAGIC_NUMBER = "ME_ENC_01"

    @JvmStatic
    fun generateRandom(length: Int): ByteArray {
        val raw = ByteArray(length)
        SecureRandom().nextBytes(raw)
        return raw
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun generateSymmetricKeyFromPassword(password: String): SecretKey {
        val sha = MessageDigest.getInstance("SHA-256")
        return SecretKeySpec(sha.digest(password.toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray?, password: String?): ByteArray {
        requireNotNull(plaintext) { "Plaintext cannot be null" }
        requireNotNull(password) { "Password cannot be null" }
        val magicNumber = MAGIC_NUMBER.toByteArray()
        val iv = generateRandom(ENCRYPTION_IV_LENGTH)
        val cipherText = encrypt(generateSymmetricKeyFromPassword(password), IvParameterSpec(iv), plaintext)
        val combined = ByteArray(magicNumber.size + iv.size + cipherText.size)

        System.arraycopy(magicNumber, 0, combined, 0, magicNumber.size)
        System.arraycopy(iv, 0, combined, magicNumber.size, iv.size)
        System.arraycopy(cipherText, 0, combined, magicNumber.size + iv.size, cipherText.size)

        return combined
    }

    private fun encrypt(secretKey: SecretKey, iv: IvParameterSpec, plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM_SYMMETRIC)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        return cipher.doFinal(plainText)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(secretKey: SecretKey, iv: IvParameterSpec, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM_SYMMETRIC)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        return cipher.doFinal(cipherText)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(cipherText: ByteArray?, password: String?): ByteArray {
        requireNotNull(cipherText) { "Ciphertext cannot be null" }
        requireNotNull(password) { "Password cannot be null" }
        val magicLength = MAGIC_NUMBER.length
        val magic = cipherText.copyOfRange(0, magicLength)
        if (MAGIC_NUMBER != String(magic)) {
            throw GeneralSecurityException("Invalid Magic Number")
        }
        val to = magicLength + ENCRYPTION_IV_LENGTH
        val iv = cipherText.copyOfRange(magicLength, to)
        val encrypted = cipherText.copyOfRange(to, cipherText.size)

        return decrypt(generateSymmetricKeyFromPassword(password), IvParameterSpec(iv), encrypted)
    }

    @JvmStatic
    @Throws(IOException::class, GeneralSecurityException::class)
    fun encrypt(outputStream: OutputStream, password: String?): OutputStream {
        requireNotNull(password) { "Password cannot be null" }
        outputStream.write(MAGIC_NUMBER.toByteArray())
        val key = generateSymmetricKeyFromPassword(password)
        val cipher = Cipher.getInstance(ALGORITHM_SYMMETRIC)
        val iv = generateRandom(ENCRYPTION_IV_LENGTH)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        outputStream.write(iv)
        return CipherOutputStream(outputStream, cipher)
    }

    @JvmStatic
    @Throws(IOException::class, GeneralSecurityException::class)
    fun decrypt(inputStream: InputStream?, password: String?): InputStream {
        requireNotNull(inputStream) { "InputStream cannot be null" }
        requireNotNull(password) { "Password cannot be null" }
        val magic = ByteArray(MAGIC_NUMBER.length)
        val read = read(inputStream, magic)
        if (read == 0) return inputStream
        if (MAGIC_NUMBER != String(magic)) {
            throw GeneralSecurityException("Invalid Magic Number")
        }
        val iv = ByteArray(ENCRYPTION_IV_LENGTH)
        read(inputStream, iv)
        val key = generateSymmetricKeyFromPassword(password)
        val cipher = Cipher.getInstance(ALGORITHM_SYMMETRIC)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return CipherInputStream(inputStream, cipher)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun read(`in`: InputStream, b: ByteArray): Int {
        var total = 0
        while (total < b.size) {
            val result = `in`.read(b, total, b.size - total)
            if (result == -1) {
                break
            }
            total += result
        }
        return total
    }

    @JvmStatic
    fun wrap(`is`: InputStream?): PushbackInputStream? {
        return `is`?.let { PushbackInputStream(it, MAGIC_NUMBER.length) }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun isEncrypted(pb: InputStream): Boolean {
        val magic = ByteArray(MAGIC_NUMBER.length)
        pb.read(magic)
        if (pb is PushbackInputStream) {
            pb.unread(magic)
        }
        return MAGIC_NUMBER == String(magic)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(inputStream: InputStream?, password: String?): InputStream {
        requireNotNull(inputStream) { "InputStream cannot be null" }
        requireNotNull(password) { "Password cannot be null" }
        val magicNumber = ByteArrayInputStream(MAGIC_NUMBER.toByteArray())
        val key = generateSymmetricKeyFromPassword(password)
        val cipher = Cipher.getInstance(ALGORITHM_SYMMETRIC)
        val iv = generateRandom(ENCRYPTION_IV_LENGTH)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ivStream = ByteArrayInputStream(iv)
        return SequenceInputStream(
            Collections.enumeration(
                listOf(
                    magicNumber,
                    ivStream,
                    object : CipherInputStream(inputStream, cipher) {
                        override fun close() {
                            try {
                                super.close()
                            } catch (ignored: IllegalStateException) {
                                /* https://github.com/google/conscrypt/issues/548#issuecomment-426968330 */
                            }
                        }
                    }
                )
            )
        )
    }
}
