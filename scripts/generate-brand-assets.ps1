param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$brandGeometry = Get-Content -LiteralPath (Join-Path $RepoRoot 'shared\brand-mark.json') -Raw | ConvertFrom-Json
$brandBlue = $brandGeometry.color
$brandSize = [float]$brandGeometry.size

Add-Type -AssemblyName System.Drawing

function Resolve-RepoPath([string]$Path) {
    return Join-Path $RepoRoot $Path
}

function Ensure-Directory([string]$Path) {
    $directory = if ([System.IO.Path]::HasExtension($Path)) { Split-Path -Parent $Path } else { $Path }
    if ($directory -and -not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
}

function Color-Hex([string]$Hex, [int]$Alpha = 255) {
    $value = $Hex.TrimStart('#')
    return [System.Drawing.Color]::FromArgb(
        $Alpha,
        [Convert]::ToInt32($value.Substring(0, 2), 16),
        [Convert]::ToInt32($value.Substring(2, 2), 16),
        [Convert]::ToInt32($value.Substring(4, 2), 16)
    )
}

function New-RoundedRectanglePath([float]$X, [float]$Y, [float]$Width, [float]$Height, [float]$Radius) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-Canvas([int]$Size, [System.Drawing.Color]$Background) {
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.Clear($Background)
    return @{ Bitmap = $bitmap; Graphics = $graphics }
}

function New-BrandPath($Commands) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $currentX = [float]0
    $currentY = [float]0
    try {
        foreach ($command in $Commands) {
            switch ($command[0]) {
                'M' {
                    $path.StartFigure()
                    $currentX = [float]$command[1]
                    $currentY = [float]$command[2]
                }
                'L' {
                    $path.AddLine($currentX, $currentY, [float]$command[1], [float]$command[2])
                    $currentX = [float]$command[1]
                    $currentY = [float]$command[2]
                }
                'C' {
                    $path.AddBezier($currentX, $currentY, [float]$command[1], [float]$command[2],
                        [float]$command[3], [float]$command[4], [float]$command[5], [float]$command[6])
                    $currentX = [float]$command[5]
                    $currentY = [float]$command[6]
                }
                'Z' { $path.CloseFigure() }
                default { throw "Unsupported brand geometry command: $($command[0])" }
            }
        }
        return $path
    } catch {
        $path.Dispose()
        throw
    }
}

