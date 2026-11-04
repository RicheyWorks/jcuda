[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Distribution,
    [Parameter(Mandatory=$true)][string]$CudaRuntimeBin,
    [Parameter(Mandatory=$true)][string]$NvrtcBin,
    [Parameter(Mandatory=$true)][string]$MainClass,
    [string[]]$ClassPath = @(),
    [string[]]$ApplicationArguments = @(),
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$CheckJni
)
$ErrorActionPreference = 'Stop'
$Distribution = (Resolve-Path -LiteralPath $Distribution).Path
$manifest = Get-Content -LiteralPath (Join-Path $Distribution 'build-manifest.json') -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1 -or @($manifest.artifacts).Count -ne 2 -or
    $manifest.javaJar -notin $manifest.artifacts.name -or $manifest.nativeJar -notin $manifest.artifacts.name) { throw 'Invalid matching-artifact manifest' }
foreach ($artifact in $manifest.artifacts) {
    if ([IO.Path]::GetFileName($artifact.name) -ne $artifact.name) { throw 'Invalid artifact filename' }
    $file = Join-Path $Distribution $artifact.name
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256) { throw "Artifact mismatch: $file" }
}
if (-not $JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command java).Source -Parent) -Parent }
$java = Join-Path $JavaHome 'bin/java.exe'
$extra = @($ClassPath | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
$runtime = (Resolve-Path -LiteralPath $CudaRuntimeBin).Path
$nvrtc = (Resolve-Path -LiteralPath $NvrtcBin).Path
foreach ($file in @((Join-Path $runtime 'cudart64_12.dll'),(Join-Path $nvrtc 'nvrtc64_120_0.dll'),(Join-Path $nvrtc 'nvrtc-builtins64_126.dll'))) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing runtime prerequisite: $file" }
}
$cache = Join-Path $Distribution ('runtime-cache/' + [Guid]::NewGuid().ToString('N'))
$empty = Join-Path $cache 'empty-library-path'
New-Item -ItemType Directory -Path $empty -Force | Out-Null
$classpathValue = (@((Join-Path $Distribution $manifest.javaJar),(Join-Path $Distribution $manifest.nativeJar)) + $extra) -join ';'
$options = @("-Djava.io.tmpdir=$cache", "-Djava.library.path=$empty")
if ($CheckJni) { $options += '-Xcheck:jni' }
$previousPath = $env:PATH
try {
    $env:PATH = "$runtime;$nvrtc;$previousPath"
    & $java @options '-cp' $classpathValue $MainClass @ApplicationArguments
    if ($LASTEXITCODE -ne 0) { throw "Application failed ($LASTEXITCODE)" }
} finally { $env:PATH = $previousPath }
