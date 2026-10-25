# Dívida em Dia

Aplicativo Android para controle de dívidas e vencimentos.

**Desenvolvido por Thiers Lima**

## Gerar APK pelo GitHub

1. Envie todos os arquivos desta pasta para a raiz de um repositório GitHub.
2. Abra a aba **Actions** do repositório.
3. Entre em **Gerar APK - Dívida em Dia**.
4. Clique em **Run workflow** (ou faça um novo push para a branch main/master).
5. Aguarde a compilação terminar.
6. Abra a execução concluída e baixe o artifact **DividaEmDia-v1.4-APK**.
7. Dentro do ZIP do artifact estará o arquivo `DividaEmDia-v1.4.apk`.

## Recursos

- Cadastro de dívidas
- Parcelas e contas recorrentes
- Calendário e histórico
- Status de vencimento e pagamento
- Lembretes nativos Android
- Modo escuro
- Categorias
- Backup/restauração
- Exportação CSV/PDF
- Uso offline

Package ID: `com.thierslima.dividaemdia`


## Versão 1.1
- Inicialização limpa, sem dívidas de demonstração.
- Nome do usuário opcional e personalizável.
- Histórico com seleção por mês/ano.
- Migração automática dos dados de demonstração da versão 1.0.

## Versão 1.2
- Som exclusivo “Dívida em Dia” incluído no APK.
- Três sons internos adicionais: Alerta suave, Aviso claro e Toque duplo.
- Opção de usar o som padrão do Android.
- Escolha de qualquer som de notificação disponível no celular.
- Importação de arquivo de áudio do aparelho para usar como notificação.
- Som geral do app ou som personalizado por dívida.
- Vibração configurável.
- Botão para ouvir o som e botão para testar a notificação imediatamente.
- Notificações mostram nome da dívida, valor e data de vencimento.


## Versão 1.3
- Card principal da tela inicial transformado em carrossel.
- “Pendente do mês” soma somente contas/parcelas com vencimento no mês atual.
- “Total pendente” mostra todas as contas e parcelas ainda não pagas, inclusive meses futuros.
- Indicadores permitem deslizar ou tocar para alternar entre os dois totais.


## Versão 1.4
- Nova tela de sons mais compacta e organizada.
- Prévia de notificação recolhida por padrão em “Como será o aviso?”.
- Pedido amigável de permissão de notificações apenas uma vez na primeira abertura.
- Se o usuário escolher “Agora não”, o app não insiste e permite ativar depois em Ajustes.
- Status real da permissão de notificações do Android.
- Botão compacto para testar uma notificação.
