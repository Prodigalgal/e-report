param(
    [Parameter(Mandatory = $true)]
    [string]$Username,

    [Parameter(Mandatory = $true)]
    [Security.SecureString]$Password,

    [string]$BaseUrl = "https://e-report.fantong.eu.org:60443/api",

    [string]$TransferBaseUrl = "https://e-report.fantong.eu.org:60443/api",

    [string]$ApkPath = "android/app/build/outputs/apk/release/app-release.apk",

    [string]$AppRoot = (Join-Path $PSScriptRoot '..'),

    [Parameter(Mandatory = $true)]
    [int]$VersionCode,

    [string]$VersionName = "1.0.0",

    [string]$RuntimeVersion = "",

    [string]$Channel = "stable",

    [ValidateSet('NATIVE', 'LITE')]
    [string]$Edition = 'NATIVE',

    [int]$MinSupportedVersionCode = 0,

    [switch]$ForceUpdate,

    [string]$ReleaseNotes = ""
)

$ErrorActionPreference = "Stop"
$ChunkSize = 2MB
Import-Module (Join-Path $PSScriptRoot 'release-session.psm1') -Force

function Resolve-ExistingFile([string]$PathValue) {
    $resolvedPath = Resolve-Path -LiteralPath $PathValue -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "APK 文件不存在: $PathValue"
    }
    return $resolvedPath.Path
}

