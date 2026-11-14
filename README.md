# Sistema de troca de mensagens instantâneas

Projeto da disciplina de Sistemas Distribuídos: uma versão simplificada de um BBS/IRC em que bots fazem login, criam canais e trocam mensagens com servidores usando ZeroMQ.

## Estrutura

```
proto/      definição das mensagens (Protocol Buffers)
servidor/   servidor em Java (Spring Boot)
bot/        cliente (bot) em Python
broker/     broker REQ-REP em Python
```

## Linguagens

- **Java (Spring Boot)**: servidor
- **Python**: bot e broker

## Serialização

As mensagens são serializadas em binário com Protocol Buffers (`proto/messages.proto`). Toda mensagem é enviada dentro de um `Envelope`, que possui um `Header` com o id da mensagem, o remetente e o timestamp do envio.

## Comunicação

- `broker`: ROUTER na porta 5555 (clientes) e DEALER na porta 5556 (servidores)

| Requisição | Resposta | Erros possíveis |
|---|---|---|
| `LoginReq` (usa o `remetente` do header) | `LoginRep` | `NOME_INVALIDO` |
| `CriarCanalReq` | `CriarCanalRep` | `NOME_INVALIDO`, `CANAL_JA_EXISTE` |
| `ListarCanaisReq` | `ListarCanaisRep` com a lista de nomes | |

Regras:

- nome de usuário: não pode ser vazio e tem no máximo 32 caracteres
- nome de canal: letras, números, `_` e `-`, com no máximo 32 caracteres
- não é permitido criar dois canais com o mesmo nome
- em caso de erro no login, o bot repete a requisição até conseguir

## Bot

1. faz login no serviço
2. lista os canais disponíveis
3. se existirem menos do que 5 canais, cria um novo com nome aleatório (`canal-N`)
4. repete os passos 2 e 3 a cada 2 segundos

## Persistência

Cada servidor grava seus dados no diretório definido por `DATA_DIR` (mapeado para `dados/<nome-do-servidor>` no compose), sem compartilhar arquivos com os outros servidores. O formato é JSON Lines, com um registro por linha, sempre anexado ao final do arquivo:

- `logins.jsonl`: usuário e timestamp de cada login
- `canais.jsonl`: nome do canal, usuário que criou e timestamp da criação

## Execução

```
docker compose up --build
```
