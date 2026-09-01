param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$outputDirectory = Join-Path $ProjectRoot 'src/main/resources/data/gregsteamexpansion/structures'
$outputPath = Join-Path $outputDirectory 'empty.nbt'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$memory = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter $memory, ([System.Text.Encoding]::UTF8), $true

function Write-U16BE([int] $value) {
    $writer.Write([byte](($value -shr 8) -band 0xFF))
    $writer.Write([byte]($value -band 0xFF))
}

function Write-I32BE([int] $value) {
    $unsigned = [uint32]$value
    $writer.Write([byte](($unsigned -shr 24) -band 0xFF))
    $writer.Write([byte](($unsigned -shr 16) -band 0xFF))
    $writer.Write([byte](($unsigned -shr 8) -band 0xFF))
    $writer.Write([byte]($unsigned -band 0xFF))
}

function Write-NbtString([string] $value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
    Write-U16BE $bytes.Length
    $writer.Write($bytes)
}

function Write-TagHeader([byte] $type, [string] $name) {
    $writer.Write($type)
    Write-NbtString $name
}

# Root compound.
$writer.Write([byte]10)
Write-NbtString ''

Write-TagHeader 3 'DataVersion'
Write-I32BE 3465

# A 3x1x1 empty structure gives the registration test room for both boilers.
Write-TagHeader 9 'size'
$writer.Write([byte]3)
Write-I32BE 3
Write-I32BE 3
Write-I32BE 1
Write-I32BE 1

Write-TagHeader 9 'palette'
$writer.Write([byte]10)
Write-I32BE 1
Write-TagHeader 8 'Name'
Write-NbtString 'minecraft:air'
$writer.Write([byte]0)

Write-TagHeader 9 'blocks'
$writer.Write([byte]10)
Write-I32BE 0

Write-TagHeader 9 'entities'
$writer.Write([byte]10)
Write-I32BE 0

$writer.Write([byte]0)
$writer.Flush()
$memory.Position = 0

$file = [System.IO.File]::Create($outputPath)
$gzip = New-Object System.IO.Compression.GZipStream $file, ([System.IO.Compression.CompressionLevel]::Optimal), $false
$memory.CopyTo($gzip)
$gzip.Dispose()
$file.Dispose()
$writer.Dispose()
$memory.Dispose()

Write-Output $outputPath
