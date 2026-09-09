from fastapi.testclient import TestClient

from app.main import app


def test_session_requires_token():
    client = TestClient(app)
    created = client.post("/v1/sessions").json()
    response = client.delete(f"/v1/sessions/{created['session_id']}")
    assert response.status_code == 401


def test_reference_image_is_attached_to_session():
    client = TestClient(app)
    created = client.post("/v1/sessions").json()
    response = client.post(
        f"/v1/sessions/{created['session_id']}/reference",
        headers={"Authorization": f"Bearer {created['token']}"},
        files={"file": ("reference.jpg", b"not-an-image", "image/jpeg")},
    )
    assert response.status_code == 200
