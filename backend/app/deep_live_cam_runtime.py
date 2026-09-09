from __future__ import annotations

import importlib
import sys
from pathlib import Path
from typing import Any

import cv2
import numpy as np


class DeepLiveCamRuntime:
    """Adapter around the real Deep-Live-Cam frame processor."""

    def __init__(self, upstream_path: Path, models_path: Path, execution_provider: str = "CUDAExecutionProvider") -> None:
        self.upstream_path = Path(upstream_path)
        self.models_path = Path(models_path)
        self.execution_provider = execution_provider
        self._ready = False
        self._source_face: Any = None
        self._face_analyser: Any = None
        self._face_swapper: Any = None

    @property
    def ready(self) -> bool:
        return self._ready

    def load(self) -> None:
        if not self.upstream_path.is_dir():
            raise RuntimeError(f"Deep-Live-Cam checkout is missing: {self.upstream_path}")

        self.models_path.mkdir(parents=True, exist_ok=True)
        upstream = str(self.upstream_path)
        if upstream not in sys.path:
            sys.path.insert(0, upstream)
        importlib.invalidate_caches()

        globals_module = importlib.import_module("modules.globals")
        globals_module.execution_providers = (
            [self.execution_provider, "CPUExecutionProvider"]
            if self.execution_provider != "CPUExecutionProvider"
            else ["CPUExecutionProvider"]
        )
        globals_module.many_faces = False
        globals_module.opacity = 1.0
        globals_module.frame_processors = ["face_swapper"]
        globals_module.mouth_mask = False
        globals_module.poisson_blend = False

        self._face_analyser = importlib.import_module("modules.face_analyser")
        self._face_swapper = importlib.import_module("modules.processors.frame.face_swapper")

        if hasattr(self._face_swapper, "pre_check") and not self._face_swapper.pre_check():
            raise RuntimeError("Deep-Live-Cam face-swap model preparation failed")
        if hasattr(self._face_swapper, "get_face_swapper") and self._face_swapper.get_face_swapper() is None:
            raise RuntimeError("Deep-Live-Cam face-swap model failed to load")
        self._ready = True

    def set_reference(self, image_bytes: bytes) -> None:
        if not self._ready:
            raise RuntimeError("Deep-Live-Cam runtime is not ready")
        image = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("reference image could not be decoded")
        source_face = self._face_analyser.get_one_face(image)
        if source_face is None:
            raise ValueError("no face detected in reference image")
        self._source_face = source_face

    def process(self, frame_bgr: np.ndarray) -> np.ndarray:
        if not self._ready or self._source_face is None:
            raise RuntimeError("runtime and reference image must be ready before processing")
        if frame_bgr.ndim != 3 or frame_bgr.shape[2] != 3:
            raise ValueError("frame must be a BGR image with three channels")
        frame = np.ascontiguousarray(frame_bgr, dtype=np.uint8)
        result = self._face_swapper.process_frame(self._source_face, frame)
        if result is None:
            raise RuntimeError("Deep-Live-Cam returned no processed frame")
        return np.ascontiguousarray(result, dtype=np.uint8)
