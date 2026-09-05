package com.visionmate.pro.communication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class Esp32Stream(
    private val streamUrl: String
) {

    suspend fun start(
        onFrame: (Bitmap) -> Unit
    ) = withContext(Dispatchers.IO) {

        val connection =
            URL(streamUrl).openConnection() as HttpURLConnection

        connection.connectTimeout = 5000
        connection.readTimeout = 0
        connection.doInput = true

        connection.connect()

        val input =
            BufferedInputStream(connection.inputStream)

        val buffer = ByteArray(16 * 1024)

        var imageData = ByteArray(0)

        while (true) {

            val bytesRead = input.read(buffer)

            if (bytesRead == -1) {
                break
            }

            imageData += buffer.copyOf(bytesRead)

            while (true) {

                val start = findJpegStart(imageData)
                val end = findJpegEnd(imageData)

                if (start == -1 || end == -1) {
                    break
                }

                val jpegEnd = end + 2

                val jpegBytes =
                    imageData.copyOfRange(
                        start,
                        jpegEnd
                    )

                imageData =
                    imageData.copyOfRange(
                        jpegEnd,
                        imageData.size
                    )

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        jpegBytes,
                        0,
                        jpegBytes.size
                    )

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        onFrame(bitmap)
                    }
                }
            }
        }

        input.close()
        connection.disconnect()
    }

    private fun findJpegStart(
        data: ByteArray
    ): Int {

        for (i in 0 until data.size - 1) {

            if (
                data[i].toInt() and 0xFF == 0xFF &&
                data[i + 1].toInt() and 0xFF == 0xD8
            ) {
                return i
            }
        }

        return -1
    }

    private fun findJpegEnd(
        data: ByteArray
    ): Int {

        for (i in 0 until data.size - 1) {

            if (
                data[i].toInt() and 0xFF == 0xFF &&
                data[i + 1].toInt() and 0xFF == 0xD9
            ) {
                return i
            }
        }

        return -1
    }
}