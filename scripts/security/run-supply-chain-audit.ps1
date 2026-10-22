param(
  [string]$OutputDirectory = "",
  [switch]$SkipSecretScan,
  [switch]$SkipContainerScan,
  [ValidateSet('Release', 'Inventory')]
  [string]$ContainerScanScope = 'Inventory'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
  $OutputDirectory = Join-Path $repoRoot 'output\security'
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
  $OutputDirectory = Join-Path $repoRoot $OutputDirectory
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$toolRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'e-report-security-tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null

$isWindowsHost = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
  [System.Runtime.InteropServices.OSPlatform]::Windows
)
$isLinuxHost = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
  [System.Runtime.InteropServices.OSPlatform]::Linux
)
$platform = if ($isWindowsHost) { 'windows' } elseif ($isLinuxHost) { 'linux' } else { throw 'Only Windows and Linux are supported.' }
$tools = @{
  osv = @{
    Version = '2.5.0'
    Windows = @('https://github.com/google/osv-scanner/releases/download/v2.5.0/osv-scanner_windows_amd64.exe', '4342285bd8be36b9f113468f3eea86e7900befbcd19ca8dc6ac4f0f6cbe7c362', 'osv-scanner.exe', 'raw')
    Linux = @('https://github.com/google/osv-scanner/releases/download/v2.5.0/osv-scanner_linux_amd64', 'edcfc41d257db36148f065055655fe3fcfc434b0b423ea67468a84c207524e0c', 'osv-scanner', 'raw')
  }
  syft = @{
    Version = '1.51.0'
    Windows = @('https://github.com/anchore/syft/releases/download/v1.51.0/syft_1.51.0_windows_amd64.zip', 'fc5ffaeffb993576ece9c791da5a688fb2c8969a1479bbfe58583672c64da336', 'syft.exe', 'zip')
    Linux = @('https://github.com/anchore/syft/releases/download/v1.51.0/syft_1.51.0_linux_amd64.tar.gz', '2a2e837a2c8d59ec9af5472ee22d3b04ee463c4e44476ecf993fd1e5ab6ebc7f', 'syft', 'tar')
  }
  gitleaks = @{
    Version = '8.30.1'
    Windows = @('https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_windows_x64.zip', 'd29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e', 'gitleaks.exe', 'zip')
    Linux = @('https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz', '551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb', 'gitleaks', 'tar')
  }
  trivy = @{
    Version = '0.73.0'
    Windows = @('https://github.com/aquasecurity/trivy/releases/download/v0.73.0/trivy_0.73.0_windows-64bit.zip', 'd2d3ad5292aae470a03eb6506db86fce81b1894592b8451cadaf60eaa22f2025', 'trivy.exe', 'zip')
    Linux = @('https://github.com/aquasecurity/trivy/releases/download/v0.73.0/trivy_0.73.0_Linux-64bit.tar.gz', '2edd39da482bb4e9831962487b68f68e3928ec3137794757f54d00383d79547b', 'trivy', 'tar')
  }
  actionlint = @{
    Version = '1.7.12'
    Windows = @('https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_windows_amd64.zip', '6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9', 'actionlint.exe', 'zip')
    Linux = @('https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz', '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8', 'actionlint', 'tar')
  }
}

function Get-VerifiedTool([string]$name) {
  $definition = $tools[$name]
  $asset = if ($platform -eq 'windows') { $definition.Windows } else { $definition.Linux }
  $installDirectory = Join-Path $toolRoot "$name-$($definition.Version)-$platform-amd64"
  $executable = Join-Path $installDirectory $asset[2]
  $archiveName = [System.IO.Path]::GetFileName(([System.Uri]$asset[0]).AbsolutePath)
  $archive = Join-Path $installDirectory $archiveName
  New-Item -ItemType Directory -Force -Path $installDirectory | Out-Null

  if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -UseBasicParsing -Uri $asset[0] -OutFile $archive
  }
  $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actualSha256 -ne $asset[1]) {
    throw "$name archive SHA-256 mismatch: expected $($asset[1]), got $actualSha256"
  }

  if (-not (Test-Path -LiteralPath $executable)) {
    switch ($asset[3]) {
      'raw' { Copy-Item -LiteralPath $archive -Destination $executable }
      'zip' { Expand-Archive -LiteralPath $archive -DestinationPath $installDirectory -Force }
      'tar' {
        & tar -xzf $archive -C $installDirectory
        if ($LASTEXITCODE -ne 0) { throw "Cannot extract $archive" }
      }
      default { throw "Unsupported archive format $($asset[3])" }
    }
  }
  if (-not $isWindowsHost) {
    & chmod 0755 $executable
    if ($LASTEXITCODE -ne 0) { throw "Cannot mark $executable executable" }
  }
  return $executable
}

