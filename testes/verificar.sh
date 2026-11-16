#!/usr/bin/env bash
set -u
cd "$(dirname "$0")/.."

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi
PROJETO=bbsteste
REDE=${PROJETO}_default
NOVO=${PROJETO}-servidor-3
export DADOS=./testes/dados
OK=0
FALHAS=0

dc() {
    $COMPOSE -p "$PROJETO" "$@"
}

logs() {
    if [ "$1" = "servidor-3" ]; then
        docker logs "$NOVO" 2>&1
    else
        dc logs --no-color --no-log-prefix "$@" 2>/dev/null
    fi
}

verificar() {
    local descricao=$1
    shift
    if "$@" >/dev/null 2>&1; then
        echo "  ✅ $descricao"
        OK=$((OK + 1))
    else
        echo "  ❌ $descricao"
        FALHAS=$((FALHAS + 1))
    fi
}

esperar_ate() {
    local limite=$1
    shift
    for _ in $(seq "$limite"); do
        "$@" >/dev/null 2>&1 && return 0
        sleep 1
    done
    return 1
}

tem_log() {
    logs "$1" | grep -qE "$2"
}

coordenador_de() {
    logs "$1" | grep '^\[coordenador\]' | tail -1 | awk '{print $2}'
}

e_coordenador() {
    [ "$(coordenador_de "$1")" = "$2" ]
}

inscricoes_ok() {
    local total
    total=$(logs "$1" | grep -c '^\[inscrito em')
    [ "$total" -ge 1 ] && [ "$total" -le 3 ]
}

