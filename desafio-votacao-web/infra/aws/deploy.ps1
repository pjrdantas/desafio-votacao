param(
    [Parameter(Mandatory = $true)]
    [string]$ApiOriginDomain,
    [Parameter(Mandatory = $true)]
    [string]$OriginToken,
    [ValidateSet("cloudfront", "lambda_url")]
    [string]$HostingMode = "cloudfront",
    [string]$Region = "us-east-2",
    [string]$Environment = "demo"
)

$ErrorActionPreference = "Stop"
$webDirectory = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

Push-Location $webDirectory
try {
    npm ci
    npm test
    npm run build
}
finally {
    Pop-Location
}

$previousOriginToken = $env:TF_VAR_origin_token
$env:TF_VAR_origin_token = $OriginToken
Push-Location $PSScriptRoot
try {
    terraform init
    terraform apply -var="aws_region=$Region" -var="environment=$Environment" `
        -var="api_origin_domain=$ApiOriginDomain" -var="hosting_mode=$HostingMode"

    $bucket = terraform output -raw bucket_name
    $build = Join-Path $webDirectory "dist\desafio-votacao-web\browser"

    aws s3 sync $build "s3://$bucket" --delete --exclude "index.html" `
        --cache-control "public,max-age=31536000,immutable"
    aws s3 cp (Join-Path $build "index.html") "s3://$bucket/index.html" `
        --cache-control "no-cache" --content-type "text/html"

    if ($HostingMode -eq "cloudfront") {
        $distribution = terraform output -raw cloudfront_distribution_id
        aws cloudfront create-invalidation --distribution-id $distribution --paths "/*" | Out-Null
    }
    terraform output application_url
}
finally {
    Pop-Location
    $env:TF_VAR_origin_token = $previousOriginToken
}
