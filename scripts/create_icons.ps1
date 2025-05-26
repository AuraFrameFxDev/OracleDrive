# Create launcher icons for all densities
$baseDir = "$PSScriptRoot\app\src\main\res"

# Create launcher icons for each density
$densities = @(
    @{name="mipmap-mdpi"; size=48},
    @{name="mipmap-hdpi"; size=72},
    @{name="mipmap-xhdpi"; size=96},
    @{name="mipmap-xxhdpi"; size=144},
    @{name="mipmap-xxxhdpi"; size=192}
)

foreach ($density in $densities) {
    $dir = Join-Path $baseDir $density.name
    $outputFile = Join-Path $dir "ic_launcher.png"
    
    # Create a simple colored square with the density name and size
    $text = "${$density.size}px"
    $fontSize = [math]::Floor($density.size / 6)
    
    # Create a simple image with ImageMagick (if available)
    if (Get-Command magick -ErrorAction SilentlyContinue) {
        magick -size ${$density.size}x${$density.size} xc:none -fill "#6200EE" -draw "rectangle 0,0 $($density.size-1),$($density.size-1)" -fill white -pointsize $fontSize -gravity center -annotate 0 $text $outputFile
        Write-Host "Created $outputFile"
    } else {
        # Fallback: Create empty files as placeholders
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        [System.IO.File]::WriteAllText($outputFile, "Placeholder for ${$density.size}x${$density.size} icon")
        Write-Host "Created placeholder for $outputFile (install ImageMagick for actual icons)"
    }
}

Write-Host "Launcher icons created successfully!"
