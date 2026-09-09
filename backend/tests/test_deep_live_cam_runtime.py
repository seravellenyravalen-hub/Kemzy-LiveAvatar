import numpy as np
import pytest

from app.deep_live_cam_runtime import DeepLiveCamRuntime


def test_runtime_requires_existing_upstream_checkout(tmp_path):
    runtime = DeepLiveCamRuntime(
        upstream_path=tmp_path / "missing",
        models_path=tmp_path / "models",
        execution_provider="CPUExecutionProvider",
    )

    with pytest.raises(RuntimeError, match="Deep-Live-Cam checkout is missing"):
        runtime.load()


def test_runtime_processes_through_upstream_processor(tmp_path):
    upstream = tmp_path / "Deep-Live-Cam"
    modules = upstream / "modules"
    modules.mkdir(parents=True)
    (modules / "__init__.py").write_text("")
    (modules / "face_analyser.py").write_text(
        "def get_one_face(frame):\n    return {'source': True}\n"
    )
    processors = modules / "processors" / "frame"
    processors.mkdir(parents=True)
    (processors / "__init__.py").write_text("")
    (processors / "face_swapper.py").write_text(
        "def process_frame(source_face, temp_frame, target_face=None):\n"
        "    return temp_frame + 1\n"
    )

    runtime = DeepLiveCamRuntime(
        upstream_path=upstream,
        models_path=tmp_path / "models",
        execution_provider="CPUExecutionProvider",
    )
    runtime.load()
    runtime.set_reference(np.zeros((8, 8, 3), dtype=np.uint8))

    frame = np.zeros((8, 8, 3), dtype=np.uint8)
    result = runtime.process(frame)

    np.testing.assert_array_equal(result, np.ones((8, 8, 3), dtype=np.uint8))
    assert runtime.ready is True
