# Phantom-Code V2

## Objetivo

Rebuild incremental do app com um nucleo Linux testavel e uma UI que apenas
observa estado e dispara comandos. O app atual continua funcional durante a
migracao; nenhuma tela nova deve acessar `Process`, `LocalSocket` ou arquivos de
instalacao diretamente.

## Camadas

```text
ui/                         Compose: telas, navegacao e feedback
data/vm/core/               contratos e estado unico do runtime Linux
data/vm/                    adaptadores QEMU, terminal, instalador e scanner
data/                       workspace, Git, preferencias e secrets
```

## Fluxo minimo

```text
NoDistro -> Ready -> Starting -> Running -> Stopping -> Ready
                         \-> Error -----------/
```

O instalador so publica `Ready` depois de baixar, validar SHA-256, extrair,
validar arquitetura/arquivos e concluir a troca atomica do diretorio temporario.

## Regras

- Uma unica instancia do runtime controla o processo QEMU.
- A UI nunca cria ou encerra processos.
- A UI nunca escreve diretamente em sockets.
- Cada transicao de estado tem um dono e uma operacao concorrente cancelavel.
- Terminal, scanner e log consomem eventos do runtime sem competir pelo mesmo
  stream.
- O editor usa `spckio/spck-embed` apenas onde a licenca MIT for aplicavel; o
  APK do Spck nao e usado como fonte.
- O `spck-io/spck-cli` serve como referencia para protocolos de Git/terminal
  remoto, nao como dependencia Android obrigatoria.

## Milestones

1. Contratos e estado unico.
2. Adaptador QEMU com testes de ciclo de vida.
3. Sessao de terminal unica e entrada/saida verificaveis.
4. Instalador transacional de uma distro Phantom.
5. Migracao das telas Toolbox/Terminal.
6. Editor, Git e recursos extras depois do fluxo Linux estavel.