function Draw-BrandMark(
    [System.Drawing.Graphics]$Graphics,
    [float]$Scale,
    [float]$OffsetX,
    [float]$OffsetY,
    [bool]$WithBackground,
    [string]$ForegroundColor = '#FFFFFF'
) {
    $Graphics.TranslateTransform($OffsetX, $OffsetY)
    $Graphics.ScaleTransform($Scale, $Scale)
    try {
        if ($WithBackground) {
            $backgroundPath = New-RoundedRectanglePath 0 0 $brandSize $brandSize ($brandSize / 4)
            $backgroundBrush = [System.Drawing.SolidBrush]::new((Color-Hex $brandBlue))
            try { $Graphics.FillPath($backgroundBrush, $backgroundPath) }
            finally { $backgroundBrush.Dispose(); $backgroundPath.Dispose() }
        }
        $foregroundBrush = [System.Drawing.SolidBrush]::new((Color-Hex $ForegroundColor))
        try {
            foreach ($commands in $brandGeometry.paths) {
                $path = New-BrandPath $commands
                try { $Graphics.FillPath($foregroundBrush, $path) }
                finally { $path.Dispose() }
            }
        } finally { $foregroundBrush.Dispose() }
    } finally { $Graphics.ResetTransform() }
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    Ensure-Directory $Path
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-IconPng(
    [string]$Path,
    [int]$Size,
    [bool]$ForegroundOnly = $false,
    [float]$ForegroundScale = 0.9,
    [string]$ForegroundColor = '#FFFFFF'
) {
    $canvas = New-Canvas $Size ([System.Drawing.Color]::Transparent)
    try {
        $scale = if ($ForegroundOnly) { $ForegroundScale } else { 1.0 }
        $offset = $Size * ((1 - $scale) / 2)
        Draw-BrandMark $canvas.Graphics ($Size / $brandSize * $scale) $offset $offset (-not $ForegroundOnly) $ForegroundColor
        Save-Png $canvas.Bitmap $Path
    } finally { $canvas.Graphics.Dispose(); $canvas.Bitmap.Dispose() }
}

function New-AdaptiveBackgroundPng([string]$Path, [int]$Size) {
    $canvas = New-Canvas $Size (Color-Hex $brandBlue)
    try { Save-Png $canvas.Bitmap $Path }
    finally { $canvas.Graphics.Dispose(); $canvas.Bitmap.Dispose() }
}

function New-LauncherPng([string]$Path, [int]$Size, [bool]$Round = $false) {
    $canvas = New-Canvas $Size ([System.Drawing.Color]::Transparent)
    try {
        $clipPath = if ($Round) {
            New-RoundedRectanglePath 0 0 $Size $Size ($Size / 2)
        } else {
            New-RoundedRectanglePath 0 0 $Size $Size ($Size * 0.22)
        }
        $canvas.Graphics.SetClip($clipPath)
        $brush = [System.Drawing.SolidBrush]::new((Color-Hex $brandBlue))
        try { $canvas.Graphics.FillRectangle($brush, 0, 0, $Size, $Size) }
        finally { $brush.Dispose() }
        $canvas.Graphics.ResetClip()
        $clipPath.Dispose()

        $symbolScale = 0.9
        $offset = $Size * ((1 - $symbolScale) / 2)
        Draw-BrandMark $canvas.Graphics ($Size / $brandSize * $symbolScale) $offset $offset $false
        Save-Png $canvas.Bitmap $Path
    } finally {
        $canvas.Graphics.Dispose()
        $canvas.Bitmap.Dispose()
    }
}

function New-MaskablePng([string]$Path, [int]$Size) {
    $canvas = New-Canvas $Size (Color-Hex $brandBlue)
    try {
        $symbolScale = 0.9
        $offset = $Size * ((1 - $symbolScale) / 2)
        Draw-BrandMark $canvas.Graphics ($Size / $brandSize * $symbolScale) $offset $offset $false
        Save-Png $canvas.Bitmap $Path
    } finally { $canvas.Graphics.Dispose(); $canvas.Bitmap.Dispose() }
}

function New-SplashPng([string]$Path, [int]$Size) {
    $canvas = New-Canvas $Size (Color-Hex '#F8FAFC')
    try {
        $iconSize = [Math]::Round($Size * 0.38)
        $offset = ($Size - $iconSize) / 2
        Draw-BrandMark $canvas.Graphics ($iconSize / $brandSize) $offset ($offset - ($Size * 0.03)) $false $brandBlue
        Save-Png $canvas.Bitmap $Path
    } finally {
        $canvas.Graphics.Dispose()
        $canvas.Bitmap.Dispose()
    }
}

function Convert-ToWebP([string]$SourcePng, [string]$TargetWebP) {
    Ensure-Directory $TargetWebP
    $ffmpeg = Get-Command ffmpeg -ErrorAction Stop
    $encodedWebP = [System.IO.Path]::ChangeExtension($SourcePng, '.webp')
    & $ffmpeg.Source -y -loglevel error -i $SourcePng -lossless 1 $encodedWebP
    if ($LASTEXITCODE -ne 0) {
        throw "ffmpeg failed to create $TargetWebP"
    }
    if (Test-Path -LiteralPath $TargetWebP) {
        if ((Get-FileHash -LiteralPath $encodedWebP).Hash -eq (Get-FileHash -LiteralPath $TargetWebP).Hash) {
            [System.IO.File]::Delete($encodedWebP)
        } else {
            [System.IO.File]::Replace($encodedWebP, $TargetWebP, [System.Management.Automation.Language.NullString]::Value)
        }
    } else {
        [System.IO.File]::Move($encodedWebP, $TargetWebP)
    }
}

function Write-BrandSvg([string]$Path, [bool]$WithBackground = $false) {
    Ensure-Directory $Path
    $foreground = if ($WithBackground) { '#FFFFFF' } else { $brandBlue }
    $background = if ($WithBackground) {
        "  <rect width=`"$brandSize`" height=`"$brandSize`" rx=`"$($brandSize / 4)`" fill=`"$brandBlue`"/>"
    } else { '' }
    $paths = foreach ($commands in $brandGeometry.paths) {
        $segments = foreach ($command in $commands) {
            ($command | ForEach-Object {
                if ($_ -is [string]) { $_ } else { ([double]$_).ToString('0.###', [System.Globalization.CultureInfo]::InvariantCulture) }
            }) -join ' '
        }
        '  <path d="{0}" fill="{1}"/>' -f ($segments -join ' '), $foreground
    }
    $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $brandSize $brandSize" role="img" aria-label="网络运维管理">
$background
$($paths -join "`n")
</svg>
"@
    Set-Content -LiteralPath $Path -Value $svg -Encoding UTF8
}

function Update-WebBrandReferences {
    $geometrySource = [System.IO.File]::ReadAllText((Resolve-RepoPath 'shared\brand-mark.json')).Replace("`r`n", "`n").TrimEnd()
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $hasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($geometrySource))
        $revision = [System.BitConverter]::ToString($digest).Replace('-', '').Substring(0, 12).ToLowerInvariant()
    } finally { $hasher.Dispose() }
    $indexPath = Resolve-RepoPath 'e-report\index.html'
    $index = [System.IO.File]::ReadAllText($indexPath)
    $assetPattern = '(?<asset>/(?:e-report-logo\.svg|favicon\.svg|icons/pwa-icon-192\.png|manifest\.webmanifest))(?:\?v=[^"]*)?'
    $index = [regex]::Replace($index, $assetPattern, { param($match) $match.Groups['asset'].Value + '?v=' + $revision })
    [System.IO.File]::WriteAllText($indexPath, $index)

    $manifestPath = Resolve-RepoPath 'e-report\public\manifest.webmanifest'
    $manifest = [System.IO.File]::ReadAllText($manifestPath)
    $manifest = [regex]::Replace($manifest, '(?<asset>/icons/pwa-[^"?]+\.png)(?:\?v=[^"]*)?',
        { param($match) $match.Groups['asset'].Value + '?v=' + $revision })
    [System.IO.File]::WriteAllText($manifestPath, $manifest)

    $workerPath = Resolve-RepoPath 'e-report\public\service-worker.js'
    $worker = [System.IO.File]::ReadAllText($workerPath)
    $worker = [regex]::Replace($worker, "const CACHE_NAME = '[^']+';", "const CACHE_NAME = 'e-report-app-shell-$revision';")
    $worker = [regex]::Replace($worker, "const BRAND_REVISION = '[^']+';", "const BRAND_REVISION = '$revision';")
    [System.IO.File]::WriteAllText($workerPath, $worker)
}

