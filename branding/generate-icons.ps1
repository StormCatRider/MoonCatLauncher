param([string]$Src, [string]$Res, [string]$Out)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile($Src)

function New-Rounded([float]$x, [float]$y, [float]$w, [float]$r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = 2 * $r
  $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $p.AddArc($x + $w - $d, $y + $w - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $w - $d, $d, $d, 90, 90)
  $p.CloseFigure(); return $p
}
function New-Canvas([int]$size) {
  $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'; $g.CompositingQuality = 'HighQuality'
  return @($bmp, $g)
}
# The artwork's rounded square spans about 20..1234 px of the 1254 px source with ~230 px corners.
$artX = 34; $artW = 1186; $artR = 220

# Full-bleed composite: gradient matching the artwork, artwork clipped to its rounded square on top.
function New-FullBleed([int]$size, [float]$artScale) {
  $c = New-Canvas $size; $bmp = $c[0]; $g = $c[1]
  $rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.Color]::FromArgb(255,0x16,0x17,0x52)), ([System.Drawing.Color]::FromArgb(255,0x4B,0x33,0xB0)), 90
  $g.FillRectangle($brush, $rect)
  $w = $size * $artScale; $o = ($size - $w) / 2; $k = $w / $artW
  $g.SetClip((New-Rounded $o $o $w ($artR * $k)))
  $g.DrawImage($img, [float]($o - $artX * $k), [float]($o - $artX * $k), [float]($img.Width * $k), [float]($img.Height * $k))
  $g.Dispose(); return $bmp
}

# Adaptive icon: masks show roughly the middle 2/3, so the art fills 76% of the layer.
$sizes = @{ 'mipmap-mdpi'=108; 'mipmap-hdpi'=162; 'mipmap-xhdpi'=216; 'mipmap-xxhdpi'=324; 'mipmap-xxxhdpi'=432 }
foreach ($k in $sizes.Keys) {
  New-Item -ItemType Directory -Force (Join-Path $Res $k) | Out-Null
  $b = New-FullBleed $sizes[$k] 0.76
  $b.Save((Join-Path $Res "$k\ic_launcher_art.png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
}

# In-app logo: the artwork with transparent rounded corners.
New-Item -ItemType Directory -Force (Join-Path $Res 'drawable-nodpi') | Out-Null
$c = New-Canvas 384; $bmp = $c[0]; $g = $c[1]; $k = 384 / $artW
$g.SetClip((New-Rounded 0 0 384 ($artR * $k)))
$g.DrawImage($img, [float](-$artX * $k), [float](-$artX * $k), [float]($img.Width * $k), [float]($img.Height * $k))
$g.Dispose(); $bmp.Save((Join-Path $Res 'drawable-nodpi\app_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# Store icon: 512 px full square; the store applies its own mask.
$b = New-FullBleed 512 1.12
$b.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
$img.Dispose()
"ok"
