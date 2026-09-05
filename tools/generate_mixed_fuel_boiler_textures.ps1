param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

Add-Type -AssemblyName System.Drawing

$blockOutput = Join-Path $ProjectRoot 'src/main/resources/assets/gregsteamexpansion/textures/block/generators/boiler/mixed_fuel'
$iconOutput = Join-Path $ProjectRoot 'src/main/resources/assets/gregsteamexpansion/textures/gui/icon/mixed_fuel_boiler'
New-Item -ItemType Directory -Force -Path $blockOutput, $iconOutput | Out-Null

$colors = @{
    outline = [System.Drawing.Color]::FromArgb(255, 24, 24, 28)
    bronzeMid = [System.Drawing.Color]::FromArgb(255, 124, 86, 34)
    bronzeEdge = [System.Drawing.Color]::FromArgb(255, 166, 122, 56)
    bronzeHi = [System.Drawing.Color]::FromArgb(255, 222, 184, 110)
    iron = [System.Drawing.Color]::FromArgb(255, 128, 128, 136)
    fireDeep = [System.Drawing.Color]::FromArgb(255, 255, 106, 0)
    fireMid = [System.Drawing.Color]::FromArgb(255, 255, 136, 0)
    fireBright = [System.Drawing.Color]::FromArgb(255, 255, 170, 0)
    dark = [System.Drawing.Color]::FromArgb(255, 25, 25, 25)
    metal = [System.Drawing.Color]::FromArgb(255, 51, 51, 51)
    light = [System.Drawing.Color]::FromArgb(255, 76, 76, 76)
    steel = [System.Drawing.Color]::FromArgb(255, 178, 178, 178)
    black = [System.Drawing.Color]::FromArgb(255, 7, 7, 7)
    bronze = [System.Drawing.Color]::FromArgb(255, 176, 109, 36)
    bronzeDark = [System.Drawing.Color]::FromArgb(255, 104, 66, 28)
    bronzeLight = [System.Drawing.Color]::FromArgb(255, 218, 151, 65)
    amber = [System.Drawing.Color]::FromArgb(255, 195, 93, 27)
    orange = [System.Drawing.Color]::FromArgb(255, 255, 143, 0)
    yellow = [System.Drawing.Color]::FromArgb(255, 255, 216, 0)
    pale = [System.Drawing.Color]::FromArgb(255, 255, 255, 151)
    white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    blueDark = [System.Drawing.Color]::FromArgb(255, 25, 55, 150)
    blue = [System.Drawing.Color]::FromArgb(255, 45, 95, 225)
    blueLight = [System.Drawing.Color]::FromArgb(255, 95, 165, 255)
    gray = [System.Drawing.Color]::FromArgb(255, 105, 105, 105)
    grayLight = [System.Drawing.Color]::FromArgb(255, 170, 170, 170)
    redDark = [System.Drawing.Color]::FromArgb(255, 125, 20, 20)
    red = [System.Drawing.Color]::FromArgb(255, 225, 45, 35)
}

function New-Sprite {
    $bitmap = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.Dispose()
    return $bitmap
}

function Set-Pixel {
    param($Bitmap, [int] $X, [int] $Y, [string] $Color)
    if ($X -ge 0 -and $X -lt 16 -and $Y -ge 0 -and $Y -lt 16) {
        $Bitmap.SetPixel($X, $Y, $colors[$Color])
    }
}

function Fill-Rect {
    param($Bitmap, [int] $X, [int] $Y, [int] $Width, [int] $Height, [string] $Color)
    for ($py = $Y; $py -lt $Y + $Height; $py++) {
        for ($px = $X; $px -lt $X + $Width; $px++) {
            Set-Pixel $Bitmap $px $py $Color
        }
    }
}

function Save-Sprite {
    param($Bitmap, [string] $Directory, [string] $Name)
    $path = Join-Path $Directory ($Name + '.png')
    $Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $Bitmap.Dispose()
    Write-Output $path
}

