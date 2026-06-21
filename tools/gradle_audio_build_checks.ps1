param(
    [string]$Module = "app"
)

.\gradlew.bat ":$Module`:assembleDebug" ":$Module`:testDebugUnitTest"
