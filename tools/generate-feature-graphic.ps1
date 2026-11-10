# Generates a compliant 1024x500 Google Play Store feature graphic (24bpp RGB, no alpha)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$markPath = Join-Path $repoRoot 'docs\assets\lohe-mark.png'
$kaniPath = Join-Path $repoRoot 'docs\assets\kani.png'
$outPath  = Join-Path $repoRoot 'docs\assets\feature-graphic.png'

$bmp = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

$rect = New-Object System.Drawing.Rectangle(0, 0, 1024, 500)
$c1 = [System.Drawing.Color]::FromArgb(18, 18, 20)
$c2 = [System.Drawing.Color]::FromArgb(28, 28, 30)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45.0)
$g.FillRectangle($brush, $rect)
$brush.Dispose()

if (Test-Path $markPath) {
    $mark = [System.Drawing.Image]::FromFile($markPath)
    $g.DrawImage($mark, 100, 110, 280, 280)
    $mark.Dispose()
}

if (Test-Path $kaniPath) {
    $kani = [System.Drawing.Image]::FromFile($kaniPath)
    $g.DrawImage($kani, 620, 50, 360, 397)
    $kani.Dispose()
}

$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Output "Generated Play Store feature graphic: $outPath"
