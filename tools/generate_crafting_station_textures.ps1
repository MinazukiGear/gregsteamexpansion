param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

Add-Type -AssemblyName System.Drawing

$blockOutput = Join-Path $ProjectRoot 'src/main/resources/assets/gregsteamexpansion/textures/block/crafting_station'
$guiOutput = Join-Path $ProjectRoot 'src/main/resources/assets/gregsteamexpansion/textures/gui'
New-Item -ItemType Directory -Force -Path $blockOutput, $guiOutput | Out-Null

$colors = @{
    wood        = [System.Drawing.Color]::FromArgb(255, 197, 157, 99)
    woodLight   = [System.Drawing.Color]::FromArgb(255, 216, 178, 118)
    woodDark    = [System.Drawing.Color]::FromArgb(255, 165, 133, 79)
    woodDeep    = [System.Drawing.Color]::FromArgb(255, 122, 98, 56)
    woodShadow  = [System.Drawing.Color]::FromArgb(255, 90, 72, 40)
    gridLine    = [System.Drawing.Color]::FromArgb(255, 91, 70, 35)
    gridSurface = [System.Drawing.Color]::FromArgb(255, 217, 184, 116)
    bronze      = [System.Drawing.Color]::FromArgb(255, 176, 109, 36)
    bronzeDark  = [System.Drawing.Color]::FromArgb(255, 104, 66, 28)
    bronzeLight = [System.Drawing.Color]::FromArgb(255, 218, 151, 65)
    dark        = [System.Drawing.Color]::FromArgb(255, 74, 58, 32)
    panel       = [System.Drawing.Color]::FromArgb(255, 198, 198, 198)
    slotFill    = [System.Drawing.Color]::FromArgb(255, 139, 139, 139)
    slotDark    = [System.Drawing.Color]::FromArgb(255, 55, 55, 55)
    slotLight   = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    borderLight = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    borderDark  = [System.Drawing.Color]::FromArgb(255, 85, 85, 85)
    arrowDark   = [System.Drawing.Color]::FromArgb(255, 85, 85, 85)
    gearBody    = [System.Drawing.Color]::FromArgb(255, 97, 97, 97)
    gearHole    = [System.Drawing.Color]::FromArgb(255, 58, 58, 58)
}

function New-Sprite {
    param([int] $Width, [int] $Height)
    $bitmap = New-Object System.Drawing.Bitmap $Width, $Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.Dispose()
    return $bitmap
}

