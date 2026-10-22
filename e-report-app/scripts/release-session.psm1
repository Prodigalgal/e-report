Add-Type -AssemblyName System.Net.Http

function Read-JsonResponse($Response) {
    try {
        $body = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $Response.IsSuccessStatusCode) {
            throw "HTTP $([int]$Response.StatusCode): $body"
        }
        return $body | ConvertFrom-Json
    } finally {
        $Response.Dispose()
    }
}

function New-JsonContent($Payload) {
    $json = $Payload | ConvertTo-Json -Compress -Depth 10
    return [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
}

function Get-PlainTextPassword([Security.SecureString]$SecurePassword) {
    return [System.Net.NetworkCredential]::new('', $SecurePassword).Password
}

function New-ReleasePublisherWebEnvironment {
    $fingerprintSource = 'e-report-release-publisher:web-env-v1'
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($fingerprintSource))
        $fingerprintHash = ([System.BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
    return [ordered]@{
        schemaVersion = 1
        fingerprintHash = $fingerprintHash
        browserName = 'e-report-release-publisher'
        browserVersion = '1.0'
        osName = 'Windows'
        osVersion = [System.Environment]::OSVersion.VersionString
        deviceName = '发布工具'
        manufacturer = 'Microsoft'
        model = 'PowerShell'
        localeTag = [System.Globalization.CultureInfo]::CurrentCulture.Name
        timeZone = [System.TimeZoneInfo]::Local.Id
        appVersion = 'release-publisher-1.0'
        canvasHash = ''
        webglHash = ''
        audioHash = ''
        fontHash = ''
        securitySignals = @()
    }
}

function Get-CaptchaAnswer($Challenge) {
    if (-not $Challenge.captchaRequired) {
        return $null
    }
    if ($Challenge.captchaQuestion -notmatch '^\s*(\d+)\s*([+-])\s*(\d+)\s*(?:=\s*\?)?\s*$') {
        throw "无法解析登录验证码。"
    }
    $left = [int]$matches[1]
    $right = [int]$matches[3]
    $result = if ($matches[2] -eq '+') { [string]($left + $right) } else { [string]($left - $right) }
    return $result
}

function Get-PowNonce($Challenge) {
    $difficulty = [Math]::Max(0, [int]$Challenge.powDifficulty)
    $target = '0' * $difficulty
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        for ([long]$nonce = 0; $nonce -lt [long]::MaxValue; $nonce += 1) {
            $payload = "$($Challenge.challengeId):$($Challenge.powPrefix):$nonce"
            $hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
            $hash = ([System.BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
            if ($hash.StartsWith($target, [System.StringComparison]::Ordinal)) {
                return [string]$nonce
            }
        }
    } finally {
        $sha256.Dispose()
    }
    throw "登录安全挑战计算失败。"
}

function New-AuthenticatedWebSession([string]$ApiBaseUrl, [string]$LoginUsername, [Security.SecureString]$LoginPassword) {
    $normalizedApiBaseUrl = $ApiBaseUrl.TrimEnd('/')
    $origin = ([Uri]$normalizedApiBaseUrl).GetLeftPart([System.UriPartial]::Authority)
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $sessionClient = [System.Net.Http.HttpClient]::new($handler)
    $sessionClient.Timeout = [TimeSpan]::FromMinutes(10)
    $sessionClient.DefaultRequestHeaders.UserAgent.ParseAdd('e-report-release-publisher/1.0')
    $sessionClient.DefaultRequestHeaders.TryAddWithoutValidation('Origin', $origin) | Out-Null
    $sessionClient.DefaultRequestHeaders.TryAddWithoutValidation('X-Client-Platform', 'WEB') | Out-Null
    $clientInstanceId = "release-publisher-$([Guid]::NewGuid().ToString('N'))"
    $webEnvironment = New-ReleasePublisherWebEnvironment
    $plainTextPassword = Get-PlainTextPassword $LoginPassword
    try {
        $challengeResponse = $sessionClient.PostAsync(
            "$normalizedApiBaseUrl/auth/challenge",
            (New-JsonContent ([ordered]@{
                username = $LoginUsername
                clientType = 'WEB'
                clientInstanceId = $clientInstanceId
                webFingerprintHash = $webEnvironment.fingerprintHash
                webSecuritySignals = $webEnvironment.securitySignals
            }))
        ).GetAwaiter().GetResult()
        $challenge = Read-JsonResponse $challengeResponse
        $loginResponse = $sessionClient.PostAsync(
            "$normalizedApiBaseUrl/auth/login",
            (New-JsonContent ([ordered]@{
                username = $LoginUsername
                password = $plainTextPassword
                challengeId = $challenge.challengeId
                captchaAnswer = Get-CaptchaAnswer $challenge
                powNonce = Get-PowNonce $challenge
                clientType = 'WEB'
                clientInstanceId = $clientInstanceId
                webEnvironment = $webEnvironment
            }))
        ).GetAwaiter().GetResult()
        $login = Read-JsonResponse $loginResponse
        if ($login.passwordChangeRequired) {
            throw "发布账号必须先完成密码修改。"
        }
        $csrfResponse = $sessionClient.GetAsync("$normalizedApiBaseUrl/auth/csrf").GetAwaiter().GetResult()
        $csrf = Read-JsonResponse $csrfResponse
        if ([string]::IsNullOrWhiteSpace($csrf.token)) {
            throw "发布会话未返回 CSRF token。"
        }
        $sessionClient.DefaultRequestHeaders.TryAddWithoutValidation($csrf.headerName, $csrf.token) | Out-Null
        return [pscustomobject]@{
            ApiBaseUrl = $normalizedApiBaseUrl
            Client = $sessionClient
            Handler = $handler
        }
    } catch {
        $sessionClient.Dispose()
        $handler.Dispose()
        throw
    } finally {
        $plainTextPassword = $null
    }
}

function Close-AuthenticatedWebSession($Session) {
    if ($null -eq $Session) {
        return
    }
    try {
        $response = $Session.Client.PostAsync(
            "$($Session.ApiBaseUrl)/auth/logout",
            (New-JsonContent @{})
        ).GetAwaiter().GetResult()
        $response.Dispose()
    } catch {
        Write-Warning "发布会话自动注销失败，服务端会按会话时限自动失效。"
    } finally {
        $Session.Client.Dispose()
        $Session.Handler.Dispose()
    }
}

Export-ModuleMember -Function Read-JsonResponse,New-JsonContent,Get-PlainTextPassword,New-ReleasePublisherWebEnvironment,Get-CaptchaAnswer,Get-PowNonce,New-AuthenticatedWebSession,Close-AuthenticatedWebSession
