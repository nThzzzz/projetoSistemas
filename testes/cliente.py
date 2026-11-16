import random
import sys
import time
import uuid

import zmq

import messages_pb2 as pb

BROKER = "tcp://broker:5555"
PROXY = "tcp://proxy:5558"
REFERENCIA = "tcp://referencia:5559"

contexto = zmq.Context()
falhas = 0


def verificar(descricao, condicao):
    global falhas
    if not condicao:
        falhas += 1
    print(f"  {'✅' if condicao else '❌'} {descricao}", flush=True)


def novo(remetente="teste", lamport=0):
    envelope = pb.Envelope()
    envelope.header.msg_id = str(uuid.uuid4())
    envelope.header.remetente = remetente
    envelope.header.ts_envio_ms = int(time.time() * 1000)
    envelope.header.lamport = lamport
    return envelope


def requisitar(endereco, envelope):
    socket = contexto.socket(zmq.REQ)
    socket.setsockopt(zmq.RCVTIMEO, 5000)
    socket.setsockopt(zmq.LINGER, 0)
    socket.connect(endereco)
    socket.send(envelope.SerializeToString())
    try:
        resposta = pb.Envelope()
        resposta.ParseFromString(socket.recv())
        return resposta
    except zmq.Again:
        return pb.Envelope()
    finally:
        socket.close()


def login(nome):
    envelope = novo(nome)
    envelope.login_req.SetInParent()
    return requisitar(BROKER, envelope)


def criar(canal):
    envelope = novo()
    envelope.criar_canal_req.canal = canal
    return requisitar(BROKER, envelope)


def listar(lamport=0):
    envelope = novo(lamport=lamport)
    envelope.listar_canais_req.SetInParent()
    return requisitar(BROKER, envelope)


def publicar(canal, mensagem):
    envelope = novo()
    envelope.publicar_req.canal = canal
    envelope.publicar_req.mensagem = mensagem
    return requisitar(BROKER, envelope)


def receber(sub, segundos):
    recebidas = []
    limite = time.time() + segundos
    while sub.poll(max(0, int((limite - time.time()) * 1000))):
        _, dados = sub.recv_multipart()
        envelope = pb.Envelope()
        envelope.ParseFromString(dados)
        recebidas.append(envelope)
    return recebidas


def parte1(canal):
    print("Parte 1: login e canais")
    respostas = [login("teste"), login("")]
    verificar("login com nome valido retorna OK", respostas[0].login_rep.status == pb.OK)
    verificar("login com nome vazio retorna NOME_INVALIDO", respostas[1].login_rep.status == pb.NOME_INVALIDO)
    respostas.append(criar(canal))
    verificar("criar canal novo retorna OK", respostas[-1].criar_canal_rep.status == pb.OK)
    time.sleep(1)
    respostas.append(criar(canal))
    verificar("criar canal repetido retorna CANAL_JA_EXISTE", respostas[-1].criar_canal_rep.status == pb.CANAL_JA_EXISTE)
    respostas.append(criar("nome invalido!"))
    verificar("criar canal com nome invalido retorna NOME_INVALIDO", respostas[-1].criar_canal_rep.status == pb.NOME_INVALIDO)
    listagens = [listar() for _ in range(4)]
    respostas.extend(listagens)
    verificar("canal novo aparece na listagem de todos os servidores", all(canal in l.listar_canais_rep.canais for l in listagens))
    verificar("todas as respostas tem timestamp de envio", all(r.header.ts_envio_ms > 0 for r in respostas))


def parte2(canal):
    print("Parte 2: publicacao em canais")
    sub = contexto.socket(zmq.SUB)
    sub.connect(PROXY)
    sub.setsockopt_string(zmq.SUBSCRIBE, canal)
    time.sleep(1)
    outro = f"outro-{random.randint(1000, 9999)}"
    criar(outro)
    respostas = [publicar(canal, f"mensagem {i}") for i in range(3)]
    publicar(outro, "nao deve chegar")
    verificar("publicacoes validas retornam OK", all(r.publicar_rep.status == pb.OK for r in respostas))
    recebidas = receber(sub, 2)
    mensagens = sorted(e.publicacao.mensagem for e in recebidas)
    verificar("as 3 publicacoes chegaram ao inscrito pelo pub/sub", mensagens == [f"mensagem {i}" for i in range(3)])
    verificar("publicacao de canal nao inscrito nao chegou", "nao deve chegar" not in mensagens)
    verificar("publicacao recebida tem canal, autor e timestamp",
              bool(recebidas) and all(e.publicacao.canal == canal and e.publicacao.autor == "teste"
                                      and e.header.ts_envio_ms > 0 for e in recebidas))
    verificar("publicacao com mensagem vazia retorna erro", publicar(canal, "").publicar_rep.status == pb.NOME_INVALIDO)
    sub.close()


def parte3():
    print("Parte 3: relogio logico e referencia")
    resposta = listar(lamport=100000)
    verificar("servidor responde com lamport maior que o recebido", resposta.header.lamport > 100000)
    envelope = novo()
    envelope.listar_servidores_req.SetInParent()
    resposta = requisitar(REFERENCIA, envelope)
    servidores = {s.nome: s.rank for s in resposta.listar_servidores_rep.servidores}
    verificar("referencia lista servidor-1 e servidor-2", {"servidor-1", "servidor-2"} <= set(servidores))
    verificar("ranks sao diferentes", len(set(servidores.values())) == len(servidores))
    verificar("referencia responde com timestamp e lamport", resposta.header.ts_envio_ms > 0 and resposta.header.lamport > 0)
    return servidores


def parte4(servidores):
    print("Parte 4: coordenador")
    sub = contexto.socket(zmq.SUB)
    sub.connect(PROXY)
    sub.setsockopt_string(zmq.SUBSCRIBE, "servers")
    anuncios = []
    limite = time.time() + 40
    while not anuncios and time.time() < limite:
        anuncios = receber(sub, 1)
    verificar("coordenador anunciado no topico servers", bool(anuncios))
    if anuncios:
        esperado = max(servidores, key=servidores.get)
        verificar(f"coordenador e o servidor de maior rank ({esperado})", anuncios[-1].coordenador.nome == esperado)
    sub.close()


if __name__ == "__main__":
    canal = f"teste-{random.randint(1000, 9999)}"
    parte1(canal)
    parte2(canal)
    parte4(parte3())
    sys.exit(1 if falhas else 0)