function Set-Pixel {
    param($Bitmap, [int] $X, [int] $Y, [string] $Color)
    if ($X -ge 0 -and $X -lt $Bitmap.Width -and $Y -ge 0 -and $Y -lt $Bitmap.Height) {
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

function Draw-Planks {
    param($Bitmap, [string] $BaseColor, [string] $SeamColor)
    for ($py = 0; $py -lt 16; $py++) {
        $tone = if ($py % 4 -eq 0) { 'woodLight' } else { $BaseColor }
        for ($px = 0; $px -lt 16; $px++) {
            Set-Pixel $Bitmap $px $py $tone
        }
    }
    for ($py = 3; $py -lt 16; $py += 4) {
        Fill-Rect $Bitmap 0 $py 16 1 $SeamColor
    }
    Fill-Rect $Bitmap 4 0 1 3 'woodShadow'
    Fill-Rect $Bitmap 11 4 1 3 'woodShadow'
    Fill-Rect $Bitmap 6 8 1 3 'woodShadow'
    Fill-Rect $Bitmap 13 12 1 3 'woodShadow'
}

function Draw-TopTexture {
    $sprite = New-Sprite 16 16
    Draw-Planks $sprite 'wood' 'woodDeep'

    Fill-Rect $sprite 2 2 12 12 'gridSurface'
    for ($i = 2; $i -le 14; $i += 4) {
        Fill-Rect $sprite $i 2 1 12 'gridLine'
        Fill-Rect $sprite 2 $i 12 1 'gridLine'
    }
    Fill-Rect $sprite 3 3 3 3 'woodLight'
    Fill-Rect $sprite 11 3 3 3 'woodLight'
    Fill-Rect $sprite 3 11 3 3 'woodLight'
    Fill-Rect $sprite 11 11 3 3 'woodLight'

    foreach ($corner in @(@(0, 0), @(13, 0), @(0, 13), @(13, 13))) {
        $cx = $corner[0]
        $cy = $corner[1]
        Fill-Rect $sprite $cx $cy 3 3 'bronze'
        Set-Pixel $sprite ($cx + 1) ($cy + 1) 'bronzeDark'
        Set-Pixel $sprite $cx $cy 'bronzeLight'
    }

    Save-Sprite $sprite $blockOutput 'top'
}

function Draw-SideTexture {
    $sprite = New-Sprite 16 16
    Draw-Planks $sprite 'woodDark' 'woodDeep'

    Fill-Rect $sprite 0 0 16 3 'bronze'
    Fill-Rect $sprite 0 0 16 1 'bronzeLight'
    Fill-Rect $sprite 0 3 16 1 'bronzeDark'

    Fill-Rect $sprite 3 6 10 8 'dark'
    Fill-Rect $sprite 4 7 8 6 'woodDeep'
    Fill-Rect $sprite 4 7 8 1 'wood'
    Fill-Rect $sprite 4 12 8 1 'woodShadow'

    Save-Sprite $sprite $blockOutput 'side'
}

function Draw-BottomTexture {
    $sprite = New-Sprite 16 16
    Draw-Planks $sprite 'woodDeep' 'woodShadow'
    Fill-Rect $sprite 0 0 16 1 'bronzeDark'
    Fill-Rect $sprite 0 15 16 1 'bronzeDark'
    Fill-Rect $sprite 0 0 1 16 'bronzeDark'
    Fill-Rect $sprite 15 0 1 16 'bronzeDark'

    Save-Sprite $sprite $blockOutput 'bottom'
}

function Draw-GearIcon {
    # Small gear silhouette used as the tool-slot backdrop hint.
    param($Bitmap, [int] $X, [int] $Y)
    for ($py = 0; $py -lt 16; $py++) {
        for ($px = 0; $px -lt 16; $px++) {
            $dx = $px - 7.5
            $dy = $py - 7.5
            $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
            if ($r -gt 7.0) { continue }
            $angle = [Math]::Atan2($dy, $dx) + [Math]::PI
            $tooth = ([Math]::Floor($angle / ([Math]::PI / 4)) % 2) -eq 0
            if ($r -le 2.0) {
                Set-Pixel $Bitmap ($X + $px) ($Y + $py) 'gearHole'
            } elseif ($r -le 5.0 -or $tooth) {
                Set-Pixel $Bitmap ($X + $px) ($Y + $py) 'gearBody'
            }
        }
    }
}

function Draw-Slot {
    param($Bitmap, [int] $X, [int] $Y)
    Fill-Rect $Bitmap $X $Y 16 16 'slotFill'
    Fill-Rect $Bitmap ($X - 1) ($Y - 1) 18 1 'slotDark'
    Fill-Rect $Bitmap ($X - 1) $Y 1 18 'slotDark'
    Fill-Rect $Bitmap $X ($Y + 16) 18 1 'slotLight'
    Fill-Rect $Bitmap ($X + 16) $Y 1 17 'slotLight'
}

function Draw-GuiBackground {
    # Vanilla crafting-table look: flat gray panel, standard inset slots, a
    # plain arrow. No text. The source terminal docks onto this panel's left.
    $W = 184
    $H = 190
    $gui = New-Sprite $W $H

    Fill-Rect $gui 0 0 $W $H 'panel'
    Fill-Rect $gui 0 0 $W 1 'borderLight'
    Fill-Rect $gui 0 0 1 $H 'borderLight'
    Fill-Rect $gui 0 ($H - 1) $W 1 'borderDark'
    Fill-Rect $gui ($W - 1) 0 1 $H 'borderDark'

    # Crafting grid (3x3).
    for ($row = 0; $row -lt 3; $row++) {
        for ($col = 0; $col -lt 3; $col++) {
            Draw-Slot $gui (30 + $col * 18) (17 + $row * 18)
        }
    }

    # Result slot.
    Draw-Slot $gui 114 35

    # Tool bar (1x9) with a gear backdrop in every slot.
    for ($i = 0; $i -lt 9; $i++) {
        Draw-Slot $gui (8 + $i * 18) 80
        Draw-GearIcon $gui (8 + $i * 18) 80
    }

    # Player inventory and hotbar.
    for ($row = 0; $row -lt 3; $row++) {
        for ($col = 0; $col -lt 9; $col++) {
            Draw-Slot $gui (8 + $col * 18) (106 + $row * 18)
        }
    }
    for ($col = 0; $col -lt 9; $col++) {
        Draw-Slot $gui (8 + $col * 18) 168
    }

    # Arrow from the crafting grid to the result slot.
    Fill-Rect $gui 89 42 13 4 'arrowDark'
    Fill-Rect $gui 89 42 13 1 'slotLight'
    Fill-Rect $gui 101 38 2 12 'arrowDark'
    Fill-Rect $gui 103 40 2 8 'arrowDark'
    Fill-Rect $gui 105 42 2 4 'arrowDark'

    Save-Sprite $gui $guiOutput 'crafting_station'
}

function Draw-TerminalBackground {
    # Docked source-terminal panel: 54 terminal slots (6x9) plus a scrollbar
    # groove spanning the full slot column. Rendered left of the main panel.
    $W = 140
    $H = 190
    $gui = New-Sprite $W $H

    Fill-Rect $gui 0 0 $W $H 'panel'
    Fill-Rect $gui 0 0 $W 1 'borderLight'
    Fill-Rect $gui 0 0 1 $H 'borderLight'
    Fill-Rect $gui 0 ($H - 1) $W 1 'borderDark'
    Fill-Rect $gui ($W - 1) 0 1 $H 'borderDark'

    # Scrollbar groove spanning the slot column.
    Fill-Rect $gui 120 12 6 160 'slotFill'
    Fill-Rect $gui 120 12 6 1 'slotDark'
    Fill-Rect $gui 120 12 1 160 'slotDark'
    Fill-Rect $gui 120 171 6 1 'slotLight'
    Fill-Rect $gui 125 12 1 160 'slotLight'

    # Terminal slots (6x9 = one full page).
    for ($row = 0; $row -lt 9; $row++) {
        for ($col = 0; $col -lt 6; $col++) {
            Draw-Slot $gui (8 + $col * 18) (12 + $row * 18)
        }
    }

    Save-Sprite $gui $guiOutput 'crafting_station_terminal'
}

Draw-TopTexture
Draw-SideTexture
Draw-BottomTexture
Draw-GuiBackground
Draw-TerminalBackground
