#!/usr/bin/env bash
# Demonstração de ponta a ponta, sem baixar o BAF:
#   1. compila o projeto (com os testes);
#   2. gera um CSV sintético com as colunas do BAF e drift conhecido no mês 5;
#   3. roda as 7 estratégias;
#   4. abre o painel em http://localhost:<porta>/viewer/.
#
# Os números são sintéticos: servem para validar o pipeline, NÃO para o artigo.
#
# Uso: scripts/demo.sh [opções]
#   --rows N        linhas do CSV sintético (padrão: 200000)
#   --seeds LISTA   sementes (padrão: 1,2,3)
#   --port P        porta do painel (padrão: 8000)
#   --no-serve      não sobe o painel; só gera os resultados
#   --skip-tests    compila sem rodar os testes
set -euo pipefail

ROWS=200000
SEEDS="1,2,3"
PORT=8000
SERVE=1
MVN_FLAGS=(-q)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rows) ROWS="${2:?}"; shift 2 ;;
    --seeds) SEEDS="${2:?}"; shift 2 ;;
    --port) PORT="${2:?}"; shift 2 ;;
    --no-serve) SERVE=0; shift ;;
    --skip-tests) MVN_FLAGS+=(-DskipTests); shift ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opção desconhecida: $1 (use --help)" >&2; exit 1 ;;
  esac
done

cd "$(dirname "$0")/.."

need() { command -v "$1" >/dev/null 2>&1 || { echo "Erro: '$1' não encontrado. $2" >&2; exit 1; }; }
need java "Instale o JDK 21 ou superior."
need mvn "Instale o Maven 3.9+."

JAVA_MAJOR=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
if [[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -lt 21 ]]; then
  echo "Erro: Java $JAVA_MAJOR encontrado; o projeto exige Java 21+." >&2
  exit 1
fi

DATA="data/synthetic_${ROWS}.csv"
OUT="results_sintetico"
JAR="target/fraud-drift-fairness-1.0.0.jar"

echo "==> [1/4] Compilando (mvn package)"
mvn "${MVN_FLAGS[@]}" package

echo "==> [2/4] Gerando dados sintéticos ($ROWS linhas)"
mkdir -p data
if [[ -f "$DATA" ]]; then
  echo "    $DATA já existe; reaproveitando."
else
  java -cp target/classes br.mestrado.frauddrift.synthetic.SyntheticBafGenerator "$DATA" "$ROWS" 42
fi

echo "==> [3/4] Rodando o experimento (seeds $SEEDS)"
java -Xmx2g -jar "$JAR" --data "$DATA" --out "$OUT" --seeds "$SEEDS"

echo
echo "Resultados em $OUT/ (summary.csv, results.csv, drift_events.csv, config.txt)"

if [[ "$SERVE" -eq 0 ]]; then
  echo "Painel: abra viewer/index.html e arraste $OUT/summary.csv e $OUT/drift_events.csv."
  exit 0
fi

echo "==> [4/4] Abrindo o painel"
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 não encontrado. Abra viewer/index.html no navegador e arraste os CSVs de $OUT/." >&2
  exit 0
fi

URL="http://localhost:${PORT}/viewer/?dados=../${OUT}"
echo "    $URL"
echo "    (Ctrl+C para encerrar)"
# Só localhost: o painel não fica visível para outras máquinas da rede.
exec python3 -m http.server "$PORT" --bind 127.0.0.1
