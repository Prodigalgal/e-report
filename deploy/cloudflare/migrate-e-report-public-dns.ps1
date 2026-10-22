param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('UpsertNew', 'RetireNew', 'RetireLegacy')]
  [string]$Operation,

  [string]$Ipv4 = '36.212.129.231',

  [string]$TokenFile = 'D:\WorkSpace\Project\服务器管理\private\cloudflare\tokens.env'
)

$ErrorActionPreference = 'Stop'

$newZone = 'fantong.eu.org'
$newDomain = 'e-report.fantong.eu.org'
$newTokenKey = 'CF_ACCOUNT_3101557049_ALL_ZONES_DNS_TOKEN'
$legacyZone = 'omnnu.xyz'
$legacyDomains = @('e-report.omnnu.xyz', 'e-report-relay.omnnu.xyz')
$legacyTokenKey = 'CF_ACCOUNT_3489459218_DNS_TOKEN'

function Read-EnvValue([string]$Name) {
  if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) {
    throw "Cloudflare token file not found: $TokenFile"
  }
  $line = Get-Content -LiteralPath $TokenFile |
    Where-Object { $_ -match "^$([Regex]::Escape($Name))=" } |
    Select-Object -First 1
  if ([string]::IsNullOrWhiteSpace($line)) {
    throw "Cloudflare token $Name not found in $TokenFile"
  }
  return ($line -replace "^$([Regex]::Escape($Name))=", '').Trim().Trim('"').Trim("'")
}

function Invoke-CloudflareApi([string]$Token, [string]$Method, [string]$Uri, [object]$Body = $null) {
  $parameters = @{
    Method = $Method
    Uri = $Uri
    Headers = @{ Authorization = "Bearer $Token"; 'Content-Type' = 'application/json' }
    TimeoutSec = 30
  }
  if ($null -ne $Body) {
    $parameters.Body = $Body | ConvertTo-Json -Depth 5 -Compress
  }
  $response = Invoke-RestMethod @parameters
  if (-not $response.success) {
    throw "Cloudflare API request failed: $Method $Uri"
  }
  return $response
}

function Get-ZoneId([string]$Token, [string]$ZoneName) {
  $response = Invoke-CloudflareApi $Token Get "https://api.cloudflare.com/client/v4/zones?name=$ZoneName&per_page=1"
  if (-not $response.result -or $response.result.Count -ne 1) {
    throw "Cloudflare zone not found or ambiguous: $ZoneName"
  }
  return $response.result[0].id
}

function Remove-DomainRecords([string]$Token, [string]$ZoneId, [string]$Domain) {
  $encodedDomain = [Uri]::EscapeDataString($Domain)
  $response = Invoke-CloudflareApi $Token Get "https://api.cloudflare.com/client/v4/zones/$ZoneId/dns_records?name=$encodedDomain&per_page=100"
  foreach ($record in $response.result) {
    if ($record.name -eq $Domain -and $record.type -in @('A', 'AAAA', 'CNAME')) {
      Invoke-CloudflareApi $Token Delete "https://api.cloudflare.com/client/v4/zones/$ZoneId/dns_records/$($record.id)" | Out-Null
    }
  }
}

function Get-DomainRecords([string]$Token, [string]$ZoneId, [string]$Domain) {
  $encodedDomain = [Uri]::EscapeDataString($Domain)
  $response = Invoke-CloudflareApi $Token Get "https://api.cloudflare.com/client/v4/zones/$ZoneId/dns_records?name=$encodedDomain&per_page=100"
  return @($response.result | Where-Object { $_.name -eq $Domain })
}

if ($Operation -eq 'UpsertNew') {
  $token = Read-EnvValue $newTokenKey
  $zoneId = Get-ZoneId $token $newZone
  Remove-DomainRecords $token $zoneId $newDomain
  $record = @{
    type = 'A'
    name = $newDomain
    content = $Ipv4
    ttl = 120
    proxied = $false
    comment = 'e-report production ingress on HTTPS 60443'
  }
  Invoke-CloudflareApi $token Post "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records" $record | Out-Null
  $createdRecords = @(Get-DomainRecords $token $zoneId $newDomain)
  $recordCountMatches = $createdRecords.Count -eq 1
  $recordContentMatches = $recordCountMatches -and $createdRecords[0].type -eq 'A' -and $createdRecords[0].content -eq $Ipv4
  $recordProxyMatches = $recordCountMatches -and $createdRecords[0].proxied -eq $false
  if (-not ($recordCountMatches -and $recordContentMatches -and $recordProxyMatches)) {
    throw "Cloudflare DNS verification failed for $newDomain."
  }
  Write-Host "Configured $newDomain -> $Ipv4 (DNS only, record $($createdRecords[0].id))."
  exit 0
}

if ($Operation -eq 'RetireNew') {
  $token = Read-EnvValue $newTokenKey
  $zoneId = Get-ZoneId $token $newZone
  Remove-DomainRecords $token $zoneId $newDomain
  Write-Host "Retired DNS records for $newDomain."
  exit 0
}

$token = Read-EnvValue $legacyTokenKey
$zoneId = Get-ZoneId $token $legacyZone
foreach ($domain in $legacyDomains) {
  Remove-DomainRecords $token $zoneId $domain
  Write-Host "Retired legacy DNS records for $domain."
}