function Draw-BoilerFrame {
    param($Bitmap)

    # GT decal language: partial transparent overlay over the bricked hull,
    # near-black outlines with the shared bronze ramp (tools/gen_hatch_textures.py).

    # Powder inlet throat centred on top, feeding the chamber (粉料入口).
    Fill-Rect $Bitmap 6 3 4 1 'outline'
    Set-Pixel $Bitmap 5 4 'outline'
    Fill-Rect $Bitmap 6 4 4 1 'bronzeMid'
    Set-Pixel $Bitmap 10 4 'outline'

    # Reinforced double border of the firebox panel (强化双层边框).
    Fill-Rect $Bitmap 3 5 10 1 'outline'
    Fill-Rect $Bitmap 3 13 10 1 'outline'
    for ($py = 6; $py -le 12; $py++) {
        Set-Pixel $Bitmap 3 $py 'outline'
        Set-Pixel $Bitmap 12 $py 'outline'
    }
    Fill-Rect $Bitmap 4 6 8 1 'bronzeEdge'
    Fill-Rect $Bitmap 4 12 8 1 'bronzeEdge'
    for ($py = 7; $py -le 11; $py++) {
        Set-Pixel $Bitmap 4 $py 'bronzeEdge'
        Set-Pixel $Bitmap 11 $py 'bronzeEdge'
    }

    # Central combustion chamber kept dark while idle, with an unlit pilot
    # light pair and the grate bar at its base (中央燃烧室).
    Fill-Rect $Bitmap 5 7 6 4 'outline'
    Set-Pixel $Bitmap 7 7 'iron'
    Set-Pixel $Bitmap 8 7 'iron'
    Set-Pixel $Bitmap 5 11 'outline'
    Set-Pixel $Bitmap 10 11 'outline'
    Fill-Rect $Bitmap 6 11 4 1 'bronzeDark'

    # Liquid fuel injector joined to the left side (液体燃料喷嘴).
    for ($py = 8; $py -le 9; $py++) {
        Set-Pixel $Bitmap 1 $py 'outline'
        Set-Pixel $Bitmap 2 $py 'bronzeMid'
    }
}

function Add-ActiveFlame {
    param($Bitmap)

    # The chamber lights up with the GT fire ramp; the pilot pair ignites.
    Fill-Rect $Bitmap 6 7 4 1 'fireDeep'
    Set-Pixel $Bitmap 5 8 'fireDeep'
    Fill-Rect $Bitmap 6 8 4 1 'fireMid'
    Set-Pixel $Bitmap 10 8 'fireDeep'
    Set-Pixel $Bitmap 5 9 'fireDeep'
    Set-Pixel $Bitmap 6 9 'fireMid'
    Set-Pixel $Bitmap 7 9 'fireBright'
    Set-Pixel $Bitmap 8 9 'fireBright'
    Set-Pixel $Bitmap 9 9 'fireMid'
    Set-Pixel $Bitmap 10 9 'fireDeep'
    Set-Pixel $Bitmap 5 10 'fireDeep'
    Fill-Rect $Bitmap 6 10 4 1 'fireMid'
    Set-Pixel $Bitmap 10 10 'fireDeep'
}

$idle = New-Sprite
Draw-BoilerFrame $idle
Save-Sprite $idle $blockOutput 'overlay_front'

$idleEmissive = New-Sprite
Save-Sprite $idleEmissive $blockOutput 'overlay_front_emissive'

$active = New-Sprite
Draw-BoilerFrame $active
Add-ActiveFlame $active
Save-Sprite $active $blockOutput 'overlay_front_active'

$activeEmissive = New-Sprite
Add-ActiveFlame $activeEmissive
Save-Sprite $activeEmissive $blockOutput 'overlay_front_active_emissive'

