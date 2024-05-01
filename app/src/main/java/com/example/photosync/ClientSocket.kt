package com.example.photosync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Button
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.*
import kotlin.system.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.map
import java.io.IOException


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "syncFolders")
val FOLDERS_TO_SYNC = stringSetPreferencesKey("FOLDERS_TO_SYNC")
data class syncFolders(val folderUris: Set<String>? = emptySet())

open class ClientSocket : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val myButton = findViewById<Button>(R.id.button1)
        myButton.setOnClickListener {
            openDirectory(Uri.parse(Environment.DIRECTORY_DCIM), 13)
        }

        val button2 = findViewById<Button>(R.id.button2)
        button2.setOnClickListener {
            openDirectory(Uri.parse(Environment.DIRECTORY_DCIM), 11)
        }

        val button3 = findViewById<Button>(R.id.SyncStored)
        button3.setOnClickListener {
            runBlocking {
                var folders = emptySet<String>()

                retrieveFolderUris().collect { syncFolders ->
                    folders = syncFolders.folderUris!!
                }
                Log.d("test", folders.toString())
                for (i in folders) {
                    CoroutineScope(Dispatchers.IO).launch {
                        uploadFolder(Uri.parse(i))
                    }
                }
            }
        }
    }

    private suspend fun upload() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect("192.168.178.136", 9002)

        val sendChannel = socket.openWriteChannel(autoFlush = true)
        withContext(Dispatchers.IO) {
            FtpClient.connect("192.168.178.136", 2211, "mathis", "3252")
            val tes = File("/storage/self/primary/DCIM/20240322_214253.jpg")
            if (tes.exists() && tes.canRead()) {
                FtpClient.uploadFile("/srv/ftp/test/20240310_002421.jpg",tes)
            } else {
                try {
                    sendChannel.writeStringUtf8(tes.canRead().toString())
                } catch (e: Throwable) {
                    println("Error sending message: ${e.message}") // Log the exception message
                } finally {
                    sendChannel.close()
                    socket.close()
                }
            }
            FtpClient.disconnect()
        }
    }

    private fun openDirectory(pickerInitialUri: Uri, code: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        startActivityForResult(intent, code)
    }

    private fun openFile(pickerInitialUri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        startActivityForResult(intent, 14)
    }

    suspend fun storeFolderUri(uri: Uri) {
        dataStore.edit { preferences ->
            val stringFolderUris: Set<String> = preferences[FOLDERS_TO_SYNC] ?: setOf()
            val newStringFolderUris = stringFolderUris + (uri.toString())
            preferences[FOLDERS_TO_SYNC] = newStringFolderUris
        }
    }



    private fun retrieveFolderUris(): Flow<syncFolders> = dataStore.data
        .map { preferences ->
            syncFolders(
                folderUris = preferences[FOLDERS_TO_SYNC]
            )
        }


    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == 14 && resultCode == Activity.RESULT_OK) {

            resultData?.data?.also { uri ->
                val tes = fileFromContentUri(context = applicationContext,contentUri = uri)

                tes.setReadable(true)
                tes.setWritable(true)

                runBlocking {
                    withContext(Dispatchers.IO) {
                    val selectorManager = SelectorManager(Dispatchers.IO)
                    val socket = aSocket(selectorManager).tcp().connect("192.168.178.136", 9002)

                    val sendChannel = socket.openWriteChannel(autoFlush = true)
                    FtpClient.connect("192.168.178.136", 2211, "mathis", "3252")
                    if (tes.exists() && tes.canRead()) {
                        FtpClient.correctTransferType()
                        FtpClient.uploadFile("/srv/ftp/test/${uri.path?.substring(uri.path!!.lastIndexOf("/")+1)}",tes)
                    } else {
                        try {
                            sendChannel.writeStringUtf8(tes.exists().toString())
                        } catch (e: Throwable) {
                            println("Error sending message: ${e.message}") // Log the exception message
                        } finally {
                            sendChannel.close()
                            socket.close()
                        }
                    }
                    FtpClient.disconnect()
                    }
                }
            }
        }
        if (requestCode == 11 && resultCode == Activity.RESULT_OK) {

            resultData?.data?.also { uri ->
                uploadFolder(uri)
            }
        }

        if (requestCode == 13 && resultCode == Activity.RESULT_OK) {

            resultData?.data?.also { uri ->
                runBlocking {
                    storeFolderUri(uri)
                }
            }
        }
    }
    private fun fileFromContentUri(context: Context, contentUri: Uri): File {

        val fileExtension = getFileExtension(context, contentUri)
        val fileName = "temporary_file" + if (fileExtension != null) ".$fileExtension" else ""

        val tempFile = File(context.cacheDir, fileName)
        tempFile.createNewFile()

        try {
            val oStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)

            inputStream?.let {
                copy(inputStream, oStream)
            }

            oStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tempFile
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        val fileType: String? = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType)
    }

    @Throws(IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }

    private fun uploadFolder(uri: Uri) {
        val docRoot: DocumentFile? = DocumentFile.fromTreeUri(this, uri)

        val docPrefsPng: List<DocumentFile> = docRoot?.listFiles().orEmpty()
            .filterNot { it.isDirectory }
            .filter { it.name.orEmpty().endsWith(".png") }

        val docPrefsJpg: List<DocumentFile> = docRoot?.listFiles().orEmpty()
            .filterNot { it.isDirectory }
            .filter { it.name.orEmpty().endsWith(".jpg") }

        val docPrefsMp4: List<DocumentFile> = docRoot?.listFiles().orEmpty()
            .filterNot { it.isDirectory }
            .filter { it.name.orEmpty().endsWith(".mp4") }

        val docPrefs = docPrefsJpg + docPrefsPng + docPrefsMp4

        val docs: List<Uri> = docPrefs.map { it.uri }
        for (i in docs) {
            val tes = fileFromContentUri(context = applicationContext,contentUri = i)

            if (!tes.isFile && !tes.canRead() && !tes.exists()) {
                Log.d("error", "onActivityResult: srthrsth")
            }
            Log.d("error", "onActivityResult: juhu")
            tes.setReadable(true)
            tes.setWritable(true)

            runBlocking {
                withContext(Dispatchers.IO) {
                    val selectorManager = SelectorManager(Dispatchers.IO)
                    val socket = aSocket(selectorManager).tcp().connect("192.168.178.136", 9002)

                    val sendChannel = socket.openWriteChannel(autoFlush = true)
                    FtpClient.connect("192.168.178.136", 2211, "mathis", "3252")
                    if (tes.exists() && tes.canRead()) {
                        FtpClient.correctTransferType()
                        FtpClient.uploadFile("/srv/ftp/test/${i.path?.substring(i.path!!.lastIndexOf("/")+1)}",tes)
                    } else {
                        try {
                            sendChannel.writeStringUtf8(tes.exists().toString())
                        } catch (e: Throwable) {
                            println("Error sending message: ${e.message}") // Log the exception message
                        } finally {
                            sendChannel.close()
                            socket.close()
                        }
                    }
                    FtpClient.disconnect()
                }
            }
        }
    }
}




