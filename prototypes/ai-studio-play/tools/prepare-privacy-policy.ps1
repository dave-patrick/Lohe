param([switch]$Check)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $repoRoot 'docs/privacy-policy.md'
$policy = [IO.File]::ReadAllText($sourcePath).Replace("`r`n", "`n").TrimEnd() + "`n"
if ($policy -match 'privacy@example.com|\(placeholder\)|draft, not yet published') {
    throw 'Privacy policy still contains a placeholder.'
}
$paragraphs = foreach ($block in ($policy.Trim() -split "`n\s*`n")) {
    $escaped = [Net.WebUtility]::HtmlEncode(($block -replace "`n", ' '))
    if ($escaped.StartsWith('## ')) { '<h2>' + $escaped.Substring(3) + '</h2>' }
    elseif ($escaped.StartsWith('# ')) { '<h1>' + $escaped.Substring(2) + '</h1>' }
    else { '<p>' + $escaped + '</p>' }
}
$html = @"
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Lohe Privacy Policy</title><style>body{font:17px/1.65 system-ui,sans-serif;max-width:800px;margin:40px auto;padding:0 24px;color:#17212b;background:#fff}h1,h2{line-height:1.3}h2{margin-top:2em}a{color:#0069a8}</style></head><body><main>
$($paragraphs -join "`n")
<p><a href="mailto:atomic22industries@gmail.com">Contact Atomic 22 Industries</a> · <a href="../">Lohe home</a></p>
</main></body></html>
"@
$outputs = @{
    'app/src/main/assets/privacy-policy.txt' = $policy
    'docs/privacy/index.html' = $html.TrimEnd() + "`n"
}
foreach ($relativePath in $outputs.Keys) {
    $path = Join-Path $repoRoot $relativePath
    if ($Check) {
        if (!(Test-Path -LiteralPath $path) -or [IO.File]::ReadAllText($path).Replace("`r`n", "`n") -cne $outputs[$relativePath]) {
            throw "Generated privacy policy is stale: $relativePath. Run tools/prepare-privacy-policy.ps1."
        }
    } else {
        [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
        [IO.File]::WriteAllText($path, $outputs[$relativePath], [Text.UTF8Encoding]::new($false))
    }
}
Write-Output $(if ($Check) { 'Privacy policy outputs match their source.' } else { 'Generated offline and website privacy policies.' })
