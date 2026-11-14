import os
import random
import time
import uuid

import zmq

import messages_pb2 as pb

BROKER_ADDR = os.getenv("BROKER_ADDR", "tcp://broker:5555")
BOT_NAME = os.getenv("BOT_NAME", "bot")
MINIMO_CANAIS = 5


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


def login(socket):
    while True:
        envelope = novo_envelope()
        envelope.login_req.SetInParent()
        if requisitar(socket, envelope).login_rep.status == pb.OK:
            return
        time.sleep(1)


def listar_canais(socket):
    envelope = novo_envelope()
    envelope.listar_canais_req.SetInParent()
    return list(requisitar(socket, envelope).listar_canais_rep.canais)


def criar_canal(socket, nome):
    envelope = novo_envelope()
    envelope.criar_canal_req.canal = nome
    return requisitar(socket, envelope).criar_canal_rep.status == pb.OK


def main():
    contexto = zmq.Context()
    socket = contexto.socket(zmq.REQ)
    socket.connect(BROKER_ADDR)

    login(socket)
    while True:
        if len(listar_canais(socket)) < MINIMO_CANAIS:
            criar_canal(socket, f"canal-{random.randint(1, 20)}")
        time.sleep(2)


if __name__ == "__main__":
    main()
