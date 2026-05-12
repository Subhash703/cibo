# syntax=docker/dockerfile:1.7
FROM python:3.13-slim AS runtime

# Don't write .pyc, flush stdout immediately (so Render's logs see real-time output).
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Copy package metadata + source. psycopg[binary] ships the C extension as a
# pre-built wheel, so we don't need libpq-dev / gcc in the runtime image —
# keeps the layer small.
COPY pyproject.toml ./
COPY src ./src

RUN pip install .

# Render assigns the port via $PORT (no hard-coded default in their infra).
# Locally / when building manually, default to 8000.
EXPOSE 8000

# `exec` so uvicorn becomes PID 1 and receives SIGTERM directly when Render
# stops the container — clean shutdown, no 30s hard-kill wait.
CMD ["sh", "-c", "exec python -m uvicorn foodlens_backend.main:app --host 0.0.0.0 --port ${PORT:-8000} --app-dir src"]
