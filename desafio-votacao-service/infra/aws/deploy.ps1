param(
    [string]$Region = "us-east-2",
    [string]$Environment = "demo"
)

$ErrorActionPreference = "Stop"
$serviceDirectory = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

Push-Location $PSScriptRoot
try {
    terraform init
    terraform apply -target=aws_ecr_repository.app `
        -var="aws_region=$Region" -var="environment=$Environment"

    $repository = terraform output -raw ecr_repository_url
    $registry = $repository.Split('/')[0]
    aws ecr get-login-password --region $Region |
        docker login --username AWS --password-stdin $registry

    docker build --target app -t "${repository}:latest" $serviceDirectory
    docker push "${repository}:latest"

    terraform apply -var="aws_region=$Region" -var="environment=$Environment"
    terraform output
}
finally {
    Pop-Location
}