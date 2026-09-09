import os
import threading
import uuid
from pathlib import Path

import cv2
import gradio as gr
import insightface
import numpy as np
from huggingface_hub import hf_hub_download

try:
    import spaces
except ImportError:
    class _SpacesShim:
        @staticmethod
        def GPU(*args, **kwargs):
            if args and callable(args[0]) and not kwargs:
                return args[0]
            return lambda fn: fn
    spaces = _SpacesShim()

MODEL_REPO = "hacksider/deep-live-cam"
MODEL_FILE = "inswapper_128_fp16.onnx"
MODEL_DIR = Path("models")
MODEL_DIR.mkdir(parents=True, exist_ok=True)
SWAPPER_PATH = MODEL_DIR / MODEL_FILE

# One cached source face per session. This avoids uploading the source image for
# every camera frame. Sessions are intentionally short-lived and in-memory.
SESSIONS: dict[str, object] = {}
LOCK = threading.Lock()
FACE_ANALYSER = None
FACE_SWAPPER = None


def _model_path() -> str:
    if not SWAPPER_PATH.exists():
        downloaded = hf_hub_download(
            repo_id=MODEL_REPO,
            filename=MODEL_FILE,
            local_dir=str(MODEL_DIR),
        )
        return downloaded
    return str(SWAPPER_PATH)


def _providers() -> list:
    try:
        available = insightface.model_zoo.get_default_providers()
    except Exception:
        available = ["CPUExecutionProvider"]
    if "CUDAExecutionProvider" in available:
        return ["CUDAExecutionProvider", "CPUExecutionProvider"]
    return ["CPUExecutionProvider"]


def _load_models():
    global FACE_ANALYSER, FACE_SWAPPER
    if FACE_ANALYSER is not None and FACE_SWAPPER is not None:
        return

    providers = _providers()
    FACE_ANALYSER = insightface.app.FaceAnalysis(
        name="buffalo_l",
        providers=providers,
        allowed_modules=["detection", "recognition"],
    )
    FACE_ANALYSER.prepare(ctx_id=0, det_size=(640, 640))
    FACE_SWAPPER = insightface.model_zoo.get_model(_model_path(), providers=providers)


def _read_image(path: str):
    image = cv2.imread(path)
    if image is None:
        raise ValueError("Could not read uploaded image")
    return image


@spaces.GPU(duration=120)
def prepare_avatar(source_file: str) -> str:
    """Prepare one source face and return an in-memory session id."""
    _load_models()
    source = _read_image(source_file)
    faces = FACE_ANALYSER.get(source)
    if not faces:
        raise ValueError("No face detected in the source image")
    # Match Deep-Live-Cam's single-source behavior: use the largest detected face.
    source_face = max(faces, key=lambda f: float((f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1])))
    session_id = uuid.uuid4().hex
    with LOCK:
        SESSIONS[session_id] = source_face
    return session_id


@spaces.GPU(duration=30)
def process_frame(session_id: str, target_file: str):
    """Apply the prepared source identity to the largest face in one target frame."""
    _load_models()
    with LOCK:
        source_face = SESSIONS.get(session_id)
    if source_face is None:
        raise ValueError("Deep-Live-Cam session expired; choose the source photo again")

    frame = _read_image(target_file)
    faces = FACE_ANALYSER.get(frame)
    if not faces:
        return target_file
    target_face = max(faces, key=lambda f: float((f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1])))
    result = FACE_SWAPPER.get(frame, target_face, source_face, paste_back=True)
    if result is None:
        raise RuntimeError("INSwapper produced no output")

    output = Path("outputs")
    output.mkdir(exist_ok=True)
    path = output / f"kemzy-{uuid.uuid4().hex}.jpg"
    cv2.imwrite(str(path), result)
    return str(path)


with gr.Blocks(title="Kemzy-LiveAvatar • Deep-Live-Cam") as demo:
    gr.Markdown(
        "# Kemzy-LiveAvatar — Deep-Live-Cam\n"
        "Real InsightFace face detection/recognition + INSwapper FP16. "
        "For authorized personal/developer testing only."
    )
    source = gr.File(label="Source face", file_types=["image"], type="filepath")
    prepare = gr.Button("Prepare source face")
    session = gr.Textbox(label="Session", interactive=False)
    prepare.click(prepare_avatar, inputs=source, outputs=session, api_name="prepare_avatar")

    target = gr.File(label="Camera/target frame", file_types=["image"], type="filepath")
    output = gr.File(label="Processed frame")
    process = gr.Button("Process frame")
    process.click(process_frame, inputs=[session, target], outputs=output, api_name="process_frame")

    gr.Markdown(
        "Model source: hacksider/deep-live-cam. The FP16 INSwapper file is used to reduce "
        "server memory/bandwidth versus the 554 MB full-precision variant. No face-enhancement "
        "model is loaded."
    )

if __name__ == "__main__":
    demo.queue(max_size=4).launch()
