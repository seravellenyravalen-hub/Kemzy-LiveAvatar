import numpy as np
import pytest
from av import VideoFrame
from aiortc import MediaStreamTrack

from app.webrtc import ProcessedVideoTrack


class FakeRuntime:
    def __init__(self):
        self.calls = 0

    def process(self, frame):
        self.calls += 1
        return np.clip(frame + 7, 0, 255).astype(np.uint8)


class OneFrameTrack(MediaStreamTrack):
    kind = "video"

    def __init__(self):
        super().__init__()
        self.sent = False

    async def recv(self):
        if self.sent:
            self.stop()
            raise RuntimeError("done")
        self.sent = True
        frame = VideoFrame.from_ndarray(np.zeros((4, 4, 3), dtype=np.uint8), format="bgr24")
        frame.pts = 11
        frame.time_base = 1 / 30
        return frame


@pytest.mark.asyncio
async def test_processed_video_track_returns_runtime_output():
    runtime = FakeRuntime()
    track = ProcessedVideoTrack(OneFrameTrack(), runtime)

    frame = await track.recv()

    assert runtime.calls == 1
    assert frame.pts == 11
    assert frame.to_ndarray(format="bgr24")[0, 0, 0] == 7
