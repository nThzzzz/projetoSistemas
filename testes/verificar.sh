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
LOGS=testes/logs
ARQUIVO=""
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
    local resultado
    shift
    if "$@" >/dev/null 2>&1; then
        resultado="  ✅ $descricao"
        OK=$((OK + 1))
    else
        resultado="  ❌ $descricao"
        FALHAS=$((FALHAS + 1))
    fi
    echo "$resultado"
    [ -n "$ARQUIVO" ] && echo "$resultado" >> "$ARQUIVO"
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

cabecalho() {
    printf '%s\n%s\n\n%s\n\nVerificacoes:\n' "$2" "$(printf '%s' "$2" | sed 's/./=/g')" "$3" > "$LOGS/$1.log"
}

preparar_logs() {
    rm -rf "$LOGS"
    mkdir -p "$LOGS/completo"
    cabecalho 01-login-e-canais "Login e canais (parte 1)" \
        "Login com nome valido e invalido, criacao de canal novo, repetido e invalido, listagem em todos os servidores e gravacao em disco."
    cabecalho 02-publicacao "Publicacao em canais (parte 2)" \
        "Publicacao pelo proxy, recebimento so nos canais inscritos, limite de 3 inscricoes por bot, minimo de 5 canais e monitor."
    cabecalho 03-sincronizacao "Sincronizacao (partes 3 e 4)" \
        "Relogio logico de Lamport, servico de referencia (rank, lista e heartbeat) e ajuste do relogio fisico pelo algoritmo de Berkeley."
    cabecalho 04-eleicao "Eleicao (parte 4)" \
        "O coordenador e o servidor de maior rank. Ele e derrubado e outro servidor precisa assumir sem parar o atendimento dos bots."
    cabecalho 05-replicacao "Replicacao (parte 5)" \
        "O servidor derrubado volta e recupera o historico, um servidor-3 e adicionado e no fim todos precisam ter os mesmos dados."
}

etapa() {
    ARQUIVO="$LOGS/$1.log"
    echo "$2"
}

marco() {
    echo "  ...$1"
    echo "$(date -u +%Y-%m-%dT%H:%M:%S.000) teste >>> $1" >> "$LOGS/completo/marcos.txt"
}

eventos() {
    printf '\nEventos (horario local):\n' >> "$LOGS/$1.log"
    grep -E "$2" "$LOGS/completo/eventos.txt" | LC_ALL=C awk -v fuso="$(date +%z)" '
        BEGIN {
            sinal = substr(fuso, 1, 1) == "-" ? -1 : 1
            deslocamento = sinal * (substr(fuso, 2, 2) * 3600 + substr(fuso, 4, 2) * 60)
        }
        {
            servico = $2
            mensagem = $0
            sub(/^[^ ]+ [^ ]+ /, "", mensagem)
            if (servico == "monitor") sub(/^[0-9:.]+  /, "", mensagem)
            if (ultima[servico] == mensagem) next
            ultima[servico] = mensagem
            t = substr($1, 12, 2) * 3600 + substr($1, 15, 2) * 60 + substr($1, 18) + deslocamento + 86400
            t = t - int(t / 86400) * 86400
            printf "  %02d:%02d:%06.3f  %-11s %s\n", int(t / 3600), int((t % 3600) / 60), t - int(t / 60) * 60, servico, mensagem
        }' >> "$LOGS/$1.log"
}

resumo_dados() {
    printf '\nRegistros gravados no fim do teste:\n' >> "$LOGS/05-replicacao.log"
    for servidor in servidor-1 servidor-2 servidor-3; do
        local linha="  $servidor:"
        for arquivo in logins canais publicacoes; do
            local total
            total=$(grep -c '"timestamp"' "$DADOS/$servidor/$arquivo.json" 2>/dev/null)
            linha="$linha $arquivo=${total:-0}"
        done
        echo "$linha" >> "$LOGS/05-replicacao.log"
    done
}

salvar_logs() {
    dc logs --no-color -t > "$LOGS/completo/compose.log" 2>&1
    docker logs -t "$NOVO" > "$LOGS/completo/servidor-3.log" 2>&1
    {
        sed -E 's/^([a-z0-9-]+)-1 +\| ([0-9T:.-]+)Z (.*)$/\2 \1 \3/' "$LOGS/completo/compose.log"
        sed -E 's/^([0-9T:.-]+)Z (.*)$/\1 servidor-3 \2/' "$LOGS/completo/servidor-3.log"
        cat "$LOGS/completo/marcos.txt" 2>/dev/null
    } | grep -E '^[0-9]{4}-' | LC_ALL=C sort > "$LOGS/completo/eventos.txt"
    eventos 01-login-e-canais '\[erro\] (LOGIN_REQ|CRIAR_CANAL_REQ)|conectado em tcp://broker'
    eventos 02-publicacao '#canal-|\[inscrito em|\[erro\] PUBLICAR_REQ'
    eventos 03-sincronizacao '\[(rank|cadastrado|removido|berkeley|relogio)\]'
    eventos 04-eleicao '>>>|\[(eleicao|coordenador|removido)\]|novo coordenador|sem resposta de tcp://servidor|sem conexao'
    eventos 05-replicacao '>>>|\[replicacao\]|\[cadastrado\] servidor-3'
    resumo_dados
}

encerrar() {
    docker rm -f "$NOVO" >/dev/null 2>&1
    dc down >/dev/null 2>&1
}

