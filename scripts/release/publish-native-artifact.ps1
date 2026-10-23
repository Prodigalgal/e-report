param(
    [Parameter(Mandatory=$true)][ValidateSet('NATIVE','LITE')][string]$Edition,
    [Parameter(Mandatory=$true)][ValidateSet('test','production')][string]$Environment,
    [Parameter(Mandatory=$true)][string]$ArtifactDirectory,
    [Parameter(Mandatory=$true)][string]$SourceSha,
    [Parameter(Mandatory=$true)][string]$Username,
    [Parameter(Mandatory=$true)][Security.SecureString]$Password
)
$ErrorActionPreference='Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$appRoot = Join-Path $repoRoot $(if ($Edition -eq 'NATIVE') { 'e-report-app' } else { 'e-report-lite' })
$contract = if ($Edition -eq 'NATIVE') {
    $sourceContract = & node (Join-Path $appRoot 'scripts/verify-version-contract.mjs') --app-root $appRoot
    if ($LASTEXITCODE -ne 0) { throw 'Native 源码版本契约校验失败。' }
    $verified = ($sourceContract -replace '^VERSION_CONTRACT=', '') | ConvertFrom-Json
    [pscustomobject]@{ version=$verified.versionName; versionCode=$verified.versionCode; runtimeVersion=$verified.runtimeVersion }
} else { Get-Content -LiteralPath (Join-Path $appRoot 'native-release.json') -Raw | ConvertFrom-Json }
$manifest = Get-Content -LiteralPath (Join-Path $ArtifactDirectory 'release-manifest.json') -Raw | ConvertFrom-Json
$apk = Join-Path $ArtifactDirectory 'app-release.apk'
$checksum = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($manifest.sourceSha -ne $SourceSha -or $manifest.version -ne $contract.version -or
    $manifest.versionCode -ne $contract.versionCode -or $manifest.sha256 -ne $checksum -or $manifest.edition -ne $Edition) { throw 'APK 与本次源码及客户端契约不一致。' }
if ($Edition -eq 'NATIVE' -and ($manifest.artifactSourceSha -ne $SourceSha -or $manifest.businessVersion -ne $contract.version)) {
    throw 'Native APK 未包含当前候选的完整业务代码，停止发布。'
}
$origin = if ($Environment -eq 'test') { 'https://e-report-test.fantong.eu.org:60443' } else { 'https://e-report.fantong.eu.org:60443' }
$api = "$origin/api"
$query = "edition=$Edition&platform=ANDROID&channel=stable"
function Assert-PublicDistribution {
    & node (Join-Path $PSScriptRoot 'verify-client-distribution.mjs') --api-base-url $api --edition $Edition `
        --expected-sha256 $checksum --expected-size ([string]$manifest.size)
    if ($LASTEXITCODE -ne 0) { throw 'APK 公开分发验收失败。' }
}
if ($Environment -eq 'production') {
    $accepted = Invoke-RestMethod -Uri "https://e-report-test.fantong.eu.org:60443/api/app-updates/latest?$query" -TimeoutSec 30
    if ($accepted.versionCode -ne $contract.versionCode -or $accepted.sha256 -ne $checksum) { throw '生产只能提升测试环境已发布的同一不可变 APK。' }
}
$previous = Invoke-RestMethod -Uri "$api/app-updates/latest?$query" -TimeoutSec 30
if ($previous.edition -ne $Edition) { throw '目标 Backend 尚未就绪或返回了其他客户端，停止发布。' }
if ($previous.versionCode -eq $contract.versionCode) {
    if ($previous.versionName -eq $contract.version -and $previous.runtimeVersion -eq $contract.runtimeVersion -and $previous.sha256 -eq $checksum) {
        Write-Host "APK_ALREADY_PUBLISHED edition=$Edition version=$($contract.version) sha256=$checksum"
        Assert-PublicDistribution
        return
    }
    throw '相同构建号已登记不同 APK，禁止覆盖。'
}
$minimum = if ($null -ne $previous.minSupportedVersionCode) { [int]$previous.minSupportedVersionCode } else { 0 }
& (Join-Path $repoRoot 'e-report-app/scripts/publish-app-release.ps1') `
    -AppRoot $appRoot -Edition $Edition -Username $Username -Password $Password -BaseUrl $api -TransferBaseUrl $api `
    -ApkPath $apk -VersionCode ([int]$contract.versionCode) -VersionName $contract.version -RuntimeVersion $contract.runtimeVersion `
    -MinSupportedVersionCode $minimum -ForceUpdate:([bool]$previous.forceUpdate) `
    -ReleaseNotes $(if ($Edition -eq 'NATIVE') { '完整安装包，内置本次发布的业务功能；兼容的已安装版本通过签名 OTA 更新。' } else { '独立 Lite 客户端，网页功能自动更新；支持附件、定位、环境设置和壳升级。' })
$published = Invoke-RestMethod -Uri "$api/app-updates/latest?$query" -TimeoutSec 30
if ($published.edition -ne $Edition -or $published.versionCode -ne $contract.versionCode -or $published.sha256 -ne $checksum) { throw '发布中心未返回目标客户端的正确 APK。' }
Assert-PublicDistribution
Write-Host "APK_PUBLISHED edition=$Edition version=$($contract.version) sha256=$checksum"