function Get-Sha256Hex([string]$PathValue) {
    $stream = [System.IO.File]::OpenRead($PathValue)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($stream)
        return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

function Send-ChunkWithRetry($Client, [string]$Url, [byte[]]$Buffer, [int]$Length, [long]$Start, [long]$End, [long]$TotalSize) {
    $attempt = 0
    while ($true) {
        $attempt += 1
        $content = [System.Net.Http.ByteArrayContent]::new($Buffer, 0, $Length)
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
        $content.Headers.ContentRange = [System.Net.Http.Headers.ContentRangeHeaderValue]::new($Start, $End, $TotalSize)
        try {
            $response = $Client.PutAsync($Url, $content).GetAwaiter().GetResult()
            return Read-JsonResponse $response
        } catch {
            if ($attempt -ge 5) {
                throw
            }
            Start-Sleep -Seconds ([Math]::Min($attempt * 2, 8))
        }
    }
}

function Find-RegisteredRelease($Client, [string]$ApiBaseUrl, [string]$ReleaseChannel, [int]$ReleaseVersionCode) {
    $encodedChannel = [Uri]::EscapeDataString($ReleaseChannel)
    for ($pageNumber = 0; $pageNumber -lt 100; $pageNumber += 1) {
        $url = "$ApiBaseUrl/admin/app-updates?edition=$Edition&platform=ANDROID&channel=$encodedChannel&sort=versionCode,desc&page=$pageNumber&size=100"
        $response = $Client.GetAsync($url).GetAwaiter().GetResult()
        $page = Read-JsonResponse $response
        if ($null -eq $page.PSObject.Properties['content']) {
            throw '发布中心未返回预期的分页列表。'
        }
        $releases = @($page.content)
        foreach ($release in $releases) {
            if ([int]$release.versionCode -eq $ReleaseVersionCode) {
                return $release
            }
        }
        if ($page.last -or $releases.Count -eq 0 -or [int]$releases[-1].versionCode -lt $ReleaseVersionCode) {
            return $null
        }
    }
    throw '发布记录超过检索范围，无法确认 versionCode 唯一性。'
}

function Assert-ReleaseArtifact($Release, [int]$ExpectedCode, [string]$ExpectedName,
    [string]$ExpectedRuntime, [string]$ExpectedChannel, [long]$ExpectedSize, [string]$ExpectedSha256) {
    if ($Release.edition -ne $Edition -or $Release.platform -ne 'ANDROID' -or $Release.channel -ne $ExpectedChannel -or
        [int]$Release.versionCode -ne $ExpectedCode -or $Release.versionName -ne $ExpectedName -or
        $Release.runtimeVersion -ne $ExpectedRuntime -or [long]$Release.fileSize -ne $ExpectedSize -or
        $Release.sha256 -ne $ExpectedSha256) {
        throw '发布中心已登记相同 versionCode 的不同制品，禁止覆盖或复用版本号。'
    }
}

function Resume-ReleaseSynchronization($Client, [string]$ApiBaseUrl, $Release) {
    $response = $Client.GetAsync("$ApiBaseUrl/admin/app-updates/r2?edition=$Edition").GetAwaiter().GetResult()
    $overview = Read-JsonResponse $response
    $releaseItem = $overview.items | Where-Object { $_.releaseId -eq $Release.id } | Select-Object -First 1
    if ($releaseItem -and (($releaseItem.published -and $releaseItem.status -eq 'SYNCED') -or
        $releaseItem.status -eq 'SYNCING')) {
        return
    }
    $response = $Client.PostAsync("$ApiBaseUrl/admin/app-updates/r2/releases/$($Release.id)",
        (New-JsonContent @{})).GetAwaiter().GetResult()
    $null = Read-JsonResponse $response
    Write-Host "RELEASE_SYNC_RESUMED release=$($Release.id)"
}

$resolvedApkPath = Resolve-ExistingFile $ApkPath
$resolvedAppRoot = (Resolve-Path -LiteralPath $AppRoot).Path
$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$normalizedTransferBaseUrl = $TransferBaseUrl.TrimEnd("/")
$normalizedChannel = $Channel.Trim().ToLowerInvariant()
if ([string]::IsNullOrWhiteSpace($RuntimeVersion)) {
    $runtimePrefix = if ($Edition -eq 'LITE') { 'android-lite' } else { 'android' }
    $RuntimeVersion = "$runtimePrefix-$VersionName-native$VersionCode"
}

$versionVerifier = Join-Path $resolvedAppRoot 'scripts/verify-version-contract.mjs'
& node $versionVerifier `
    --app-root $resolvedAppRoot `
    --apk $resolvedApkPath `
    --expected-version-code $VersionCode `
    --expected-version-name $VersionName `
    --expected-runtime-version $RuntimeVersion
if ($LASTEXITCODE -ne 0) {
    throw "Android 版本、APK manifest 或签名契约校验失败，退出码 $LASTEXITCODE。"
}

$apkFile = Get-Item -LiteralPath $resolvedApkPath
$apkSha256 = Get-Sha256Hex $resolvedApkPath

$primarySession = $null
$transferSession = $null
try {
    $primarySession = New-AuthenticatedWebSession $normalizedBaseUrl $Username $Password
    $client = $primarySession.Client
    $published = Find-RegisteredRelease $client $normalizedBaseUrl $normalizedChannel $VersionCode
    if ($null -ne $published) {
        Assert-ReleaseArtifact $published $VersionCode $VersionName $RuntimeVersion $normalizedChannel $apkFile.Length $apkSha256
        Write-Host "RELEASE_ARTIFACT_REUSED version=$VersionName code=$VersionCode sha256=$apkSha256"
        Resume-ReleaseSynchronization $client $normalizedBaseUrl $published
    } else {
        if ($normalizedTransferBaseUrl -eq $normalizedBaseUrl) {
            $transferSession = $primarySession
        } else {
            $transferSession = New-AuthenticatedWebSession $normalizedTransferBaseUrl $Username $Password
        }
        $transferClient = $transferSession.Client

        $initPayload = [ordered]@{
            purpose = "APP_RELEASE"
            fileName = $apkFile.Name
            contentType = "application/vnd.android.package-archive"
            totalSize = [long]$apkFile.Length
            sha256 = $apkSha256
            chunkSize = $ChunkSize
        }

        $initUrl = "$normalizedTransferBaseUrl/file-transfers/uploads"
        Write-Host "Uploading APK in chunks to $initUrl"
        Write-Host "APK: $resolvedApkPath"
        Write-Host "SHA-256: $apkSha256"

        $uploadId = $null
        $uploadCompleted = $false
        try {
            $sessionResponse = $transferClient.PostAsync($initUrl, (New-JsonContent $initPayload)).GetAwaiter().GetResult()
            $session = Read-JsonResponse $sessionResponse
            $uploadId = $session.uploadId
            $serverChunkSize = [int64]$ChunkSize
            if ($session.chunkSize -and [int64]$session.chunkSize -gt 0) {
                $serverChunkSize = [int64]$session.chunkSize
            }

            $buffer = [byte[]]::new($serverChunkSize)
            $stream = [System.IO.File]::OpenRead($resolvedApkPath)
            try {
                $uploadedBytes = 0L
                $chunkIndex = 0
                while ($uploadedBytes -lt $apkFile.Length) {
                    $read = $stream.Read($buffer, 0, $buffer.Length)
                    if ($read -le 0) {
                        throw "读取 APK 文件中断。"
                    }
                    $start = $uploadedBytes
                    $end = $uploadedBytes + $read - 1
                    $chunkUrl = "$normalizedTransferBaseUrl/file-transfers/uploads/$uploadId/chunks/$chunkIndex"
                    $chunkResult = Send-ChunkWithRetry $transferClient $chunkUrl $buffer $read $start $end $apkFile.Length
                    $uploadedBytes = [Math]::Max([int64]$chunkResult.receivedBytes, $end + 1)
                    $percent = [Math]::Round(($uploadedBytes * 100.0) / $apkFile.Length, 1)
                    Write-Progress -Activity "上传 APK 分片" -Status "$percent%" -PercentComplete $percent
                    Write-Host "APK_UPLOAD_PROGRESS chunk=$chunkIndex bytes=$uploadedBytes total=$($apkFile.Length) percent=$percent"
                    $chunkIndex += 1
                }
            } finally {
                $stream.Dispose()
                Write-Progress -Activity "上传 APK 分片" -Completed
            }

            $completeUrl = "$normalizedTransferBaseUrl/file-transfers/uploads/$uploadId/complete"
            $completeResponse = $transferClient.PostAsync($completeUrl, (New-JsonContent @{})).GetAwaiter().GetResult()
            $completedUpload = Read-JsonResponse $completeResponse
            $uploadCompleted = $true
        } catch {
            if ($uploadId -and -not $uploadCompleted) {
                try {
                    $abortUrl = "$normalizedTransferBaseUrl/file-transfers/uploads/$uploadId"
                    $abortResponse = $transferClient.DeleteAsync($abortUrl).GetAwaiter().GetResult()
                    $abortResponse.Dispose()
                    Write-Warning "发布失败，已终止上传会话 $uploadId。"
                } catch {
                    Write-Warning "发布失败，且上传会话 $uploadId 自动清理失败，请人工检查。"
                }
            }
            throw
        }

        $metadata = [ordered]@{
            edition = $Edition
            platform = "ANDROID"
            channel = $normalizedChannel
            versionCode = $VersionCode
            versionName = $VersionName
            runtimeVersion = $RuntimeVersion
            minSupportedVersionCode = $MinSupportedVersionCode
            forceUpdate = [bool]$ForceUpdate
            releaseNotes = $ReleaseNotes
            completedUploadId = $completedUpload.uploadId
        }

        $targetUrl = "$normalizedBaseUrl/admin/app-updates"
        Write-Host "Publishing Android release to $targetUrl"
        $publishResponse = $client.PostAsync($targetUrl, (New-JsonContent $metadata)).GetAwaiter().GetResult()
        $published = Read-JsonResponse $publishResponse
        ($published | ConvertTo-Json -Depth 10)
    }

    Write-Host "Waiting for server-side object storage synchronization"
    $syncDeadline = [DateTimeOffset]::UtcNow.AddMinutes(20)
    while ([DateTimeOffset]::UtcNow -lt $syncDeadline) {
        $overviewResponse = $client.GetAsync("$normalizedBaseUrl/admin/app-updates/r2?edition=$Edition").GetAwaiter().GetResult()
        $overview = Read-JsonResponse $overviewResponse
        $releaseItem = $overview.items | Where-Object { $_.releaseId -eq $published.id } | Select-Object -First 1
        if ($releaseItem -and $releaseItem.published -and $releaseItem.status -eq 'SYNCED') {
            break
        }
        if ($releaseItem -and $releaseItem.status -eq 'SYNC_FAILED') {
            throw "安装包已登记，但对象存储同步失败；请在发布中心查看错误并重试。"
        }
        Start-Sleep -Seconds 3
    }
    if (-not $releaseItem -or -not $releaseItem.published -or $releaseItem.status -ne 'SYNCED') {
        throw "安装包已登记，但等待对象存储同步超时；当前版本尚未向客户端发布。"
    }

    Write-Host ""
    $releaseOrigin = ([Uri]$normalizedBaseUrl).GetLeftPart([System.UriPartial]::Authority)
    $downloadPath = if ($Edition -eq 'LITE') { 'android-lite' } else { 'android' }
    Write-Host "App 安装包发布完成。下载入口: $releaseOrigin/download/$downloadPath"
} finally {
    if ($null -ne $transferSession -and -not [object]::ReferenceEquals($transferSession, $primarySession)) {
        Close-AuthenticatedWebSession $transferSession
    }
    Close-AuthenticatedWebSession $primarySession
}
