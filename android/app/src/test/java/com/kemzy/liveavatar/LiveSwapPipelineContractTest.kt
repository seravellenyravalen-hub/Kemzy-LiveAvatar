package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSwapPipelineContractTest {
    @Test
    fun targetFaceAlignmentUsesFiveLandmarks() {
        val landmarks = FaceLandmarks(
            leftEye = Point2(40f, 40f),
            rightEye = Point2(88f, 44f),
            nose = Point2(64f, 68f),
            leftMouth = Point2(46f, 92f),
            rightMouth = Point2(82f, 94f)
        )

        val geometry = FaceGeometry(landmarks = landmarks)

        assertEquals(5, geometry.landmarkCount)
        assertTrue(geometry.faceCenter.x > 40f)
        assertTrue(geometry.faceCenter.x < 88f)
    }

    @Test
    fun swapperRequiresTheExpectedInswapperInputContract() {
        val contract = InswapperInputContract()

        assertEquals(128, contract.faceWidth)
        assertEquals(128, contract.faceHeight)
        assertEquals(512, contract.sourceEmbeddingSize)
        assertEquals(1, contract.batchSize)
    }

    @Test
    fun liveProcessorDoesNotProcessWithoutSourceEmbedding() {
        val processor = LiveSwapProcessor(
            detector = UnavailableFaceDetector,
            embedder = UnavailableFaceEmbedder,
            swapper = UnavailableFaceSwapper,
            compositor = FaceCompositor()
        )

        val result = processor.process(null, null)

        assertEquals(LiveSwapResult.NoSourceFace, result)
    }
}
