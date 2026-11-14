import os

import zmq


def main():
    xsub_porta = os.getenv("XSUB_PORT", "5557")
    xpub_porta = os.getenv("XPUB_PORT", "5558")

    contexto = zmq.Context()
    xsub = contexto.socket(zmq.XSUB)
    xsub.bind(f"tcp://*:{xsub_porta}")
    xpub = contexto.socket(zmq.XPUB)
    xpub.bind(f"tcp://*:{xpub_porta}")

    print(f"proxy ativo | publicadores: {xsub_porta} | inscritos: {xpub_porta}", flush=True)
    zmq.proxy(xsub, xpub)


if __name__ == "__main__":
    main()
