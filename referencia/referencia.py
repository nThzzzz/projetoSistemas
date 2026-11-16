import os
import time
import uuid

import zmq

import messages_pb2 as pb

PORTA = os.getenv("PORT", "5559")
NOME = os.getenv("NAME", "referencia")
TEMPO_LIMITE = float(os.getenv("HEARTBEAT_TIMEOUT", "30"))

lamport = 0
ranks = {}
ativos = {}


def agora_ms():
    return int(time.time() * 1000)


def receber(dados):
    global lamport
    envelope = pb.Envelope()
    envelope.ParseFromString(dados)
    lamport = max(lamport, envelope.header.lamport)
    print(f"[recebido]\n{envelope}", flush=True)
    return envelope


def finalizar(envelope):
    global lamport
    lamport += 1
    envelope.header.msg_id = str(uuid.uuid4())
    envelope.header.remetente = NOME
    envelope.header.ts_envio_ms = agora_ms()
    envelope.header.lamport = lamport
    return envelope


def registrar(nome):
    if nome not in ranks:
        ranks[nome] = len(ranks) + 1
        print(f"[cadastrado] {nome} com rank {ranks[nome]}", flush=True)
    ativos[nome] = time.time()


def remover_inativos():
    limite = time.time() - TEMPO_LIMITE
    for nome in [nome for nome, visto in ativos.items() if visto < limite]:
        del ativos[nome]
        print(f"[removido] {nome} sem heartbeat", flush=True)


def tratar(requisicao):
    nome = requisicao.header.remetente
    resposta = pb.Envelope()
    tipo = requisicao.WhichOneof("body")
    if tipo == "rank_req":
        registrar(nome)
        resposta.rank_rep.rank = ranks[nome]
    elif tipo == "listar_servidores_req":
        remover_inativos()
        for servidor in sorted(ativos, key=ranks.get):
            resposta.listar_servidores_rep.servidores.add(nome=servidor, rank=ranks[servidor])
    elif tipo == "heartbeat_req":
        registrar(nome)
        resposta.heartbeat_rep.status = pb.OK
    return resposta


def main():
    contexto = zmq.Context()
    socket = contexto.socket(zmq.REP)
    socket.bind(f"tcp://*:{PORTA}")
    print(f"referencia ativa na porta {PORTA}", flush=True)

    while True:
        if socket.poll(1000):
            resposta = finalizar(tratar(receber(socket.recv())))
            socket.send(resposta.SerializeToString())
            print(f"[enviado]\n{resposta}", flush=True)
        remover_inativos()


if __name__ == "__main__":
    main()
