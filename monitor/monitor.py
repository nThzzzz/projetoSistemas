import os
from datetime import datetime

import zmq

import messages_pb2 as pb

PROXY_ADDR = os.getenv("PROXY_ADDR", "tcp://proxy:5558")


def hora(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%H:%M:%S.%f")[:-3]


def main():
    contexto = zmq.Context()
    sub = contexto.socket(zmq.SUB)
    sub.connect(PROXY_ADDR)
    sub.setsockopt_string(zmq.SUBSCRIBE, "")
    print("monitor ativo: exibindo as publicacoes de todos os canais", flush=True)
    coordenador = ""

    while True:
        _, dados = sub.recv_multipart()
        envelope = pb.Envelope()
        envelope.ParseFromString(dados)
        tipo = envelope.WhichOneof("body")
        if tipo == "publicacao":
            publicacao = envelope.publicacao
            print(f"{hora(envelope.header.ts_envio_ms)}  #{publicacao.canal:<10} {publicacao.autor}: {publicacao.mensagem}  ({envelope.header.remetente})", flush=True)
        elif tipo == "coordenador" and envelope.coordenador.nome != coordenador:
            coordenador = envelope.coordenador.nome
            print(f"{hora(envelope.header.ts_envio_ms)}  ★ novo coordenador: {coordenador} (rank {envelope.coordenador.rank})", flush=True)


if __name__ == "__main__":
    main()
