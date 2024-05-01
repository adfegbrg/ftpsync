package com.example.photosync

import com.example.photosync.FtpClient
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import java.io.File


fun main() {
    runBlocking {
        FtpClient.connect("127.0.0.1", 2211, "mathis", "3252")
        val homeDir = System.getProperty("user.home")
        val file = File("$homeDir/Downloads/new-doctor-who.png")
        FtpClient.uploadFile("/srv/ftp/test/new-doctor-who.png",file)
        println("test")
        FtpClient.disconnect()
    }
}