param(
  [Parameter(Mandatory = $true)][string]$RepoRoot,
  [Parameter(Mandatory = $true)][string]$OutputDirectory,
  [Parameter(Mandatory = $true)][string]$TrivyPath
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'Release runtime auditing requires Docker to build the actual runtime layers.'
}
$auditId = [guid]::NewGuid().ToString('N')
$contextRoot = Join-Path $OutputDirectory "runtime-context-$auditId"
$reportRoot = Join-Path $OutputDirectory 'trivy-images'
New-Item -ItemType Directory -Force -Path $contextRoot, $reportRoot | Out-Null

foreach ($component in @('backend', 'frontend')) {
  $context = Join-Path $contextRoot $component
  New-Item -ItemType Directory -Force -Path $context | Out-Null
  if ($component -eq 'backend') {
    $jarPath = Join-Path $context 'app.jar'
    $archive = [System.IO.Compression.ZipFile]::Open($jarPath, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
      $entry = $archive.CreateEntry('META-INF/MANIFEST.MF')
      $writer = [System.IO.StreamWriter]::new($entry.Open())
      try { $writer.Write("Manifest-Version: 1.0`n`n") } finally { $writer.Dispose() }
    } finally { $archive.Dispose() }
  } else {
    $dist = Join-Path $context 'dist'
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Set-Content -LiteralPath (Join-Path $dist 'index.html') -Value '<!doctype html><title>Runtime audit</title>'
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'e-report/nginx.conf') -Destination (Join-Path $context 'nginx.conf')
  }

  $image = "e-report-runtime-audit-${component}:$auditId"
  $dockerfile = Join-Path $RepoRoot "deploy/dedicated-cloud/Dockerfile.$component-runtime"
  $buildLog = Join-Path $OutputDirectory "runtime-$component-build.log"
  try {
    & docker build --pull --platform linux/amd64 --file $dockerfile --tag $image $context *> $buildLog
    if ($LASTEXITCODE -ne 0) {
      Get-Content -Tail 40 -LiteralPath $buildLog
      throw "Cannot build the $component runtime audit image."
    }
    $report = Join-Path $reportRoot "$component-runtime.json"
    & $TrivyPath image --image-src docker --quiet --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 --format json --output $report $image
    if ($LASTEXITCODE -ne 0) { throw "The built $component runtime contains unreviewed fixable High/Critical vulnerabilities; see $report." }
    Write-Output "RUNTIME_CONTAINER_AUDIT_PASSED component=$component report=$report"
  } finally {
    & docker image rm --force $image *> $null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary audit image cleanup failed: $image" }
  }
}
