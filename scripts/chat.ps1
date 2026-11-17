<#
.SYNOPSIS
    Chat no terminal com o assistente, para testes manuais em desenvolvimento. SOMENTE DADOS FICTÍCIOS.

.DESCRIPTION
    Conversa com a API local (rode antes: .\gradlew.bat bootRun). Lê a ASSISTANT_API_KEY do .env da pasta
    assistant-backend, envia o texto em UTF-8 (acentos funcionam) e mostra, depois de cada resposta, o status da
    conversa, a prioridade, o motivo do encaminhamento e a intenção.

    Comandos durante o chat:
      /pedidos     solicitações desta conversa (agendamento, informação, atendimento humano)
      /historico   mensagens gravadas desta conversa
      /fila        fila da equipe (conversas aguardando atendimento)
      /devolver    simula a equipe devolvendo a conversa ao assistente
      /encerrar    simula a equipe encerrando a conversa
      /nova        começa de novo com um contato fictício novo (sem cadastro)
      /ajuda       mostra os comandos
      /sair        termina

    Não use nomes, telefones nem dados de clientes reais (DA-17): o banco de desenvolvimento não tem as proteções
    da Etapa 7 e as mensagens vão para a OpenAI.

.PARAMETER Contato
    Identificador fictício do contato no canal. Repita o mesmo valor para voltar como o mesmo "cliente" (o cadastro
    aparece como CADASTRADO / A_RECONFIRMAR em uma conversa nova). Padrão: um novo a cada execução.

.PARAMETER BaseUrl
    Endereço da API. Padrão: http://localhost:8080

.EXAMPLE
    .\scripts\chat.ps1

.EXAMPLE
    .\scripts\chat.ps1 -Contato ficticio-ana
#>
param(
    [string]$Contato = ("ficticio-" + [guid]::NewGuid().ToString().Substring(0, 8)),
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# ---- chave da API, lida do .env (nunca exibida) ----
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"
$apiKey = $env:ASSISTANT_API_KEY
if (-not $apiKey -and (Test-Path $envFile)) {
    foreach ($line in [System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8)) {
        if ($line -match '^\s*ASSISTANT_API_KEY\s*=\s*(.+?)\s*$') {
            $apiKey = $Matches[1]
        }
    }
}
if (-not $apiKey) {
    Write-Host "ASSISTANT_API_KEY não encontrada no .env nem no ambiente." -ForegroundColor Red
    exit 1
}

# ---- chamadas HTTP em UTF-8 nos dois sentidos ----
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null)

    $request = [System.Net.HttpWebRequest]::Create($BaseUrl + $Path)
    $request.Method = $Method
    $request.Headers.Add("X-Api-Key", $apiKey)
    $request.Accept = "application/json"
    $request.Timeout = 120000
    if ($null -ne $Body) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
        $request.ContentType = "application/json; charset=utf-8"
        $request.ContentLength = $bytes.Length
        $stream = $request.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    } elseif ($Method -ne "GET") {
        $request.ContentLength = 0
    }
    try {
        $response = $request.GetResponse()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            throw "Não foi possível falar com $BaseUrl. A aplicação está no ar (.\gradlew.bat bootRun)?"
        }
    }
    $reader = New-Object System.IO.StreamReader($response.GetResponseStream(), [System.Text.Encoding]::UTF8)
    $text = $reader.ReadToEnd()
    $reader.Close()
    $status = [int]$response.StatusCode
    $response.Close()
    $json = $null
    if ($text) {
        $json = $text | ConvertFrom-Json
    }
    return [pscustomobject]@{ Status = $status; Body = $json }
}

function Open-Conversation([string]$contact) {
    $result = Invoke-Api POST "/api/conversations" @{ externalContactId = $contact }
    if ($result.Status -ge 300) {
        throw "Falha ao abrir a conversa (HTTP $($result.Status)). Confira a ASSISTANT_API_KEY do .env."
    }
    return $result.Body.id
}

function Show-Help {
    Write-Host "Comandos: /pedidos  /historico  /fila  /devolver  /encerrar  /nova  /ajuda  /sair" -ForegroundColor DarkGray
}

function Show-Requests([string]$conversationId) {
    $found = $false
    foreach ($status in "COLLECTING", "PENDING", "IN_PROGRESS", "RESOLVED", "CANCELLED") {
        $list = (Invoke-Api GET "/api/service-requests?status=$status").Body
        foreach ($r in @($list)) {
            if ($r -and $r.conversationId -eq $conversationId) {
                $found = $true
                $line = "  [$($r.type) / $($r.status)] $($r.summary)"
                if ($r.outcome) { $line += " | desfecho: $($r.outcome)" }
                Write-Host $line -ForegroundColor Cyan
            }
        }
    }
    if (-not $found) { Write-Host "  (nenhuma solicitação nesta conversa)" -ForegroundColor DarkGray }
}