function Invoke-NpmAudit([string]$projectName, [string]$projectDirectory) {
  $auditFile = Join-Path $OutputDirectory "$projectName-npm-audit.json"
  Push-Location $projectDirectory
  try {
    & npm audit --json --registry=https://registry.npmjs.org 2>$null | Set-Content -LiteralPath $auditFile -Encoding UTF8
  } finally {
    Pop-Location
  }
  & node (Join-Path $PSScriptRoot 'validate-npm-audit.mjs') `
    --projectName $projectName `
    --projectRoot $projectDirectory `
    --auditJson $auditFile `
    --policy (Join-Path $repoRoot 'security\npm-audit-exceptions.json')
  if ($LASTEXITCODE -ne 0) { throw "npm audit policy rejected $projectName" }
}

$osv = Get-VerifiedTool 'osv'
$syft = Get-VerifiedTool 'syft'
$gitleaks = if ($SkipSecretScan) { $null } else { Get-VerifiedTool 'gitleaks' }
$trivy = if ($SkipContainerScan) { $null } else { Get-VerifiedTool 'trivy' }
$actionlint = Get-VerifiedTool 'actionlint'

$workflowFiles = @(
  Get-ChildItem -LiteralPath (Join-Path $repoRoot '.github\workflows') -File |
    Where-Object { $_.Extension -in @('.yml', '.yaml') } |
    ForEach-Object { $_.FullName.Substring($repoRoot.Length + 1) }
)
if ($workflowFiles.Count -eq 0) { throw 'No GitHub Actions workflow was found.' }
& $actionlint @workflowFiles
if ($LASTEXITCODE -ne 0) { throw 'actionlint rejected a GitHub Actions workflow.' }
& node (Join-Path $PSScriptRoot 'validate-supply-chain-contract.mjs') --repoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'Supply-chain source and pinning contract was rejected.' }

Invoke-NpmAudit 'e-report' (Join-Path $repoRoot 'e-report')
Invoke-NpmAudit 'e-report-app' (Join-Path $repoRoot 'e-report-app')

$releaseLocks = @()
foreach ($androidProject in @(
    @{ Name = 'android'; Lock = 'e-report-app/android/app/gradle.lockfile' },
    @{ Name = 'lite'; Lock = 'e-report-lite/app/gradle.lockfile' }
)) {
    $runtimeDirectory = Join-Path $OutputDirectory "$($androidProject.Name)-release-runtime"
    New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
    $runtimeLock = Join-Path $runtimeDirectory 'gradle.lockfile'
    $selectedDependencies = Get-Content -LiteralPath (Join-Path $repoRoot $androidProject.Lock) |
        Where-Object {
            if ($_ -notmatch '^(?<coordinate>[^#][^=]+)=(?<configurations>.*)$') { return $false }
            return @($matches.configurations -split ',') -contains 'releaseRuntimeClasspath'
        } | ForEach-Object { ($_ -replace '=.*$', '=releaseRuntimeClasspath') }
    $selectedDependencies += 'empty='
    Set-Content -LiteralPath $runtimeLock -Encoding UTF8 -Value $selectedDependencies
    $releaseLocks += "--lockfile=$runtimeLock"
}

$osvArguments = @(
  'scan',
  'source',
  "--config=$(Join-Path $repoRoot 'osv-scanner.toml')",
  "--lockfile=$(Join-Path $repoRoot 'e-report\package-lock.json')",
  "--lockfile=$(Join-Path $repoRoot 'e-report-app\package-lock.json')",
  "--lockfile=$(Join-Path $repoRoot 'e-report-backen\gradle.lockfile')",
  "--lockfile=$(Join-Path $repoRoot 'media-image-worker\requirements-runtime.txt')"
)
$osvArguments += $releaseLocks
$releaseDependencyReport = Join-Path $OutputDirectory 'osv-release.json'
& $osv @osvArguments '--format=json' "--output-file=$releaseDependencyReport"
if ($LASTEXITCODE -ne 0) { throw 'OSV-Scanner rejected the dependency graph.' }

