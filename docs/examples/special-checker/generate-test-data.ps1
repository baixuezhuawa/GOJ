param(
    [string]$DataRoot = 'D:\ProgramData\goj-data'
)

$ErrorActionPreference = 'Stop'
$sampleRoot = Join-Path $DataRoot 'testData\p8\v1'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$caseSizes = @(1, 3, 5, 1000)

# 按当前加载器的 testN/input.txt、output.txt 格式生成独立的示例目录。
for ($caseIndex = 0; $caseIndex -lt $caseSizes.Count; $caseIndex++) {
    $caseSize = $caseSizes[$caseIndex]
    $casePath = Join-Path $sampleRoot ('test' + ($caseIndex + 1))
    [System.IO.Directory]::CreateDirectory($casePath) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $casePath 'input.txt'), "$caseSize`n", $utf8)
    $expected = (1..$caseSize) -join ' '
    [System.IO.File]::WriteAllText((Join-Path $casePath 'output.txt'), "$expected`n", $utf8)
}

Write-Output "已生成 $($caseSizes.Count) 个测试点：$sampleRoot"
