param(
    [string]$Root = "app/src/main/cpp"
)

$patterns = @(
    "new ",
    "delete ",
    "malloc\(",
    "free\(",
    "std::mutex",
    "lock_guard",
    "unique_lock",
    "condition_variable",
    "\.wait\(",
    "\.resize\(",
    "__android_log",
    "LOG[DIWE]\(",
    "fopen\(",
    "read\(",
    "write\(",
    "sleep\(",
    "usleep\(",
    "std::this_thread::sleep",
    "std::string",
    "JNIEnv",
    "Call.*Method"
)

if (-not (Test-Path $Root)) {
    Write-Host "Native source root not found: $Root"
    exit 0
}

Get-ChildItem -Path $Root -Recurse -Include *.cpp,*.cc,*.cxx,*.h,*.hpp |
    Select-String -Pattern ($patterns -join "|") |
    ForEach-Object {
        "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim()
    }

Write-Host "Review matches manually. Matches are allowed outside the audio callback path."
