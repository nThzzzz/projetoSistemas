import os
import time
import uuid

import zmq

import messages_pb2 as pb

BROKER_ADDR = os.getenv("BROKER_ADDR", "tcp://broker:5555")
BOT_NAME = os.getenv("BOT_NAME", "bot")


def novo_envelope():
    envelope = pb.Envelope()
    envelope.header.msg_id = str(uuid.uuid4())
    envelope.header.remetente = BOT_NAME
    envelope.header.ts_envio_ms = int(time.time() * 1000)
    return envelope


def requisitar(socket, envelope):
    print(f"[enviado]\n{envelope}", flush=True)
    socket.send(envelope.SerializeToString())
    resposta = pb.Envelope()
    resposta.ParseFromString(socket.recv())
    print(f"[recebido]\n{resposta}", flush=True)
    return resposta


def main():
    contexto = zmq.Context()
    socket = contexto.socket(zmq.REQ)
    socket.connect(BROKER_ADDR)

    envelope = novo_envelope()
    envelope.login_req.SetInParent()
    requisitar(socket, envelope)


if __name__ == "__main__":
    main()