$tmpDir = Resolve-RepoPath 'output\brand-assets-tmp'
Ensure-Directory $tmpDir

Write-BrandSvg (Resolve-RepoPath 'e-report\public\e-report-logo.svg')
Write-BrandSvg (Resolve-RepoPath 'e-report\public\favicon.svg') $true
Write-BrandSvg (Resolve-RepoPath 'e-report\src\assets\e-report-logo.svg')
Update-WebBrandReferences

New-IconPng (Resolve-RepoPath 'e-report\public\icons\pwa-icon-192.png') 192
New-IconPng (Resolve-RepoPath 'e-report\public\icons\pwa-icon-512.png') 512
New-MaskablePng (Resolve-RepoPath 'e-report\public\icons\pwa-maskable-512.png') 512

New-IconPng (Resolve-RepoPath 'e-report-app\assets\icon.png') 1024
New-IconPng (Resolve-RepoPath 'e-report-app\assets\favicon.png') 64
New-IconPng (Resolve-RepoPath 'e-report-app\assets\splash-icon.png') 1024 $true 1 $brandBlue
New-AdaptiveBackgroundPng (Resolve-RepoPath 'e-report-app\assets\android-icon-background.png') 1024
New-IconPng (Resolve-RepoPath 'e-report-app\assets\android-icon-foreground.png') 1024 $true
New-IconPng (Resolve-RepoPath 'e-report-app\assets\android-icon-monochrome.png') 1024 $true

