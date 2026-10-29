# fraud-drift-fairness

Código do experimento do artigo:

> **Detectores de concept drift mantêm o desempenho e a justiça de um modelo antifraude quando o perfil das solicitações muda mês a mês?**

O projeto compara sete políticas de atualização de um modelo de detecção de fraude em abertura de contas bancárias, avaliadas mês a mês no *Bank Account Fraud Dataset Suite* (BAF, NeurIPS 2022), medindo ao mesmo tempo **desempenho** (AUC, recall a 5% de FPR) e **justiça** entre faixas etárias (razão de FPR e de TPR).

O núcleo é Java 21 puro, sem dependências de runtime: tudo o que gera um número do artigo (detectores, modelo, métricas) está neste repositório e pode ser auditado linha a linha.

---

## 1. Requisitos

- JDK 21 ou superior
- Maven 3.9+
- 4 GB de RAM livres para o BAF completo (1 milhão de linhas)

## 2. Dados

1. Baixe o BAF no Kaggle: <https://www.kaggle.com/datasets/sgpjesus/bank-account-fraud-dataset-neurips-2022> (exige login; licença CC BY-NC-ND 4.0, uso acadêmico).
2. Use o arquivo `Base.csv` (as variantes `Variant I` a `Variant V` servem para experimentos de viés controlado).
3. Coloque em `data/Base.csv` (a pasta `data/` está no `.gitignore`; não versione o dataset).

Colunas esperadas por padrão: `fraud_bool` (rótulo), `month` (0 a 7) e `customer_age`. Se o seu arquivo usar outros nomes, ajuste com `--label`, `--month` e `--age`.

## 3. Como rodar

```bash
mvn package                       # compila e roda os testes
java -Xmx4g -jar target/fraud-drift-fairness-1.0.0.jar --data data/Base.csv --out results --seeds 1,2,3,4,5
```

No BAF completo, as 7 estratégias com 5 seeds levam cerca de 1 minuto em um notebook comum.

### Testar sem o BAF (dados sintéticos)

```bash
java -cp target/classes br.mestrado.frauddrift.synthetic.SyntheticBafGenerator data/synthetic.csv 1000000 42
java -jar target/fraud-drift-fairness-1.0.0.jar --data data/synthetic.csv --out results_sintetico
```

O gerador cria um CSV com as mesmas colunas do BAF e um drift conhecido no mês 5. **Serve só para validar o pipeline; não use esses números no artigo.**

### Parâmetros

| Parâmetro | Padrão | Significado |
|---|---|---|
| `--data` | obrigatório | caminho do CSV |
| `--out` | `results` | pasta de saída |
| `--seeds` | `1,2,3,4,5` | sementes (ordem dentro do mês e sorteios) |
| `--strategies` | todas | lista separada por vírgula (ver seção 5) |
| `--warmup-months` | `3` | meses usados só para treinar o modelo inicial |
| `--warmup-epochs` | `3` | épocas nos treinos em lote |
| `--negative-rate` | `0.10` | fração de legítimos mantida no treino |
| `--target-fpr` | `0.05` | FPR do ponto de operação |
| `--protected-age` | `50` | idade a partir da qual o cliente é do grupo protegido |
| `--exclude-protected` | `false` | remove a idade das features |
| `--periodic-window` | `3` | meses usados no retreino periódico |
| `--drift-buffer` | `20000` | exemplos recentes usados no retreino após drift |
| `--lr` / `--l2` | `0.05` / `1e-5` | hiperparâmetros da regressão logística (AdaGrad) |
| `--adwin-delta` | `0.002` | confiança do ADWIN |
| `--ph-delta` / `--ph-lambda` | `0.005` / `50` | tolerância e limiar do Page-Hinkley |
| `--max-rows` | `0` (todas) | limita as linhas lidas, para testes rápidos |

## 4. Saídas

| Arquivo | Conteúdo |
|---|---|
| `results.csv` | uma linha por estratégia × seed × mês, com todas as métricas |
| `summary.csv` | média e desvio-padrão entre seeds, por estratégia × mês |
| `drift_events.csv` | cada alarme de drift com mês e posição no fluxo |
| `config.txt` | todos os parâmetros, versão do Java e lista de features (reprodutibilidade) |

Os CSVs usam ponto decimal e campo vazio para métrica indefinida, e abrem diretamente em Python (pandas), R ou Power BI.

## 5. Desenho experimental

**Protocolo prequential.** Os meses 0 a 2 treinam o modelo inicial, idêntico para todas as estratégias. Nos meses 3 a 7, cada solicitação é primeiro prevista e só depois tem o rótulo revelado. Como o BAF não informa a ordem dentro do mês, as linhas são embaralhadas dentro de cada mês com uma semente; cinco sementes medem a variabilidade.

**Estratégias comparadas**