canais_suficientes() {
    [ "$(grep -o '"nome": "[^"]*"' "$DADOS/servidor-1/canais.json" | sort -u | wc -l)" -ge 5 ]
}

recuperou_historico() {
    logs "$1" | grep '^\[replicacao\]' | tail -1 | grep -qE '^\[replicacao\] [1-9][0-9]* registros novos'
}

recebendo() {
    [ "$(dc logs --no-color --no-log-prefix --since 10s "$1" 2>/dev/null | grep -c '^\[recebido\]')" -gt 0 ]
}

dados_iguais() {
    for arquivo in logins canais publicacoes; do
        local base
        base=$(cksum < "$DADOS/servidor-1/$arquivo.json")
        for servidor in servidor-2 servidor-3; do
            [ "$base" = "$(cksum < "$DADOS/$servidor/$arquivo.json")" ] || return 1
        done
    done
}

salvar_logs() {
    mkdir -p testes/logs
    dc logs --no-color > testes/logs/compose.log 2>&1
    docker logs "$NOVO" > testes/logs/servidor-3.log 2>&1
}

encerrar() {
    docker rm -f "$NOVO" >/dev/null 2>&1
    dc down >/dev/null 2>&1
}

echo "Subindo os containers (projeto $PROJETO, dados em $DADOS)..."
encerrar
rm -rf "$DADOS"
if ! dc up --build -d >/dev/null 2>&1; then
    echo "Falha ao subir os containers"
    exit 1
fi
if ! esperar_ate 90 tem_log servidor-1 'conectado em' || ! esperar_ate 90 tem_log servidor-2 'conectado em'; then
    echo "Os servidores nao iniciaram"
    encerrar
    exit 1
fi

SAIDA=$(mktemp)
docker run --rm -i --network "$REDE" bbs-bot python - < testes/cliente.py | tee "$SAIDA"
OK=$((OK + $(grep -c '✅' "$SAIDA")))
FALHAS=$((FALHAS + $(grep -c '❌' "$SAIDA")))
rm -f "$SAIDA"

echo "Partes 1 a 3: logs e arquivos"
verificar "logins dos bots gravados com data" grep -qE '"usuario": "bot-1"' "$DADOS"/servidor-1/logins.json
verificar "registros com data legivel e timestamp" grep -qE '"data": "[0-9]{2}/[0-9]{2}/[0-9]{4} ' "$DADOS"/servidor-1/logins.json
verificar "canais gravados em disco" test -s "$DADOS/servidor-1/canais.json"
verificar "publicacoes gravadas em disco" test -s "$DADOS/servidor-1/publicacoes.json"
verificar "bot-1 se inscreve em 1 a 3 canais" inscricoes_ok bot-1
verificar "bot-2 se inscreve em 1 a 3 canais" inscricoes_ok bot-2
verificar "bots criam canais ate existirem pelo menos 5" esperar_ate 30 canais_suficientes
verificar "bot exibe canal, envio e recebimento das publicacoes" tem_log bot-1 '^\[publicacao em .* envio .* recebimento'
verificar "mensagens do bot tem relogio logico" tem_log bot-1 'lamport: [0-9]+'
verificar "referencia cadastrou servidor-1" tem_log referencia 'cadastrado\] servidor-1'
verificar "referencia cadastrou servidor-2" tem_log referencia 'cadastrado\] servidor-2'
verificar "servidores enviam heartbeat" tem_log servidor-1 'heartbeat_req'
verificar "monitor exibe as publicacoes dos canais" tem_log monitor '#canal-'
verificar "monitor exibe o coordenador" tem_log monitor 'novo coordenador'

echo "Parte 4: eleicao e Berkeley"
esperar_ate 60 tem_log servidor-1 '^\[(berkeley|relogio)\]'
COORDENADOR=$(coordenador_de servidor-1)
if [ "$COORDENADOR" = "servidor-1" ]; then OUTRO=servidor-2; else OUTRO=servidor-1; fi
verificar "servidores concordam sobre o coordenador ($COORDENADOR)" e_coordenador servidor-2 "$COORDENADOR"
verificar "coordenador aplica Berkeley" esperar_ate 30 tem_log "$COORDENADOR" '^\[berkeley\]'
verificar "$OUTRO ajusta o relogio pelo coordenador" esperar_ate 30 tem_log "$OUTRO" '^\[relogio\] ajuste'
echo "  ...derrubando $COORDENADOR"
dc stop "$COORDENADOR" >/dev/null 2>&1
verificar "$OUTRO assume a coordenacao" esperar_ate 60 e_coordenador "$OUTRO" "$OUTRO"
verificar "bot-1 continua sendo atendido" esperar_ate 20 recebendo bot-1
verificar "bot-2 continua sendo atendido" esperar_ate 20 recebendo bot-2
verificar "referencia remove $COORDENADOR por falta de heartbeat" esperar_ate 50 tem_log referencia "removido\] $COORDENADOR"

echo "Parte 5: replicacao"
echo "  ...religando $COORDENADOR"
dc start "$COORDENADOR" >/dev/null 2>&1
verificar "$COORDENADOR recupera o historico ao voltar" esperar_ate 60 recuperou_historico "$COORDENADOR"
verificar "$COORDENADOR volta a ser coordenador (maior rank)" esperar_ate 60 e_coordenador "$OUTRO" "$COORDENADOR"
echo "  ...adicionando servidor-3"
mkdir -p "$DADOS/servidor-3"
docker run -d --name "$NOVO" --network "$REDE" --network-alias servidor-3 \
    -e SERVER_NAME=servidor-3 -e BROKER_ADDR=tcp://broker:5556 -e PROXY_ADDR=tcp://proxy:5557 \
    -e PROXY_SUB_ADDR=tcp://proxy:5558 -e REFERENCIA_ADDR=tcp://referencia:5559 -e DATA_DIR=/data \
    -v "$PWD/testes/dados/servidor-3:/data" bbs-servidor >/dev/null
verificar "servidor-3 copia o historico de outro servidor" esperar_ate 60 recuperou_historico servidor-3
verificar "servidor-3 vira coordenador (maior rank)" esperar_ate 60 e_coordenador servidor-1 servidor-3
verificar "servidor-3 recebe requisicoes dos bots" esperar_ate 30 tem_log servidor-3 'publicar_req'
dc stop bot-1 bot-2 >/dev/null 2>&1
sleep 3
verificar "os 3 servidores tem os mesmos dados" dados_iguais

salvar_logs
encerrar
echo
echo "Resultado: $OK verificacoes ok, $FALHAS falhas (logs em testes/logs)"
[ "$FALHAS" -eq 0 ]