$splashSizes = @{
    'drawable-mdpi' = 288
    'drawable-hdpi' = 432
    'drawable-xhdpi' = 576
    'drawable-xxhdpi' = 864
    'drawable-xxxhdpi' = 1152
}

foreach ($density in $splashSizes.Keys) {
    New-SplashPng (Resolve-RepoPath "e-report-app\android\app\src\main\res\$density\splashscreen_logo.png") $splashSizes[$density]
}

$launcherSizes = @{
    'mipmap-mdpi' = @{ Legacy = 48; Adaptive = 108 }
    'mipmap-hdpi' = @{ Legacy = 72; Adaptive = 162 }
    'mipmap-xhdpi' = @{ Legacy = 96; Adaptive = 216 }
    'mipmap-xxhdpi' = @{ Legacy = 144; Adaptive = 324 }
    'mipmap-xxxhdpi' = @{ Legacy = 192; Adaptive = 432 }
}

foreach ($density in $launcherSizes.Keys) {
    $legacySize = $launcherSizes[$density].Legacy
    $adaptiveSize = $launcherSizes[$density].Adaptive
    $legacyPng = Join-Path $tmpDir "$density-ic_launcher.png"
    $roundPng = Join-Path $tmpDir "$density-ic_launcher_round.png"
    $backgroundPng = Join-Path $tmpDir "$density-ic_launcher_background.png"
    $foregroundPng = Join-Path $tmpDir "$density-ic_launcher_foreground.png"
    $monochromePng = Join-Path $tmpDir "$density-ic_launcher_monochrome.png"

    New-LauncherPng $legacyPng $legacySize
    New-LauncherPng $roundPng $legacySize $true
    New-AdaptiveBackgroundPng $backgroundPng $adaptiveSize
    New-IconPng $foregroundPng $adaptiveSize $true
    New-IconPng $monochromePng $adaptiveSize $true

    $targetDir = Resolve-RepoPath "e-report-app\android\app\src\main\res\$density"
    Convert-ToWebP $legacyPng (Join-Path $targetDir 'ic_launcher.webp')
    Convert-ToWebP $roundPng (Join-Path $targetDir 'ic_launcher_round.webp')
    Convert-ToWebP $backgroundPng (Join-Path $targetDir 'ic_launcher_background.webp')
    Convert-ToWebP $foregroundPng (Join-Path $targetDir 'ic_launcher_foreground.webp')
    Convert-ToWebP $monochromePng (Join-Path $targetDir 'ic_launcher_monochrome.webp')
}

New-IconPng (Resolve-RepoPath 'output\brand-preview.png') 1024
New-IconPng (Resolve-RepoPath 'output\brand-mark-preview.png') 1024 $true 1 $brandBlue

$resolvedTemp = (Resolve-Path -LiteralPath $tmpDir).Path
$expectedTemp = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot 'output\brand-assets-tmp'))
if ($resolvedTemp -ne $expectedTemp -or -not $resolvedTemp.StartsWith($RepoRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to delete an unexpected brand assets directory: $resolvedTemp"
}
Remove-Item -LiteralPath $resolvedTemp -Recurse -Force

Write-Host "Brand assets generated."
