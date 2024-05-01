package com.example.photosync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.handleCoroutineException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.commons.net.ftp.FTPClient
import java.lang.Error

class FtpClient private constructor() {

     companion object {
        private val ftpClient = FTPClient()

        suspend fun connect(host: String, port: Int, username: String, password: String) {
            withContext(Dispatchers.IO) {
                ftpClient.connect(host, port)
                ftpClient.login(username, password)
                ftpClient.setFileTransferMode(1)
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
            }
        }

        suspend fun downloadFile(remotePath: String, localPath: String) {
            withContext(Dispatchers.IO) {
                val outputStream = FileOutputStream(localPath)
                ftpClient.retrieveFile(remotePath, outputStream)
                outputStream.close()
            }
        }

        suspend fun uploadFile(remotePath: String, localFile: File) {
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = FileInputStream(localFile)
                    ftpClient.storeFile(remotePath, inputStream)
                    inputStream.close()
                } catch (e: Exception) {
                    // Handle the exception here, log the error, notify the user, etc.
                    handleError(e)
                }
            }
        }

        fun correctTransferType() {
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
        }

        fun handleError(e: Exception) {
            println("Error uploading file: $e")
        }
        suspend fun disconnect() {
            withContext(Dispatchers.IO) {
                ftpClient.disconnect()
            }
        }
    }


}
