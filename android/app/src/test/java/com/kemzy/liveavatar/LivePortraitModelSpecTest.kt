package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LivePortraitModelSpecTest {
    @Test fun canonicalGeneratorIsFixVariant() {
        assertEquals("warping_spade-fix.onnx", LivePortraitModelSpec.warpingSpade)
    }

    @Test fun completeManifestRequiresEveryNamedFile() {
        val dir = createTempDir(prefix = "kemzy-models-")
        try {
            val files = LivePortraitModelSpec.requiredNames.associateWith { name -> File(dir, name).apply { writeBytes(byteArrayOf(1)) } }
            assertTrue(LivePortraitModelSpec.check(files).all { it.present })
            files.getValue(LivePortraitModelSpec.warpingSpade).delete()
            assertFalse(LivePortraitModelSpec.check(files).first { it.name == LivePortraitModelSpec.warpingSpade }.present)
        } finally { dir.deleteRecursively() }
    }
}
