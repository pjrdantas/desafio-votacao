#requires -Version 7.0
param(
    [uri] $BaseUrl = 'http://localhost:8080',
    [Parameter(Mandatory=$true)] [string] $AccessToken
)
$ErrorActionPreference = 'Stop'
$api = $BaseUrl.AbsoluteUri.TrimEnd('/')
$correlationId = 'smoke-' + [guid]::NewGuid().ToString('N')
$headers = @{ 'X-Correlation-ID' = $correlationId; Authorization = 'Bearer ' + $AccessToken }

foreach ($probe in @('liveness', 'readiness')) {
    $health = Invoke-RestMethod -Uri "$api/actuator/health/$probe" -TimeoutSec 15
    if ($health.status -ne 'UP') { throw "Probe $probe não está UP." }
}

$pauta = Invoke-RestMethod -Method Post -Uri "$api/api/v1/pautas" -Headers $headers `
    -ContentType 'application/json' -Body '{"titulo":"Smoke de observabilidade"}' -TimeoutSec 15
Invoke-RestMethod -Method Post -Uri "$api/api/v1/pautas/$($pauta.id)/sessao" -Headers $headers `
    -ContentType 'application/json' -Body '{"duracaoMinutos":5}' -TimeoutSec 15 | Out-Null
$body = '{"escolha":"SIM"}'
$voto = Invoke-WebRequest -Method Post -Uri "$api/api/v1/pautas/$($pauta.id)/votos" -Headers $headers `
    -ContentType 'application/json' -Body $body -TimeoutSec 15 -SkipHttpErrorCheck
if ($voto.StatusCode -ne 201) { throw "Voto deveria retornar 201; recebido $($voto.StatusCode)." }
$duplicado = Invoke-WebRequest -Method Post -Uri "$api/api/v1/pautas/$($pauta.id)/votos" -Headers $headers `
    -ContentType 'application/json' -Body $body -TimeoutSec 15 -SkipHttpErrorCheck
$erro = $duplicado.Content | ConvertFrom-Json
if ($duplicado.StatusCode -ne 409 -or $erro.error -ne 'VOTO_DUPLICADO' -or $erro.correlationId -ne $correlationId) {
    throw 'Resposta ao voto duplicado ou correlação inválida.'
}
$resultado = Invoke-RestMethod -Uri "$api/api/v1/pautas/$($pauta.id)/resultado" -Headers $headers -TimeoutSec 15
if ($resultado.total -ne 1) { throw "Esperado exatamente um voto; recebido $($resultado.total)." }

[pscustomobject]@{
    Status = 'OK'
    CorrelationId = $correlationId
    PautaId = $pauta.id
    VotosAceitos = 1
    VotosDuplicados = 1
    Observacao = 'Fluxo local validado. Conferir este correlationId nos logs do Dynatrace e os traces associados.'
}
