<#
.SYNOPSIS
    Chat no terminal com o assistente, para testes manuais em desenvolvimento. SOMENTE DADOS FICTÍCIOS.

.DESCRIPTION
    Conversa com a API local (rode antes: .\gradlew.bat bootRun). Lê a ASSISTANT_API_KEY do .env da pasta
    assistant-backend, envia o texto em UTF-8 (acentos funcionam) e mostra, depois de cada resposta, o status da
    conversa, a prioridade, o motivo do encaminhamento e a intenção.

    O script faz o papel do tutor. O lado da equipe (fila, assumir, responder, devolver, encerrar, solicitações)
    fica na aplicação web, com login (Etapa S2).

    Comandos durante o chat:
      /atualizar   mostra as mensagens novas (ex.: a resposta de um funcionário que assumiu a conversa)
      /historico   mensagens gravadas desta conversa
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
    Write-Host "Comandos: /atualizar  /historico  /nova  /ajuda  /sair" -ForegroundColor DarkGray
}

function Get-Messages([string]$conversationId) {
    return @((Invoke-Api GET "/api/conversations/$conversationId/messages?size=100").Body.content)
}

function Format-Author($m) {
    switch ($m.author) {
        "CUSTOMER" { return "Você" }
        "STAFF" { return "Equipe" }
        default { return "Assistente" }
    }
}

function Show-History([string]$conversationId) {
    foreach ($m in (Get-Messages $conversationId)) {
        Write-Host ("  {0}: {1}" -f (Format-Author $m), $m.content) -ForegroundColor Gray
    }
}

# Mostra só o que chegou depois da última mensagem vista (resposta da equipe, aviso de que alguém assumiu).
function Show-New([string]$conversationId) {
    $all = Get-Messages $conversationId
    $new = @($all | Select-Object -Skip $script:seen)
    $script:seen = $all.Count
    $shown = $false
    foreach ($m in $new) {
        if ($m.author -ne "CUSTOMER") {
            $shown = $true
            $color = if ($m.author -eq "STAFF") { "Magenta" } else { "Green" }
            Write-Host ("{0}: " -f (Format-Author $m)) -ForegroundColor $color -NoNewline
            Write-Host $m.content
        }
    }
    if (-not $shown) { Write-Host "  (nada novo)" -ForegroundColor DarkGray }
}

# ---- conversa ----
Write-Host ""
Write-Host "Chat de teste do assistente — SOMENTE DADOS FICTÍCIOS" -ForegroundColor Yellow
Write-Host "Contato fictício: $Contato   API: $BaseUrl" -ForegroundColor DarkGray
Show-Help
$conversationId = Open-Conversation $Contato
$script:seen = (Get-Messages $conversationId).Count
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
        } elseif ($command -eq "/atualizar") {
            Show-New $conversationId
        } elseif ($command -eq "/historico") {
            Show-History $conversationId
        } elseif ($command -eq "/nova") {
            $Contato = "ficticio-" + [guid]::NewGuid().ToString().Substring(0, 8)
            $conversationId = Open-Conversation $Contato
            $script:seen = (Get-Messages $conversationId).Count
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
            if ($null -eq $b.reply) {
                Write-Host "  (sem resposta automática: um funcionário está com a conversa. Use /atualizar)" -ForegroundColor Magenta
            } else {
                Write-Host "Assistente: " -ForegroundColor Green -NoNewline
                Write-Host $b.reply
            }
            $script:seen = (Get-Messages $conversationId).Count
            $meta = "  status=$($b.status) | prioridade=$($b.priority) | intenção=$($b.intent)"
            if ($b.handoffReason) { $meta += " | encaminhamento=$($b.handoffReason)" }
            if ($b.staffRequestReason) { $meta += " | pedido para a equipe=$($b.staffRequestReason)" }
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
