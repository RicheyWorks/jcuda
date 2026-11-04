[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$CudaRoot,
    [string]$NvrtcRoot,
    [string]$NvccRoot,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildRoot,
    [switch]$RunSmoke
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$common = Join-Path (Split-Path $repo -Parent) 'jcuda-common'
$parent = Join-Path (Split-Path $repo -Parent) 'jcuda-parent'
if (-not $NvrtcRoot) { $NvrtcRoot = $CudaRoot }
if (-not $NvccRoot) { $NvccRoot = $CudaRoot }
if (-not $JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command javac).Source -Parent) -Parent }
if (-not $BuildRoot) { $BuildRoot = Join-Path $repo 'out/windows-x86_64' }
$CudaRoot = (Resolve-Path -LiteralPath $CudaRoot).Path
$NvrtcRoot = (Resolve-Path -LiteralPath $NvrtcRoot).Path
$NvccRoot = (Resolve-Path -LiteralPath $NvccRoot).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$BuildRoot = [IO.Path]::GetFullPath($BuildRoot)
if ($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitProcess) { throw 'Use a 64-bit Windows PowerShell process' }
function Require-File([string]$path) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing prerequisite: $path" } }
function Run([string]$exe, [string[]]$arguments, [string]$log) {
    & $exe @arguments *> $log
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 30; throw "Failed ($LASTEXITCODE): $exe; see $log" }
}
function Git-Head([string]$path) {
    $value = & git -C $path rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw "Not a Git checkout: $path" }
    return "$value".Trim()
}
foreach ($tool in @('git','cmake')) { Get-Command $tool -ErrorAction Stop | Out-Null }
foreach ($file in @('bin/java.exe','bin/javac.exe','bin/jar.exe','include/jni.h')) { Require-File (Join-Path $JavaHome $file) }
foreach ($file in @('include/cuda.h','include/cuda_runtime.h','include/crt/host_config.h','include/cudaProfiler.h','include/nvPTXCompiler.h','lib/x64/cuda.lib','lib/x64/cudart.lib','bin/cudart64_12.dll')) { Require-File (Join-Path $CudaRoot $file) }
foreach ($file in @('include/nvrtc.h','lib/x64/nvrtc.lib','bin/nvrtc64_120_0.dll','bin/nvrtc-builtins64_126.dll')) { Require-File (Join-Path $NvrtcRoot $file) }
foreach ($file in @('bin/nvcc.exe','lib/x64/nvptxcompiler_static.lib')) { Require-File (Join-Path $NvccRoot $file) }
Require-File (Join-Path $common 'JCudaCommonJNI/CMakeLists.txt')
Require-File (Join-Path $parent 'pom.xml')
$license = @((Join-Path $NvccRoot 'LICENSE'), (Join-Path $NvccRoot 'EULA.txt'), (Join-Path $NvccRoot 'doc/EULA.txt')) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $license) { throw 'Keep the supplied NVIDIA LICENSE/EULA.txt with the CUDA compiler prerequisite' }
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$jar = Join-Path $JavaHome 'bin/jar.exe'
$javaVersion = (& $javac -version 2>&1 | Out-String).Trim()
if ($javaVersion -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 21) { throw 'This script requires JDK21+ tools; Java classes target release8' }
$nvccVersion = (& (Join-Path $NvccRoot 'bin/nvcc.exe') --version | Out-String).Trim()
if ($nvccVersion -notmatch 'release 12\.6,') { throw 'Use matching CUDA12.6 compiler prerequisites' }
$header = [IO.File]::ReadAllText((Join-Path $CudaRoot 'include/cuda.h'))
if ($header -notmatch '#define CUDA_VERSION\s+12060\b') { throw 'CUDA include root must contain CUDA12.6 headers' }
$head = Git-Head $repo
$commonHead = Git-Head $common
$parentHead = Git-Head $parent
$dirty = @(& git -C $repo status --porcelain).Count -gt 0
$marker = Join-Path $BuildRoot '.richeyworks-build-root'
if (Test-Path -LiteralPath $BuildRoot) {
    if (-not (Test-Path -LiteralPath $marker)) { throw 'Choose a new empty/nonexistent BuildRoot; refusing an unowned directory' }
    if ([IO.File]::ReadAllText($marker) -ne $repo) { throw 'BuildRoot belongs to another checkout' }
} else { New-Item -ItemType Directory -Path $BuildRoot | Out-Null; [IO.File]::WriteAllText($marker,$repo) }
$run = Join-Path $BuildRoot ('runs/' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $run 'classes'
$stage = Join-Path $run 'native-stage'
$dist = Join-Path $BuildRoot ('dist/' + $head.Substring(0,12))
foreach ($directory in @($run,$classes,$stage,(Join-Path $stage 'lib'),$dist)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$utf8 = New-Object Text.UTF8Encoding($false)
$sources = @(Get-ChildItem (Join-Path $repo 'JCudaJava/src/main/java') -Recurse -Filter '*.java' | Sort-Object FullName)
$argfile = Join-Path $run 'sources.txt'
[IO.File]::WriteAllLines($argfile, @($sources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }), $utf8)
Run $javac @('--release','8','-encoding','UTF-8','-d',$classes,('@'+$argfile)) (Join-Path $run 'javac.log')
$sdk = $CudaRoot.Replace('\','/')
$rtc = $NvrtcRoot.Replace('\','/')
$compiler = $NvccRoot.Replace('\','/')
$modules = @('JCudaDriver','JCudaRuntime','JNvrtc','JNvPTXCompiler')
$dlls = @()
$nativeCompilers = @()
foreach ($module in $modules) {
    $build = Join-Path $BuildRoot ('native/' + $module)
    $options = @('-S',(Join-Path $repo ($module+'JNI')),'-B',$build,'-G','Visual Studio 17 2022','-A','x64',
        "-DJAVA_HOME=$($JavaHome.Replace('\','/'))", "-DCUDA_TOOLKIT_ROOT_DIR=$sdk", "-DCUDA_NVCC_EXECUTABLE=$compiler/bin/nvcc.exe",
        "-DCUDA_TOOLKIT_INCLUDE=$sdk/include", "-DCUDA_CUDA_LIBRARY=$sdk/lib/x64/cuda.lib",
        "-DCUDA_CUDART_LIBRARY=$sdk/lib/x64/cudart.lib", "-DCUDA_nvrtc_LIBRARY=$rtc/lib/x64/nvrtc.lib",
        "-DCUDA_nvptxcompiler_static_LIBRARY=$compiler/lib/x64/nvptxcompiler_static.lib",
        '-DCUDA_USE_STATIC_CUDA_RUNTIME=OFF', "-DCMAKE_CXX_FLAGS=/I`"$rtc/include`"")
    # Legacy FindCUDA may reset explicit include/library hints on the first pass.
    Run 'cmake' $options (Join-Path $run ($module+'-configure1.log'))
    Run 'cmake' $options (Join-Path $run ($module+'-configure2.log'))
    $cache = [IO.File]::ReadAllText((Join-Path $build 'CMakeCache.txt'))
    $expectedPaths = @{CUDA_TOOLKIT_INCLUDE="$sdk/include";CUDA_CUDA_LIBRARY="$sdk/lib/x64/cuda.lib";
        CUDA_CUDART_LIBRARY="$sdk/lib/x64/cudart.lib";CUDA_nvrtc_LIBRARY="$rtc/lib/x64/nvrtc.lib";
        CUDA_nvptxcompiler_static_LIBRARY="$compiler/lib/x64/nvptxcompiler_static.lib";CUDA_NVCC_EXECUTABLE="$compiler/bin/nvcc.exe"}
    foreach ($key in $expectedPaths.Keys) {
        $selected = [regex]::Match($cache, '(?m)^'+[regex]::Escape($key)+':[^=]+=(.*)$')
        if (-not $selected.Success -or $selected.Groups[1].Value.Trim() -ne $expectedPaths[$key]) { throw "Unexpected CMake selection: $key" }
    }
    $compilerInfo = Get-ChildItem (Join-Path $build 'CMakeFiles') -Recurse -Filter 'CMakeCXXCompiler.cmake' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $compilerText = [IO.File]::ReadAllText($compilerInfo.FullName)
    $nativeCompilers += @{module=$module;version=[regex]::Match($compilerText,'set\(CMAKE_CXX_COMPILER_VERSION "([^"]+)"\)').Groups[1].Value;
        executable=[regex]::Match($compilerText,'set\(CMAKE_CXX_COMPILER "([^"]+)"\)').Groups[1].Value}
    Run 'cmake' @('--build',$build,'--config','Release') (Join-Path $run ($module+'-build.log'))
    $name = "$module-12.6.0-windows-x86_64.dll"
    $dll = Join-Path $repo ("nativeLibraries/windows/x86_64/lib/$name")
    Require-File $dll
    Copy-Item -LiteralPath $dll -Destination (Join-Path $stage "lib/$name")
    $dlls += $name
}
$mit = Join-Path $repo 'LICENSE'
Copy-Item -LiteralPath $mit -Destination (Join-Path $stage 'LICENSE')
Copy-Item -LiteralPath $license -Destination (Join-Path $stage 'NVIDIA-CUDA-LICENSE.txt')
$notice = 'RicheyWorks JCuda source build. JCuda MIT attribution retained. JNvPTXCompiler statically links NVIDIA nvptxcompiler under the supplied NVIDIA license. Other NVIDIA runtime DLLs are prerequisites, not bundled. Not an official JCuda/NVIDIA release.'
[IO.File]::WriteAllText((Join-Path $stage 'BUILD-NOTICE.txt'),$notice,$utf8)
$javaJar = Join-Path $dist ("jcuda-richeyworks-$($head.Substring(0,12)).jar")
$nativeJar = Join-Path $dist ("jcuda-richeyworks-natives-windows-x86_64-$($head.Substring(0,12)).jar")
$date = '--date=2026-01-01T00:00:00Z'
Run $jar @('--create','--file',$javaJar,$date,'-C',$classes,'.','-C',$repo,'LICENSE') (Join-Path $run 'jar-java.log')
$jarArgs = @('--create','--file',$nativeJar,$date)
foreach ($entry in @('LICENSE','NVIDIA-CUDA-LICENSE.txt','BUILD-NOTICE.txt') + @($dlls | ForEach-Object { "lib/$_" })) { $jarArgs += @('-C',$stage,$entry) }
Run $jar $jarArgs (Join-Path $run 'jar-native.log')
$manifest = [ordered]@{schema=1;coreCommit=$head;commonCommit=$commonHead;parentCommit=$parentHead;coreDirty=$dirty;
    commonDirty=(@(& git -C $common status --porcelain).Count -gt 0);parentDirty=(@(& git -C $parent status --porcelain).Count -gt 0);
    javaCompiler=$javaVersion;nvcc=$nvccVersion;cmake=((& cmake --version | Select-Object -First 1) -join '');
    javaRelease=8;configuration='Release/x64';modules=$modules;nativeCompilers=$nativeCompilers;runDirectory=$run;
    cudaRoot=$CudaRoot;nvrtcRoot=$NvrtcRoot;nvccRoot=$NvccRoot;
    javaJar=[IO.Path]::GetFileName($javaJar);nativeJar=[IO.Path]::GetFileName($nativeJar);
    artifacts=@(@($javaJar,$nativeJar) | ForEach-Object { @{name=[IO.Path]::GetFileName($_);sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()} });
    nativeDlls=@($dlls | ForEach-Object { @{name=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $stage "lib/$_")).Hash.ToLowerInvariant()} });
    smoke='not requested';note='Repeatable source-to-artifact workflow; byte-identical cross-machine native builds are not claimed.'}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Run.ps1') -Destination (Join-Path $dist 'Run.ps1')
if ($RunSmoke) {
    $smokeClasses = Join-Path $run 'smoke-classes'
    $temp = Join-Path $run 'fresh-smoke-temp'
    $empty = Join-Path $run 'empty-library-path'
    foreach ($directory in @($smokeClasses,$temp,$empty)) { New-Item -ItemType Directory -Path $directory | Out-Null }
    Run $javac @('--release','8','-cp',$javaJar,'-d',$smokeClasses,(Join-Path $PSScriptRoot 'PackagedGpuSmoke.java')) (Join-Path $run 'smoke-javac.log')
    $previousPath = $env:PATH
    Push-Location $run
    try {
        $env:PATH = "$(Join-Path $CudaRoot 'bin');$(Join-Path $NvrtcRoot 'bin');$previousPath"
        $libraryLog = (Join-Path $run 'smoke-library.log').Replace('\','/')
        Run $java @('-Xcheck:jni',"-Xlog:library=info:file=$libraryLog", "-Djava.io.tmpdir=$temp", "-Djava.library.path=$empty",
            '-cp',"$smokeClasses;$javaJar;$nativeJar",'PackagedGpuSmoke',(Join-Path $dist 'smoke.json')) (Join-Path $run 'smoke.log')
    } finally { $env:PATH=$previousPath; Pop-Location }
    $loaded = [IO.File]::ReadAllText((Join-Path $run 'smoke-library.log'))
    foreach ($dll in $manifest.nativeDlls) {
        $extracted = Join-Path $temp $dll.name
        Require-File $extracted
        if ((Get-FileHash -LiteralPath $extracted).Hash.ToLowerInvariant() -ne $dll.sha256) { throw "Loaded/extracted DLL mismatch: $($dll.name)" }
        if (-not $loaded.Contains("Loaded library $extracted,")) { throw "JVM did not confirm packaged DLL: $extracted" }
    }
    $manifest.smoke = 'PASS: packaged-only classpath, four DLLs extracted/loaded/hash-verified, tiny real GPU computation'
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $dist 'build-manifest.json') -Encoding utf8
Write-Output "Built matching Java/native artifacts: $dist"
Write-Output "Smoke: $($manifest.smoke)"
