FROM nvidia/cuda:12.6.3-cudnn-runtime-ubuntu24.04

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    KEMZY_DLC_PATH=/opt/Deep-Live-Cam \
    KEMZY_MODELS_PATH=/opt/Deep-Live-Cam/models \
    KEMZY_EXECUTION_PROVIDER=CUDAExecutionProvider

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv git ffmpeg libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt ./
RUN python3 -m pip install --break-system-packages -r requirements.txt

RUN git clone https://github.com/hacksider/Deep-Live-Cam.git /opt/Deep-Live-Cam \
    && cd /opt/Deep-Live-Cam \
    && git checkout 55d306d5ae07a4e6494013422ab244306a5c0879

COPY app ./app

EXPOSE 8080
CMD ["python3", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
