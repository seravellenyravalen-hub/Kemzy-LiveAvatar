from __future__ import annotations

from typing import Any

import numpy as np
from aiortc import MediaStreamTrack, RTCPeerConnection, RTCSessionDescription
from av import VideoFrame


class ProcessedVideoTrack(MediaStreamTrack):
    """Return each incoming camera frame after remote inference."""

    kind = "video"

    def __init__(self, source: MediaStreamTrack, runtime: Any) -> None:
        super().__init__()
        self.source = source
        self.runtime = runtime

    async def recv(self) -> VideoFrame:
        incoming = await self.source.recv()
        frame_bgr = incoming.to_ndarray(format="bgr24")
        processed = self.runtime.process(np.ascontiguousarray(frame_bgr, dtype=np.uint8))
        outgoing = VideoFrame.from_ndarray(processed, format="bgr24")
        outgoing.pts = incoming.pts
        outgoing.time_base = incoming.time_base
        return outgoing


class WebRTCSessionManager:
    def __init__(self) -> None:
        self._peers: dict[str, RTCPeerConnection] = {}

    async def accept_offer(self, session_id: str, sdp: str, sdp_type: str, runtime: Any) -> tuple[str, str]:
        peer = RTCPeerConnection()

        @peer.on("track")
        def on_track(track: MediaStreamTrack) -> None:
            if track.kind == "video":
                peer.addTrack(ProcessedVideoTrack(track, runtime))

        @peer.on("connectionstatechange")
        async def on_connectionstatechange() -> None:
            if peer.connectionState in {"failed", "closed", "disconnected"}:
                await self.close(session_id)

        self._peers[session_id] = peer
        await peer.setRemoteDescription(RTCSessionDescription(sdp=sdp, type=sdp_type))
        answer = await peer.createAnswer()
        await peer.setLocalDescription(answer)
        return peer.localDescription.sdp, peer.localDescription.type

    async def close(self, session_id: str) -> None:
        peer = self._peers.pop(session_id, None)
        if peer is not None:
            await peer.close()


webrtc_sessions = WebRTCSessionManager()
