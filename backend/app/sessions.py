from dataclasses import dataclass, field
from secrets import token_urlsafe
from time import monotonic

from .config import settings


@dataclass
class Session:
    session_id: str
    token: str
    created_at: float = field(default_factory=monotonic)
    reference: bytes | None = None

    @property
    def expired(self) -> bool:
        return monotonic() - self.created_at > settings.session_ttl_seconds


class SessionStore:
    def __init__(self) -> None:
        self._sessions: dict[str, Session] = {}

    def _purge(self) -> None:
        for session_id, session in list(self._sessions.items()):
            if session.expired:
                del self._sessions[session_id]

    def create(self) -> Session:
        self._purge()
        if len(self._sessions) >= settings.max_sessions:
            raise RuntimeError("maximum live sessions reached")
        session = Session(token_urlsafe(18), token_urlsafe(32))
        self._sessions[session.session_id] = session
        return session

    def get(self, session_id: str, token: str) -> Session | None:
        self._purge()
        session = self._sessions.get(session_id)
        if session is None or session.token != token:
            return None
        return session

    def delete(self, session_id: str, token: str) -> bool:
        session = self.get(session_id, token)
        if session is None:
            return False
        session.reference = None
        del self._sessions[session_id]
        return True
