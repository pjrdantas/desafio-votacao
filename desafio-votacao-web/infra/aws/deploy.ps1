param(
    [Parameter(Mandatory = $true)]
    [string]$ApiOriginDomain,
    [string]$Region = "sa-east-1",
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

Push-Location $PSScriptRoot
try {
    terraform init
    terraform apply -var="aws_region=$Region" -var="environment=$Environment" `
        -var="api_origin_domain=$ApiOriginDomain"

    $bucket = terraform output -raw bucket_name
    $distribution = terraform output -raw cloudfront_distribution_id
    $build = Join-Path $webDirectory "dist\desafio-votacao-web\browser"

    aws s3 sync $build "s3://$bucket" --delete --exclude "index.html" `
        --cache-control "public,max-age=31536000,immutable"
    aws s3 cp (Join-Path $build "index.html") "s3://$bucket/index.html" `
        --cache-control "no-cache" --content-type "text/html"
    aws cloudfront create-invalidation --distribution-id $distribution --paths "/*" | Out-Null
    terraform output application_url
}
finally {
    Pop-Location
}