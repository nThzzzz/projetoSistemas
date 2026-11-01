FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1
WORKDIR /app

COPY bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY proto /proto
RUN python -m grpc_tools.protoc -I/proto --python_out=/app /proto/messages.proto

COPY bot/bot.py .

CMD ["python", "bot.py"]