$buildToolingReport = Join-Path $OutputDirectory 'osv-build-tooling.json'
& $osv scan source `
  --format json `
  "--output-file=$buildToolingReport" `
  "--lockfile=$(Join-Path $repoRoot 'e-report-app\android\app\gradle.lockfile')" `
  "--lockfile=$(Join-Path $repoRoot 'e-report-app\android\gradle\verification-metadata.xml')" `
  "--lockfile=$(Join-Path $repoRoot 'e-report-backen\gradle\verification-metadata.xml')" `
  "--lockfile=$(Join-Path $repoRoot 'e-report-lite\gradle\verification-metadata.xml')" `
  "--lockfile=$(Join-Path $repoRoot 'e-report-lite\app\gradle.lockfile')"
if (-not (Test-Path -LiteralPath $buildToolingReport)) { throw 'OSV build-tooling report was not generated.' }
& node (Join-Path $PSScriptRoot 'validate-osv-reports.mjs') `
  --releaseReport $releaseDependencyReport `
  --buildToolingReport $buildToolingReport `
  --policy (Join-Path $repoRoot 'security\osv-build-tooling-baseline.json')
if ($LASTEXITCODE -ne 0) { throw 'OSV release or build-tooling policy was rejected.' }

if ($gitleaks) {
  $gitleaksConfig = Join-Path $repoRoot '.gitleaks.toml'
  $gitHistoryReport = Join-Path $OutputDirectory 'gitleaks-git-history.json'
  $workingTreeReport = Join-Path $OutputDirectory 'gitleaks-working-tree.json'
  Set-Content -LiteralPath $gitHistoryReport -Encoding UTF8 -Value '[]'
  Set-Content -LiteralPath $workingTreeReport -Encoding UTF8 -Value '[]'
  & $gitleaks git $repoRoot --config $gitleaksConfig --redact --no-banner --report-format json --report-path $gitHistoryReport
  if ($LASTEXITCODE -ne 0) { throw 'Gitleaks found a secret in Git history.' }
  & $gitleaks dir $repoRoot --config $gitleaksConfig --redact --no-banner --report-format json --report-path $workingTreeReport
  if ($LASTEXITCODE -ne 0) { throw 'Gitleaks found a secret in the working tree.' }
}

if ($trivy) {
  & (Join-Path $PSScriptRoot 'audit-runtime-images.ps1') -RepoRoot $repoRoot -OutputDirectory $OutputDirectory -TrivyPath $trivy
  if ($ContainerScanScope -eq 'Inventory') {
    $containerPolicyPath = Join-Path $repoRoot 'security\container-vulnerability-exceptions.json'
    $containerPolicy = Get-Content -Raw -LiteralPath $containerPolicyPath | ConvertFrom-Json
    $containerReportDirectory = Join-Path $OutputDirectory 'trivy-images'
    New-Item -ItemType Directory -Force -Path $containerReportDirectory | Out-Null
    $imageNames = @($containerPolicy.images.PSObject.Properties.Name)
    foreach ($imageName in $imageNames) {
      $imagePolicy = $containerPolicy.images.PSObject.Properties[$imageName].Value
      if (-not $imagePolicy) { throw "Missing container scan policy for $imageName." }
      $reportPath = Join-Path $containerReportDirectory "$imageName.json"
      & $trivy image --quiet --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --format json --output $reportPath $imagePolicy.reference
      if ($LASTEXITCODE -ne 0) { throw "Trivy failed to scan $imageName." }
    }
    & node (Join-Path $PSScriptRoot 'validate-container-scan.mjs') `
      --repoRoot $repoRoot `
      --reportDirectory $containerReportDirectory `
      --policy $containerPolicyPath `
      --images ($imageNames -join ',')
    if ($LASTEXITCODE -ne 0) { throw 'Container vulnerability policy rejected one or more images.' }
  }
}

$sbom = Join-Path $OutputDirectory 'e-report-source.cdx.json'
$sourceVersion = (Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'e-report-app\app.json') | ConvertFrom-Json).expo.version
if ([string]::IsNullOrWhiteSpace($sourceVersion)) { throw 'Cannot resolve release version from e-report-app/app.json.' }
& $syft scan "dir:$repoRoot" `
  --exclude '**/.git/**' `
  --exclude '**/node_modules/**' `
  --exclude '**/build/**' `
  --exclude '**/dist/**' `
  --exclude '**/.gradle/**' `
  --exclude '**/output/**' `
  --exclude '**/logs/**' `
  --exclude '**/test-results/**' `
  --exclude '**/.playwright-cli/**' `
  --exclude '**/.playwright-mcp/**' `
  --source-name e-report `
  --source-version $sourceVersion `
  -o "cyclonedx-json=$sbom"
if ($LASTEXITCODE -ne 0) { throw 'Syft SBOM generation failed.' }

Write-Output "SUPPLY_CHAIN_AUDIT_PASSED sbom=$sbom releaseDependencyReport=$releaseDependencyReport buildToolingReport=$buildToolingReport"