echo "Subindo os containers (projeto $PROJETO, dados em $DADOS)..."
encerrar
rm -rf "$DADOS"
preparar_logs
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
LC_ALL=C awk -v pasta="$LOGS" '
    /^Parte 1/ {arquivo = pasta "/01-login-e-canais.log"; next}
    /^Parte 2/ {arquivo = pasta "/02-publicacao.log"; next}
    /^Parte 3/ {arquivo = pasta "/03-sincronizacao.log"; next}
    /^Parte 4/ {arquivo = pasta "/04-eleicao.log"; next}
    arquivo {print >> arquivo}' "$SAIDA"
rm -f "$SAIDA"

etapa 01-login-e-canais "Parte 1: arquivos"
verificar "logins dos bots gravados com data" grep -qE '"usuario": "bot-1"' "$DADOS"/servidor-1/logins.json
verificar "registros com data legivel e timestamp" grep -qE '"data": "[0-9]{2}/[0-9]{2}/[0-9]{4} ' "$DADOS"/servidor-1/logins.json
verificar "canais gravados em disco" test -s "$DADOS/servidor-1/canais.json"

etapa 02-publicacao "Parte 2: logs e arquivos"
verificar "publicacoes gravadas em disco" test -s "$DADOS/servidor-1/publicacoes.json"
verificar "bot-1 se inscreve em 1 a 3 canais" inscricoes_ok bot-1
verificar "bot-2 se inscreve em 1 a 3 canais" inscricoes_ok bot-2
verificar "bots criam canais ate existirem pelo menos 5" esperar_ate 30 canais_suficientes
verificar "bot exibe canal, envio e recebimento das publicacoes" tem_log bot-1 '^\[publicacao em .* envio .* recebimento'
verificar "monitor exibe as publicacoes dos canais" tem_log monitor '#canal-'

etapa 03-sincronizacao "Sincronizacao: Lamport, referencia e Berkeley"
verificar "mensagens do bot tem relogio logico" tem_log bot-1 'lamport: [0-9]+'
verificar "referencia cadastrou servidor-1" tem_log referencia 'cadastrado\] servidor-1'
verificar "referencia cadastrou servidor-2" tem_log referencia 'cadastrado\] servidor-2'
verificar "servidores enviam heartbeat" esperar_ate 30 tem_log servidor-1 'heartbeat_req'
esperar_ate 60 tem_log servidor-1 '^\[(berkeley|relogio)\]'
COORDENADOR=$(coordenador_de servidor-1)
if [ "$COORDENADOR" = "servidor-1" ]; then OUTRO=servidor-2; else OUTRO=servidor-1; fi
verificar "coordenador ($COORDENADOR) aplica Berkeley" esperar_ate 30 tem_log "$COORDENADOR" '^\[berkeley\]'
verificar "$OUTRO ajusta o relogio pelo coordenador" esperar_ate 30 tem_log "$OUTRO" '^\[relogio\] ajuste'

etapa 04-eleicao "Eleicao"
verificar "servidores concordam sobre o coordenador ($COORDENADOR)" e_coordenador servidor-2 "$COORDENADOR"
verificar "monitor exibe o coordenador" tem_log monitor 'novo coordenador'
marco "derrubando $COORDENADOR"
dc stop "$COORDENADOR" >/dev/null 2>&1
verificar "$OUTRO assume a coordenacao" esperar_ate 60 e_coordenador "$OUTRO" "$OUTRO"
verificar "bot-1 continua sendo atendido" esperar_ate 20 recebendo bot-1
verificar "bot-2 continua sendo atendido" esperar_ate 20 recebendo bot-2
verificar "referencia remove $COORDENADOR por falta de heartbeat" esperar_ate 50 tem_log referencia "removido\] $COORDENADOR"

etapa 05-replicacao "Replicacao"
marco "religando $COORDENADOR"
dc start "$COORDENADOR" >/dev/null 2>&1
verificar "$COORDENADOR recupera o historico ao voltar" esperar_ate 60 recuperou_historico "$COORDENADOR"
verificar "$COORDENADOR volta a ser coordenador (maior rank)" esperar_ate 60 e_coordenador "$OUTRO" "$COORDENADOR"
marco "adicionando servidor-3"
mkdir -p "$DADOS/servidor-3"
docker run -d --name "$NOVO" --network "$REDE" --network-alias servidor-3 \
    -e TZ=America/Sao_Paulo -e SERVER_NAME=servidor-3 -e BROKER_ADDR=tcp://broker:5556 -e PROXY_ADDR=tcp://proxy:5557 \
    -e PROXY_SUB_ADDR=tcp://proxy:5558 -e REFERENCIA_ADDR=tcp://referencia:5559 -e DATA_DIR=/data \
    -v "$PWD/testes/dados/servidor-3:/data" bbs-servidor >/dev/null
verificar "servidor-3 copia o historico de outro servidor" esperar_ate 60 recuperou_historico servidor-3
verificar "servidor-3 vira coordenador (maior rank)" esperar_ate 60 e_coordenador servidor-1 servidor-3
verificar "servidor-3 recebe requisicoes dos bots" esperar_ate 30 tem_log servidor-3 'publicar_req'
marco "parando os bots para comparar os dados"
dc stop bot-1 bot-2 >/dev/null 2>&1
sleep 3
verificar "os 3 servidores tem os mesmos dados" dados_iguais

salvar_logs
encerrar
echo
echo "Resultado: $OK verificacoes ok, $FALHAS falhas"
echo "Logs por teste em $LOGS/ (01-login-e-canais.log ... 05-replicacao.log)"
[ "$FALHAS" -eq 0 ]
