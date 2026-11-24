# syntax=docker/dockerfile:1

# AI 지식 시드 적재 태스크. 빌드 컨텍스트: 레포 루트
FROM python:3.12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY scripts/load_stores.py scripts/load_knowledge_seed.py scripts/run_seed_task.py scripts/

USER nobody
ENTRYPOINT ["python3", "/app/scripts/run_seed_task.py"]
