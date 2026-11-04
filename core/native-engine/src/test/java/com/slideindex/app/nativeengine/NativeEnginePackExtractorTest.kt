package com.slideindex.app.nativeengine

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 17（SDK 37）起 `System.load()` 拒绝加载可写文件：libcore `Runtime.load0` 在
 * `file.canWrite()` 为真时经 compat change `THROW_ERROR_FOR_WRITABLE_DCL`(463348571)
 * 抛 `UnsatisfiedLinkError`（`targetSdk >= 37` 默认启用），上层只表现为「OCR 运行库未就绪」。
 * 因此引擎包解压出来的 native 库必须立刻置为只读。
 */
class NativeEnginePackExtractorTest {

    @Test
    fun `extracted native libraries are sealed read only`() {
        val root = createTempRoot()
        try {
            val zipFile = File(root, "pack.zip")
            writeZip(
                zipFile,
                linkedMapOf(
                    "lib/arm64-v8a/libonnxruntime.so" to "native-bytes",
                    "arm64-v8a/libsample_jni.so" to "native-bytes-2",
                    "assets/data/sample.txt" to "data-bytes",
                ),
            )

            val libDir = File(root, "pack/lib/arm64-v8a")
            val assetsDir = File(root, "pack/assets")
            NativeEnginePackExtractor.extractZip(zipFile, libDir, assetsDir)

            val onnx = File(libDir, "libonnxruntime.so")
            assertTrue("解压出的 native 库应存在：${onnx.absolutePath}", onnx.isFile)
            assertFalse("native 库必须只读，否则 Android 17 上 System.load 会被拒绝", onnx.canWrite())

            val sampleLib = File(libDir, "libsample_jni.so")
            assertTrue(sampleLib.isFile)
            assertFalse(sampleLib.canWrite())

            val data = File(assetsDir, "data/sample.txt")
            assertTrue(data.isFile)
            assertTrue("数据文件不需要只读", data.canWrite())
        } finally {
            root.makeWritableAndDelete()
        }
    }

    @Test
    fun `re-extract over already sealed libraries still writes`() {
        val root = createTempRoot()
        try {
            val zipFile = File(root, "pack.zip")
            writeZip(zipFile, linkedMapOf("lib/arm64-v8a/libonnxruntime.so" to "first"))
            val libDir = File(root, "pack/lib/arm64-v8a")
            val assetsDir = File(root, "pack/assets")

            NativeEnginePackExtractor.extractZip(zipFile, libDir, assetsDir)
            val onnx = File(libDir, "libonnxruntime.so")
            assertFalse(onnx.canWrite())

            // 覆盖安装/重新下载时可能原地重解压：先放开写权限再写，不能因为只读而失败。
            writeZip(zipFile, linkedMapOf("lib/arm64-v8a/libonnxruntime.so" to "second-content"))
            NativeEnginePackExtractor.extractZip(zipFile, libDir, assetsDir)
            assertTrue(onnx.readText() == "second-content")
            assertFalse(onnx.canWrite())
        } finally {
            root.makeWritableAndDelete()
        }
    }

    private fun createTempRoot(): File {
        val root = File(System.getProperty("java.io.tmpdir"), "native-engine-pack-test-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        return root
    }

    private fun writeZip(target: File, entries: Map<String, String>) {
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    /** Windows 上只读文件删不掉：先恢复写权限再删。 */
    private fun File.makeWritableAndDelete() {
        if (isDirectory) {
            listFiles()?.forEach { it.makeWritableAndDelete() }
        }
        setWritable(true)
        delete()
    }
}
