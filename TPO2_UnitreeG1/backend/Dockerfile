# --- Etapa 1: CycloneDDS (librería C que necesita unitree_sdk2_python) ---
FROM python:3.11-slim AS cyclonedds
RUN apt-get update && apt-get install -y --no-install-recommends git cmake build-essential \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch releases/0.10.x https://github.com/eclipse-cyclonedds/cyclonedds.git /src \
    && cmake -B /src/build -S /src -DCMAKE_INSTALL_PREFIX=/opt/cyclonedds -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF \
    && cmake --build /src/build --target install -j"$(nproc)"

# --- Etapa 2: backend ---
FROM python:3.11-slim
ENV PIP_NO_CACHE_DIR=1 \
    CYCLONEDDS_HOME=/opt/cyclonedds \
    CMAKE_PREFIX_PATH=/opt/cyclonedds \
    LD_LIBRARY_PATH=/opt/cyclonedds/lib

RUN apt-get update && apt-get install -y --no-install-recommends git build-essential \
    && rm -rf /var/lib/apt/lists/*
COPY --from=cyclonedds /opt/cyclonedds /opt/cyclonedds

WORKDIR /app
COPY requirements.txt .
# torch CPU para correr la política de locomoción; unitree_sdk2_python para DDS.
RUN pip install -r requirements.txt \
    && pip install torch --index-url https://download.pytorch.org/whl/cpu \
    && git clone --depth 1 https://github.com/unitreerobotics/unitree_sdk2_python.git /opt/unitree_sdk2_python \
    && pip install -e /opt/unitree_sdk2_python

COPY policy ./policy
COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
