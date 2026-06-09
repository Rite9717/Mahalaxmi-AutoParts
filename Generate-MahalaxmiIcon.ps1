$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Output = Join-Path $Root "mahalaxmi-auto-parts.ico"
$SourceImage = "C:\Users\user\Downloads\image.jpeg"

Add-Type -AssemblyName System.Drawing

function New-IconPngBytes($Size) {
    if (-not (Test-Path $SourceImage)) {
        throw "Logo image not found: $SourceImage"
    }

    $source = [System.Drawing.Image]::FromFile($SourceImage)
    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.Color]::White)

    $scale = [Math]::Min($Size / $source.Width, $Size / $source.Height)
    $drawWidth = [int]($source.Width * $scale)
    $drawHeight = [int]($source.Height * $scale)
    $left = [int](($Size - $drawWidth) / 2)
    $top = [int](($Size - $drawHeight) / 2)
    $dest = New-Object System.Drawing.Rectangle($left, $top, $drawWidth, $drawHeight)
    $graphics.DrawImage($source, $dest)

    $stream = New-Object System.IO.MemoryStream
    $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $stream.ToArray()

    $stream.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $source.Dispose()

    return $bytes
}

function Write-UInt16($Stream, $Value) {
    $bytes = [BitConverter]::GetBytes([UInt16]$Value)
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Write-UInt32($Stream, $Value) {
    $bytes = [BitConverter]::GetBytes([UInt32]$Value)
    $Stream.Write($bytes, 0, $bytes.Length)
}

$sizes = @(256, 128, 64, 48, 32, 16)
$images = foreach ($size in $sizes) {
    [pscustomobject]@{
        Size = $size
        Bytes = New-IconPngBytes $size
    }
}

$stream = New-Object System.IO.MemoryStream
Write-UInt16 $stream 0
Write-UInt16 $stream 1
Write-UInt16 $stream $images.Count

$offset = 6 + (16 * $images.Count)
foreach ($image in $images) {
    $widthByte = if ($image.Size -eq 256) { 0 } else { $image.Size }
    $stream.WriteByte([byte]$widthByte)
    $stream.WriteByte([byte]$widthByte)
    $stream.WriteByte(0)
    $stream.WriteByte(0)
    Write-UInt16 $stream 1
    Write-UInt16 $stream 32
    Write-UInt32 $stream $image.Bytes.Length
    Write-UInt32 $stream $offset
    $offset += $image.Bytes.Length
}

foreach ($image in $images) {
    $stream.Write($image.Bytes, 0, $image.Bytes.Length)
}

[System.IO.File]::WriteAllBytes($Output, $stream.ToArray())
$stream.Dispose()

Write-Host "Created $Output"