function Draw-Drop {
    param($Bitmap, [int] $OffsetX, [int] $OffsetY, [string] $Dark = 'blueDark', [string] $Mid = 'blue',
          [string] $Highlight = 'blueLight')
    Set-Pixel $Bitmap ($OffsetX + 4) ($OffsetY + 0) $Highlight
    Fill-Rect $Bitmap ($OffsetX + 3) ($OffsetY + 1) 3 2 $Mid
    Fill-Rect $Bitmap ($OffsetX + 2) ($OffsetY + 3) 5 2 $Mid
    Fill-Rect $Bitmap ($OffsetX + 1) ($OffsetY + 5) 7 3 $Dark
    Fill-Rect $Bitmap ($OffsetX + 2) ($OffsetY + 5) 5 3 $Mid
    Fill-Rect $Bitmap ($OffsetX + 3) ($OffsetY + 8) 3 1 $Dark
    Set-Pixel $Bitmap ($OffsetX + 3) ($OffsetY + 4) $Highlight
    Set-Pixel $Bitmap ($OffsetX + 3) ($OffsetY + 5) $Highlight
}

function Draw-Slash {
    param($Bitmap)
    for ($i = 0; $i -lt 10; $i++) {
        Set-Pixel $Bitmap (12 - $i) (3 + $i) 'redDark'
        if ($i -gt 0 -and $i -lt 9) { Set-Pixel $Bitmap (13 - $i) (3 + $i) 'red' }
    }
}

$modeLiquid = New-Sprite
Draw-Drop $modeLiquid 3 3
Save-Sprite $modeLiquid $iconOutput 'mode_liquid'

$modeCoFiring = New-Sprite
Draw-Drop $modeCoFiring 0 4
Fill-Rect $modeCoFiring 9 9 6 2 'bronze'
Fill-Rect $modeCoFiring 10 7 4 2 'amber'
Set-Pixel $modeCoFiring 11 5 'yellow'
Set-Pixel $modeCoFiring 13 6 'orange'
Set-Pixel $modeCoFiring 9 7 'bronze'
Save-Sprite $modeCoFiring $iconOutput 'mode_co_firing'

$dry = New-Sprite
Fill-Rect $dry 7 2 2 7 'steel'
Fill-Rect $dry 8 3 1 6 'red'
Fill-Rect $dry 6 9 4 4 'redDark'
Fill-Rect $dry 7 10 2 2 'red'
Set-Pixel $dry 5 14 'amber'
Set-Pixel $dry 7 13 'orange'
Set-Pixel $dry 9 14 'yellow'
Set-Pixel $dry 11 13 'orange'
Save-Sprite $dry $iconOutput 'status_dry_boiler'

$missingWater = New-Sprite
Draw-Drop $missingWater 3 3
Draw-Slash $missingWater
Save-Sprite $missingWater $iconOutput 'status_missing_water'

$missingLiquid = New-Sprite
Draw-Drop $missingLiquid 3 3 'redDark' 'amber' 'orange'
Draw-Slash $missingLiquid
Save-Sprite $missingLiquid $iconOutput 'status_missing_liquid_fuel'

$missingPowder = New-Sprite
Fill-Rect $missingPowder 3 11 10 2 'bronze'
Fill-Rect $missingPowder 5 9 6 2 'amber'
Fill-Rect $missingPowder 7 7 2 2 'orange'
Set-Pixel $missingPowder 4 8 'bronze'
Set-Pixel $missingPowder 11 8 'amber'
Draw-Slash $missingPowder
Save-Sprite $missingPowder $iconOutput 'status_missing_co_firing_fuel'

$steamBlocked = New-Sprite
Fill-Rect $steamBlocked 2 9 10 4 'white'
Fill-Rect $steamBlocked 4 7 3 2 'grayLight'
Fill-Rect $steamBlocked 8 6 3 3 'white'
Set-Pixel $steamBlocked 5 4 'grayLight'
Set-Pixel $steamBlocked 6 3 'white'
Set-Pixel $steamBlocked 9 3 'grayLight'
Set-Pixel $steamBlocked 10 2 'white'
Fill-Rect $steamBlocked 11 8 4 6 'redDark'
Fill-Rect $steamBlocked 12 9 3 4 'red'
Fill-Rect $steamBlocked 12 10 3 2 'dark'
Save-Sprite $steamBlocked $iconOutput 'status_steam_output_blocked'
