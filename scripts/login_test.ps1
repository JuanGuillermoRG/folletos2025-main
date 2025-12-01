# Automated login test script for the contactos app (Windows PowerShell)
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\login_test.ps1

$base = 'http://localhost:8081'
$adminUrl = "$base/admin/folletos/add"
$loginUrl = "$base/login"

try {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

    # 1) Visit admin to create a saved request in session
    Invoke-WebRequest -Uri $adminUrl -WebSession $session -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null

    # 2) GET login page (should be rendered with a hidden _csrf input)
    $resp = Invoke-WebRequest -Uri $loginUrl -WebSession $session -UseBasicParsing -ErrorAction Stop
    $html = $resp.Content

    $m = [regex]::Match($html, 'name=\"_csrf\"\s+value=\"([^\"]+)\"')
    if (-not $m.Success) {
        Write-Output "ERROR: _csrf token not found in login page HTML."
        exit 2
    }

    $token = $m.Groups[1].Value
    Write-Output "_csrf token: $token"

    # 3) POST credentials with the form _csrf and session
    $form = @{
        username = 'admin'
        password = 'admin'
        _csrf = $token
    }

    $post = Invoke-WebRequest -Uri $loginUrl -Method Post -Body $form -WebSession $session -MaximumRedirection 0 -ErrorAction SilentlyContinue

    if ($post -eq $null) {
        Write-Output "ERROR: No response from POST /login"
        exit 3
    }

    Write-Output "POST StatusCode: $($post.StatusCode)"
    if ($post.Headers.Location) { Write-Output "Location: $($post.Headers.Location)" }

    # If redirected, follow the Location once using the same session
    if ($post.StatusCode -eq 302 -and $post.Headers.Location) {
        $target = $post.Headers.Location
        # Normalize relative locations
        if ($target -notmatch '^https?://') { $target = "$base$target" }
        Write-Output "Following redirect to: $target"
        $final = Invoke-WebRequest -Uri $target -WebSession $session -UseBasicParsing -ErrorAction SilentlyContinue
        if ($final) {
            Write-Output "Final HTTP status: $($final.StatusCode)"
            $len = if ($final.RawContentLength) { $final.RawContentLength } else { ($final.Content).Length }
            Write-Output "Final content length: $len"
            if ($final.StatusCode -eq 200) { Write-Output "SUCCESS: Authenticated and reached admin page." }
        } else {
            Write-Output "Unable to GET final page after redirect."
        }
    } else {
        Write-Output "Login did not redirect as expected."
    }
} catch {
    Write-Output "Exception: $($_.Exception.Message)"
    exit 99
}
