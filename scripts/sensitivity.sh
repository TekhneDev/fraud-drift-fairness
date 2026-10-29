#!/usr/bin/env bash
# Análise de sensibilidade dos detectores: roda o experimento para uma grade de parâmetros.
# Cada combinação grava seus resultados em uma pasta própria (results/sens_<parâmetros>).
#
# Uso: scripts/sensitivity.sh <caminho/Base.csv> [seeds]
# Exemplo: scripts/sensitivity.sh data/Base.csv 1,2,3,4,5
set -euo pipefail

DATA="${1:?informe o caminho do Base.csv}"
SEEDS="${2:-1,2,3,4,5}"
JAR="target/fraud-drift-fairness-1.0.0.jar"

if [[ ! -f "$JAR" ]]; then
  echo "JAR não encontrado. Rode antes: mvn package" >&2
  exit 1
fi

for delta in 0.0002 0.002 0.02; do
  java -Xmx4g -jar "$JAR" --data "$DATA" --seeds "$SEEDS" \
    --strategies ADWIN_GLOBAL,ADWIN_PER_CLASS \
    --adwin-delta "$delta" --out "results/sens_adwin_delta_${delta}"
done

for lambda in 5 10 25 50; do
  java -Xmx4g -jar "$JAR" --data "$DATA" --seeds "$SEEDS" \
    --strategies PH_GLOBAL,PH_PER_CLASS \
    --ph-lambda "$lambda" --out "results/sens_ph_lambda_${lambda}"
done

echo "Concluído. Compare os arquivos summary.csv e drift_events.csv de cada pasta results/sens_*"
