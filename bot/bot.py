import os
import random
import time
import uuid
from datetime import datetime

import zmq

import messages_pb2 as pb

BROKER_ADDR = os.getenv("BROKER_ADDR", "tcp://broker:5555")
PROXY_ADDR = os.getenv("PROXY_ADDR", "tcp://proxy:5558")
BOT_NAME = os.getenv("BOT_NAME", "bot")
MINIMO_CANAIS = 5
MAXIMO_INSCRICOES = 3
TEMPO_LIMITE_MS = 10000
lamport = 0
contexto = zmq.Context()
req = None
PALAVRAS = ["ola", "mundo", "sistemas", "distribuidos", "zeromq", "canal", "bot", "mensagem", "teste", "servidor"]


def agora_ms():
    return int(time.time() * 1000)


def hora(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%H:%M:%S.%f")[:-3]


def novo_envelope():
    global lamport
    lamport += 1
    envelope = pb.Envelope()
    envelope.header.msg_id = str(uuid.uuid4())
    envelope.header.remetente = BOT_NAME
    envelope.header.ts_envio_ms = agora_ms()
    envelope.header.lamport = lamport
    return envelope


def ler(dados):
    global lamport
    envelope = pb.Envelope()
    envelope.ParseFromString(dados)
    lamport = max(lamport, envelope.header.lamport)
    return envelope


def conectar():
    global req
    if req is not None:
        req.close(linger=0)
    req = contexto.socket(zmq.REQ)
    req.setsockopt(zmq.RCVTIMEO, TEMPO_LIMITE_MS)
    req.connect(BROKER_ADDR)


def requisitar(envelope):
    while True:
        print(f"[enviado]\n{envelope}", flush=True)
        req.send(envelope.SerializeToString())
        try:
            resposta = ler(req.recv())
            print(f"[recebido]\n{resposta}", flush=True)
            return resposta
        except zmq.Again:
            print("[sem resposta, reenviando]", flush=True)
            conectar()


def login():
    while True:
        envelope = novo_envelope()
        envelope.login_req.SetInParent()
        if requisitar(envelope).login_rep.status == pb.OK:
            return
        time.sleep(1)


def listar_canais():
    envelope = novo_envelope()
    envelope.listar_canais_req.SetInParent()
    return list(requisitar(envelope).listar_canais_rep.canais)


def criar_canal(nome):
    envelope = novo_envelope()
    envelope.criar_canal_req.canal = nome
    return requisitar(envelope).criar_canal_rep.status == pb.OK


def publicar(canal, mensagem):
    envelope = novo_envelope()
    envelope.publicar_req.canal = canal
    envelope.publicar_req.mensagem = mensagem
    return requisitar(envelope).publicar_rep.status == pb.OK


def inscrever(sub, inscritos, canal):
    sub.setsockopt_string(zmq.SUBSCRIBE, canal)
    inscritos.add(canal)
    print(f"[inscrito em {canal}] canais: {sorted(inscritos)}", flush=True)


def receber(sub, inscritos, segundos):
    limite = time.time() + segundos
    while sub.poll(max(0, int((limite - time.time()) * 1000))):
        _, dados = sub.recv_multipart()
        envelope = ler(dados)
        if envelope.publicacao.canal in inscritos:
            print(f"[publicacao em {envelope.publicacao.canal} | envio {hora(envelope.header.ts_envio_ms)}"
                  f" | recebimento {hora(agora_ms())}]\n{envelope}", flush=True)


def main():
    conectar()
    sub = contexto.socket(zmq.SUB)
    sub.connect(PROXY_ADDR)
    inscritos = set()

    login()
    while True:
        canais = listar_canais()
        if len(canais) < MINIMO_CANAIS:
            nome = f"canal-{random.randint(1, 20)}"
            if criar_canal(nome):
                canais.append(nome)
        if not canais:
            receber(sub, inscritos, 1)
            continue
        livres = [canal for canal in canais if canal not in inscritos]
        if len(inscritos) < MAXIMO_INSCRICOES and livres:
            inscrever(sub, inscritos, random.choice(livres))
        canal = random.choice(canais)
        for _ in range(10):
            publicar(canal, " ".join(random.choices(PALAVRAS, k=4)))
            receber(sub, inscritos, 1)


if __name__ == "__main__":
    main()
