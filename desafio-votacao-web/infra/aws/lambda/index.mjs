import { GetObjectCommand, S3Client } from "@aws-sdk/client-s3";

const s3 = new S3Client({});
const backendPrefixes = ["/api", "/swagger-ui", "/v3/api-docs", "/actuator/health"];
const hopByHopHeaders = new Set([
  "connection",
  "content-encoding",
  "content-length",
  "host",
  "keep-alive",
  "set-cookie",
  "transfer-encoding",
  "upgrade",
  "x-forwarded-for",
  "x-forwarded-port",
  "x-forwarded-proto"
]);
const contentTypes = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".map", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml"],
  [".txt", "text/plain; charset=utf-8"],
  [".webp", "image/webp"],
  [".woff", "font/woff"],
  [".woff2", "font/woff2"]
]);
const securityHeaders = {
  "content-security-policy": "default-src 'self'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'; object-src 'none'; img-src 'self' data:; font-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'",
  "referrer-policy": "strict-origin-when-cross-origin",
  "strict-transport-security": "max-age=31536000; includeSubDomains; preload",
  "x-content-type-options": "nosniff",
  "x-frame-options": "SAMEORIGIN"
};

function response(statusCode, headers, body, cookies = []) {
  const bytes = Buffer.isBuffer(body) ? body : Buffer.from(body ?? "");
  return {
    statusCode,
    headers: { ...securityHeaders, ...headers },
    cookies,
    isBase64Encoded: true,
    body: bytes.toString("base64")
  };
}

function isBackendPath(path) {
  return backendPrefixes.some(prefix =>
    path === prefix ||
    path.startsWith(prefix + "/") ||
    (prefix === "/swagger-ui" && path.startsWith(prefix))
  );
}

function contentType(key) {
  const dot = key.lastIndexOf(".");
  return contentTypes.get(dot >= 0 ? key.slice(dot).toLowerCase() : "") ?? "application/octet-stream";
}

async function proxy(event, path) {
  const requestHeaders = new Headers();
  for (const [name, value] of Object.entries(event.headers ?? {})) {
    if (value != null && !hopByHopHeaders.has(name.toLowerCase())) {
      requestHeaders.set(name, value);
    }
  }
  if (event.cookies?.length) {
    requestHeaders.set("cookie", event.cookies.join("; "));
  }
  requestHeaders.set("x-origin-token", process.env.ORIGIN_TOKEN);

  const method = event.requestContext?.http?.method ?? "GET";
  const hasBody = !["GET", "HEAD"].includes(method) && event.body != null;
  const body = hasBody
    ? Buffer.from(event.body, event.isBase64Encoded ? "base64" : "utf8")
    : undefined;
  const query = event.rawQueryString ? "?" + event.rawQueryString : "";
  const upstream = await fetch(
    "http://" + process.env.API_ORIGIN_DOMAIN + path + query,
    {
      method,
      headers: requestHeaders,
      body,
      redirect: "manual",
      signal: AbortSignal.timeout(28000)
    }
  );

  const headers = {};
  upstream.headers.forEach((value, name) => {
    if (!hopByHopHeaders.has(name.toLowerCase())) {
      headers[name] = value;
    }
  });
  const cookies = typeof upstream.headers.getSetCookie === "function"
    ? upstream.headers.getSetCookie()
    : [];
  const bytes = method === "HEAD"
    ? Buffer.alloc(0)
    : Buffer.from(await upstream.arrayBuffer());
  return response(upstream.status, headers, bytes, cookies);
}

async function staticFile(path) {
  let key = decodeURIComponent(path).replace(/^\/+/, "");
  if (!key || path.endsWith("/") || !key.includes(".")) {
    key = "index.html";
  }
  if (key.split("/").includes("..")) {
    return response(400, { "content-type": "text/plain; charset=utf-8" }, "Caminho inválido");
  }

  try {
    const object = await s3.send(new GetObjectCommand({
      Bucket: process.env.WEB_BUCKET,
      Key: key
    }));
    const bytes = Buffer.from(await object.Body.transformToByteArray());
    const cacheControl = key === "index.html"
      ? "no-cache"
      : "public,max-age=31536000,immutable";
    return response(200, {
      "cache-control": cacheControl,
      "content-type": object.ContentType ?? contentType(key),
      ...(object.ETag ? { etag: object.ETag } : {})
    }, bytes);
  } catch (error) {
    if (
      error?.name === "NoSuchKey" ||
      error?.$metadata?.httpStatusCode === 403 ||
      error?.$metadata?.httpStatusCode === 404
    ) {
      return response(404, { "content-type": "text/plain; charset=utf-8" }, "Arquivo não encontrado");
    }
    throw error;
  }
}

export async function handler(event) {
  const path = event.rawPath || "/";
  try {
    return isBackendPath(path) ? await proxy(event, path) : await staticFile(path);
  } catch (error) {
    console.error("Falha ao atender requisição:", error?.name, error?.message);
    const status = isBackendPath(path) ? 502 : 500;
    return response(status, { "content-type": "application/json; charset=utf-8" },
      JSON.stringify({ status, detail: "Serviço temporariamente indisponível" }));
  }
}