| Nome | Política |
|---|---|
| `STATIC` | treina no aquecimento e nunca atualiza (linha de base do envelhecimento) |
| `PERIODIC` | retreina do zero no início de cada mês com os últimos 3 meses (prática comum na indústria) |
| `INCREMENTAL` | atualiza a cada rótulo, sem detector (adaptação passiva) |
| `ADWIN_GLOBAL` | incremental + ADWIN sobre a perda de todos os exemplos; no alarme, retreina com o buffer recente |
| `PH_GLOBAL` | idem, com Page-Hinkley |
| `ADWIN_PER_CLASS` | um ADWIN para a perda das fraudes e outro para a dos legítimos |
| `PH_PER_CLASS` | idem, com Page-Hinkley |
| `MOA_ARF`, `MOA_HAT` | opcionais, via MOA (seção 7) |

A comparação global × por classe testa uma hipótese específica: com ~1% de fraude, a perda média é dominada pelos legítimos, e uma piora só na detecção de fraudes pode passar despercebida por um monitor global.

**Métricas por mês**

- *AUC*: qualidade do ranking.
- *TPR a 5% de FPR*: fração das fraudes detectadas quando 5% dos legítimos são barrados (protocolo do artigo do BAF).
- *Razão de FPR* entre os grupos de idade (igualdade preditiva) e *razão de TPR* (igualdade de oportunidade), ambas como menor/maior (1 = paridade).
- *Métricas implantadas*: o limiar escolhido no mês anterior é aplicado no mês atual, como em produção. Mostra o efeito do drift sobre o próprio limiar.

## 6. Estrutura do código

```
src/main/java/br/mestrado/frauddrift/
├── App.java, ExperimentRunner.java   entrada e orquestração
├── config/      ExperimentConfig     parâmetros validados
├── data/        leitura colunar do CSV, rótulo/mês/grupo, ordem do fluxo
├── features/    FeatureEncoder       z-score + one-hot ajustados só no aquecimento
├── model/       OnlineLogisticRegression (AdaGrad + L2)
├── drift/       AdwinDetector, PageHinkleyDetector, monitores global e por classe
├── strategy/    as 7 políticas de atualização
├── eval/        PrequentialRunner    testar, depois treinar
├── metrics/     AUC, limiar por FPR, razões de justiça
├── report/      gravação dos CSVs
└── synthetic/   gerador de dados sintéticos para validação
src/moa/java/    adaptador opcional para classificadores do MOA
src/test/java/   26 testes (métricas, detectores, vazamento de dados, ponta a ponta)
```

## 7. Classificadores do MOA (opcional)

```bash
mvn -Pmoa package
java -Xmx4g -jar target/fraud-drift-fairness-1.0.0-with-moa.jar --data data/Base.csv --strategies STATIC,INCREMENTAL,MOA_ARF,MOA_HAT
```

Adiciona a Adaptive Random Forest e a Hoeffding Adaptive Tree como referências da literatura. Confira a versão mais recente do artefato `nz.ac.waikato.cms.moa:moa` no Maven Central e ajuste `moa.version` no `pom.xml`. **Este adaptador não pôde ser compilado no ambiente em que o projeto foi criado (sem acesso ao Maven Central); se der erro de compilação, o problema estará isolado em `src/moa/java`.** A ARF em 1 milhão de linhas leva bem mais tempo que a regressão logística.

## 8. Reprodutibilidade

- Toda aleatoriedade vem de sementes explícitas; mesma seed, mesmo resultado.
- O encoder e o modelo inicial usam apenas os meses de aquecimento (sem vazamento do futuro).
- `config.txt` registra parâmetros, versão do Java e a lista exata de features.
- Para a análise de sensibilidade dos detectores: `scripts/sensitivity.sh data/Base.csv`.

## 9. Limitações conhecidas

- Rótulo imediato: o protocolo assume que o rótulo chega logo após a predição. Em fraude real ele chega semanas depois (latência de verificação); estender o simulador com atraso de rótulo é um trabalho futuro natural.
- O BAF é sintético (gerado a partir de dados reais de um banco europeu) e cobre oito meses.
- A ordem dentro do mês é simulada.
- No ponto "implantado", estratégias que trocam de modelo herdam um limiar calibrado para o modelo anterior; isso é realista, mas penaliza retreinos e deve ser discutido junto com o ponto oráculo.

## 10. Referências principais

- Jesus, S. et al. *Turning the Tables: Biased, Imbalanced, Dynamic Tabular Datasets for ML Evaluation.* NeurIPS 2022 (Datasets and Benchmarks).
- Bifet, A.; Gavaldà, R. *Learning from Time-Changing Data with Adaptive Windowing.* SIAM SDM 2007.
- Page, E. S. *Continuous Inspection Schemes.* Biometrika, 1954.
- Gama, J. et al. *A Survey on Concept Drift Adaptation.* ACM Computing Surveys, 2014.
- Hardt, M.; Price, E.; Srebro, N. *Equality of Opportunity in Supervised Learning.* NeurIPS 2016.
- Bifet, A. et al. *MOA: Massive Online Analysis.* JMLR, 2010.
