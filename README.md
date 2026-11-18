# Sistema de troca de mensagens instantâneas

Projeto de Sistemas Distribuídos: uma versão simplificada de um BBS/IRC. Bots fazem login, criam canais e publicam mensagens; os servidores guardam tudo em disco, sincronizam os relógios, elegem um coordenador e replicam os dados entre si. Toda a comunicação usa ZeroMQ.

## Como rodar

É preciso ter Docker com Docker Compose.

```
docker compose up --build
```

Isso sobe 2 servidores, 2 bots e os serviços auxiliares. Os bots começam a trocar mensagens sozinhos, sem nenhuma interação.

Comandos úteis (em outro terminal):

| Comando | O que faz |
|---|---|
| `docker compose logs -f monitor` | mostra as mensagens dos canais como um chat |
| `docker compose logs -f servidor-1` | mostra tudo que um servidor envia e recebe |
| `docker compose stop servidor-2` | derruba um servidor (para ver a eleição de outro coordenador) |
| `docker compose start servidor-2` | religa o servidor (ele recupera os dados que perdeu) |
| `docker compose down` | encerra tudo |
| `./testes/verificar.sh` | testa todas as partes automaticamente (cerca de 2 minutos) |

Se o comando `docker compose` não existir, use `docker-compose`.

Os dados de cada servidor ficam em `dados/servidor-N/`.

O resultado dos testes fica em `testes/logs/`, com um arquivo por tema (`01-login-e-canais.log`, `02-publicacao.log`, `03-sincronizacao.log`, `04-eleicao.log` e `05-replicacao.log`), cada um com as verificações e os eventos daquele tema em ordem de horário.

## Serviços

| Serviço | Linguagem | Função | Portas |
|---|---|---|---|
| `broker` | Python | repassa as requisições dos bots aos servidores (REQ-REP) | 5555 (bots) e 5556 (servidores) |
| `proxy` | Python | repassa as publicações (PUB-SUB) | 5557 (publicam) e 5558 (assinam) |
| `referencia` | Python | dá o rank dos servidores, lista os disponíveis e recebe heartbeats | 5559 |
| `servidor-1` e `servidor-2` | Java (Spring Boot) | atendem os bots, guardam os dados, sincronizam e replicam | 5560 (entre servidores) |
| `bot-1` e `bot-2` | Python | usuários automáticos | |
| `monitor` | Python | exibe as publicações como um chat | |

## Escolhas

- **Linguagens:** Java com Spring Boot no servidor e Python no bot e nos serviços auxiliares. O Spring Boot é usado para organizar o servidor (injeção de dependências, configuração por variáveis de ambiente e validação dos dados); a comunicação é toda feita com ZeroMQ, sem HTTP.
- **Serialização:** Protocol Buffers (`proto/messages.proto`), em binário. Toda mensagem vai num `Envelope` com um cabeçalho que tem o id da mensagem, o remetente, o timestamp do envio e o relógio lógico.
- **Persistência:** cada servidor grava arquivos JSON formatados, em ordem de horário: `logins.json`, `canais.json` e `publicacoes.json`. Cada registro tem a data legível (horário de Brasília) e o timestamp em milissegundos. A gravação é feita num arquivo temporário que depois substitui o original, para o arquivo nunca ficar pela metade.

## Como funciona

**Login e canais.** O bot faz login (repete se der erro), lista os canais e cria um novo se houver menos de 5. Nomes vazios ou inválidos retornam `NOME_INVALIDO` e canais repetidos retornam `CANAL_JA_EXISTE`.

**Publicação.** O bot envia a mensagem ao servidor, que grava em `publicacoes.json`, publica no proxy usando o nome do canal como tópico e então responde `OK`. Cada bot se inscreve em até 3 canais, mostra o que recebe (canal, mensagem, horário do envio e do recebimento) e, em loop, escolhe um canal e envia 10 mensagens, uma por segundo.

**Relógios.** Todas as mensagens levam um relógio lógico de Lamport. O serviço de referência dá um rank a cada servidor e recebe um heartbeat a cada 10 mensagens; quem fica 30 segundos sem enviar sai da lista. A cada 15 mensagens, os servidores acertam o relógio com o coordenador pelo algoritmo de Berkeley (média dos relógios). Para isso ficar visível, os servidores começam com os relógios desajustados de propósito.

**Eleição.** O coordenador é o servidor com o maior rank (algoritmo do valentão). Se ele não responder, outro servidor faz uma eleição e anuncia o vencedor no tópico `servers`.

## Replicação

Como o broker divide as requisições entre os servidores, cada um receberia só uma parte dos dados. O método escolhido é a **replicação ativa com propagação assíncrona**: todos os servidores aceitam escritas e repassam cada uma aos outros.

- toda escrita (login, canal ou publicação) é publicada no tópico `replicacao`, e os outros servidores a gravam também
- ao iniciar ou voltar de uma queda, o servidor copia o histórico de outro servidor e grava o que estiver faltando

Assim, todos os servidores ficam com todos os dados, e a queda de um deles não faz perder nada.

Diferenças em relação ao método visto em aula:

- **sem ordem total:** os registros só são acrescentados, nunca alterados, então a ordem de chegada não muda o resultado; para exibir em ordem, basta ordenar pelo timestamp
- **consistência eventual:** o servidor responde ao bot sem esperar os outros gravarem, então por alguns milissegundos um registro pode existir em um servidor e ainda não no outro
- **sem duplicatas:** um registro que já existe não é gravado de novo
- **cópia do histórico:** foi acrescentada porque um servidor desligado não recebe o que foi replicado enquanto estava fora
