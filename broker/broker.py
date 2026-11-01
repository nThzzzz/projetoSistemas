import os

import zmq


def main():
    frontend_porta = os.getenv("FRONTEND_PORT", "5555")
    backend_porta = os.getenv("BACKEND_PORT", "5556")

    contexto = zmq.Context()
    frontend = contexto.socket(zmq.ROUTER)
    frontend.bind(f"tcp://*:{frontend_porta}")
    backend = contexto.socket(zmq.DEALER)
    backend.bind(f"tcp://*:{backend_porta}")

    print(f"broker ativo | clientes: {frontend_porta} | servidores: {backend_porta}", flush=True)
    zmq.proxy(frontend, backend)


if __name__ == "__main__":
    main()
