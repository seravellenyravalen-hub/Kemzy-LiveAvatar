from abc import ABC, abstractmethod
from typing import Optional

import numpy as np


class ModelRuntime(ABC):
    """Boundary around the real Deep-Live-Cam/InsightFace inference runtime."""

    @abstractmethod
    def load(self) -> None:
        raise NotImplementedError

    @property
    @abstractmethod
    def ready(self) -> bool:
        raise NotImplementedError

    @abstractmethod
    def set_reference(self, image_bytes: bytes) -> None:
        raise NotImplementedError

    @abstractmethod
    def process(self, frame_bgr: np.ndarray) -> np.ndarray:
        raise NotImplementedError


class UnconfiguredModelRuntime(ModelRuntime):
    """Safe startup runtime until the real model adapter is installed/configured."""

    def __init__(self) -> None:
        self._ready = False
        self._reference: Optional[bytes] = None

    def load(self) -> None:
        self._ready = False

    @property
    def ready(self) -> bool:
        return self._ready

    def set_reference(self, image_bytes: bytes) -> None:
        self._reference = image_bytes

    def process(self, frame_bgr: np.ndarray) -> np.ndarray:
        if self._reference is None:
            raise RuntimeError("reference image is required before processing")
        raise RuntimeError("Deep-Live-Cam runtime is not configured")
