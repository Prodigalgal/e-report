param(
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][Security.SecureString]$Password,
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$ArchivePath,
    [Parameter(Mandatory = $true)][string]$UpdateId
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'release-session.psm1') -Force
$archive = Get-Item -LiteralPath $ArchivePath
if ($archive.Length -le 0 -or $archive.Length -gt 50MB) { throw 'OTA 归档必须在 50MB 以内。' }
$session = $null
try {
    $session = New-AuthenticatedWebSession $BaseUrl $Username $Password
    $client = $session.Client
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(40)
    for ($attempt = 1; $attempt -le 3; $attempt += 1) {
        $stream = [IO.File]::OpenRead($archive.FullName)
        $body = [Net.Http.MultipartFormDataContent]::new()
        try {
            $content = [Net.Http.StreamContent]::new($stream)
            $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/zip')
            $body.Add($content, 'archive', 'ota-update.zip')
            $response = $client.PostAsync("$($session.ApiBaseUrl)/admin/app-updates/ota", $body).GetAwaiter().GetResult()
            $published = Read-JsonResponse $response
            if ($published.id -ne $UpdateId) { throw 'OTA 发布标识与归档不一致。' }
        } finally { $body.Dispose(); $stream.Dispose() }
        $leaseDeadline = [DateTimeOffset]::UtcNow.AddMinutes(16)
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            $response = $client.GetAsync("$($session.ApiBaseUrl)/admin/app-updates/ota/$UpdateId").GetAwaiter().GetResult()
            $status = Read-JsonResponse $response
            if ($status.status -eq 'PUBLISHED') {
                Write-Host "OTA_PUBLISHED id=$UpdateId version=$($status.versionName) runtime=$($status.runtimeVersion) sha256=$($status.sha256)"
                return
            }
            if ($status.status -eq 'FAILED') { Write-Warning "OTA 分发失败，将重试同一不可变归档（$attempt/3）。"; break }
            if ($status.status -ne 'PUBLISHING') { throw 'OTA 发布状态无效。' }
            if ([DateTimeOffset]::UtcNow -ge $leaseDeadline) {
                # 服务端的发布租约为 15 分钟；进程重启后重新上传同一归档以恢复分发。
                Write-Warning "OTA 分发租约已到期，将重新提交同一归档（$attempt/3）。"
                break
            }
            Start-Sleep -Seconds 5
        }
        if ([DateTimeOffset]::UtcNow -ge $deadline) { throw '等待 OTA 资源分发超时；当前生效版本保持不变。' }
        Start-Sleep -Seconds 10
    }
    throw 'OTA 资源分发三次失败，请查看发布记录和服务日志。'
} finally { Close-AuthenticatedWebSession $session }