function Show-History([string]$conversationId) {
    $page = (Invoke-Api GET "/api/conversations/$conversationId/messages?size=100").Body
    foreach ($m in @($page.content)) {
        $who = if ($m.author -eq "CUSTOMER") { "Você" } else { "Assistente" }
        Write-Host ("  {0}: {1}" -f $who, $m.content) -ForegroundColor Gray
    }
}

function Show-Queue {
    $queue = (Invoke-Api GET "/api/conversations?status=WAITING_HUMAN").Body
    if (-not $queue -or @($queue).Count -eq 0) {
        Write-Host "  (fila vazia)" -ForegroundColor DarkGray
        return
    }
    foreach ($q in @($queue)) {
        Write-Host ("  {0}  {1}  motivo={2}" -f $q.id, $q.priority, $q.handoffReason) -ForegroundColor Cyan
    }
}

# ---- conversa ----
Write-Host ""
Write-Host "Chat de teste do assistente — SOMENTE DADOS FICTÍCIOS" -ForegroundColor Yellow
Write-Host "Contato fictício: $Contato   API: $BaseUrl" -ForegroundColor DarkGray
Show-Help
$conversationId = Open-Conversation $Contato
Write-Host "Conversa: $conversationId" -ForegroundColor DarkGray
Write-Host ""

while ($true) {
    $text = Read-Host "Você"
    if ($null -eq $text) { break }
    $text = $text.Trim()
    if ($text -eq "") { continue }

    if ($text.StartsWith("/")) {
        $command = $text.ToLowerInvariant()
        if ($command -eq "/sair") {
            break
        } elseif ($command -eq "/ajuda") {
            Show-Help
        } elseif ($command -eq "/pedidos") {
            Show-Requests $conversationId
        } elseif ($command -eq "/historico") {
            Show-History $conversationId
        } elseif ($command -eq "/fila") {
            Show-Queue
        } elseif ($command -eq "/devolver") {
            $r = Invoke-Api POST "/api/conversations/$conversationId/return-to-assistant"
            Write-Host "  Equipe devolveu a conversa: status=$($r.Body.status) (HTTP $($r.Status))" -ForegroundColor Magenta
        } elseif ($command -eq "/encerrar") {
            $r = Invoke-Api POST "/api/conversations/$conversationId/close"
            Write-Host "  Equipe encerrou a conversa: status=$($r.Body.status) (HTTP $($r.Status))" -ForegroundColor Magenta
        } elseif ($command -eq "/nova") {
            $Contato = "ficticio-" + [guid]::NewGuid().ToString().Substring(0, 8)
            $conversationId = Open-Conversation $Contato
            Write-Host "  Novo contato fictício: $Contato   conversa: $conversationId" -ForegroundColor DarkGray
        } else {
            Write-Host "  Comando desconhecido." -ForegroundColor DarkGray
            Show-Help
        }
        continue
    }

    $result = Invoke-Api POST "/api/conversations/$conversationId/messages" @{ text = $text }
    switch ($result.Status) {
        200 {
            $b = $result.Body
            if ($b.conversationId -ne $conversationId) {
                $conversationId = $b.conversationId
                Write-Host "  (a conversa anterior expirou ou foi encerrada: nova conversa $conversationId)" -ForegroundColor DarkGray
            }
            Write-Host ""
            Write-Host "Assistente: " -ForegroundColor Green -NoNewline
            Write-Host $b.reply
            $meta = "  status=$($b.status) | prioridade=$($b.priority) | intenção=$($b.intent)"
            if ($b.handoffReason) { $meta += " | encaminhamento=$($b.handoffReason)" }
            if ($b.fallback) { $meta += " | RESPOSTA SEGURA (o modelo falhou ou foi reprovado)" }
            Write-Host $meta -ForegroundColor DarkGray
            Write-Host ""
        }
        409 { Write-Host "  409: outra mensagem chegou ao mesmo tempo; a sua ficou gravada sem resposta." -ForegroundColor Yellow }
        422 { Write-Host "  422: mensagem vazia ou com mais de 2.000 caracteres." -ForegroundColor Yellow }
        401 { Write-Host "  401: ASSISTANT_API_KEY inválida." -ForegroundColor Red; exit 1 }
        default { Write-Host "  Erro HTTP $($result.Status): $($result.Body.detail)" -ForegroundColor Red }
    }
}
