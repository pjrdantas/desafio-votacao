// Executar somente com compose.qa.yaml. Contas, votos e CPFs deste ensaio são sintéticos.
import { execFileSync } from 'node:child_process';
import { randomInt } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';

const root = fileURLToPath(new URL('../', import.meta.url));
const base = 'http://localhost:18080';
const compose = ['compose', '-f', path.join(root, 'compose.qa.yaml')];
const docker = args => execFileSync('docker', args, { cwd:root, encoding:'utf8', timeout:120000, maxBuffer:2*1024*1024, windowsHide:true });
const started = new Date().toISOString();
async function request(method, route, body, token, jar) {
  const headers = {};
  if (body !== undefined) headers['Content-Type']='application/json';
  if (token) headers.Authorization='Bearer '+token;
  if (jar?.size) headers.Cookie=[...jar].map(([k,v])=>k+'='+v).join('; ');
  if (jar?.csrf) headers['X-XSRF-TOKEN']=jar.csrf;
  const response = await fetch(base+route,{ method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000) });
  for (const cookie of response.headers.getSetCookie()) {
    const pair=cookie.split(';')[0], idx=pair.indexOf('=');
    jar?.set(pair.slice(0,idx),pair.slice(idx+1));
  }
  const text=await response.text();
  return {status:response.status,body:text?JSON.parse(text):null};
}
async function ok(method,route,body,token,jar,status=200) {
  const result=await request(method,route,body,token,jar);
  assert.equal(result.status,status,method+' '+route+' retornou '+result.status);
  return result.body;
}
async function auth(route,body,jar,status=200) {
  const csrf=await ok('GET','/api/v1/auth/csrf',undefined,undefined,jar);
  jar.csrf=csrf.token;
  return ok('POST','/api/v1/auth/'+route,body,undefined,jar,status);
}
function cpf(base) {
  let text=String(base);
  for(let round=0;round<2;round++) {
    const sum=[...text].reduce((n,d,i)=>n+Number(d)*(text.length+1-i),0),digit=11-sum%11;
    text+=digit>=10?0:digit;
  }
  return text;
}
async function pauta(token,titulo) {
  const p=await ok('POST','/api/v1/pautas',{titulo},token,undefined,201);
  const sessao=await ok('POST','/api/v1/pautas/'+p.id+'/sessao',{duracaoMinutos:60},token,undefined,201);
  return {...p,encerraEm:sessao.encerraEm};
}
function stats(samples,seconds,statuses) {
  const sorted=samples.toSorted((a,b)=>a-b),q=p=>Math.round(sorted[Math.ceil(sorted.length*p)-1]*100)/100;
  return {requisicoes:samples.length,duracaoSegundos:+seconds.toFixed(3),requisicoesPorSegundo:+(samples.length/seconds).toFixed(2),
    p50Ms:q(.5),p95Ms:q(.95),p99Ms:q(.99),maxMs:q(1),status:statuses};
}
async function load(count,concurrency,fn) {
  const samples=[],statuses={},start=performance.now(); let index=0;
  await Promise.all(Array.from({length:concurrency},async()=>{
    while(index<count) {
      const item=index++, t=performance.now();
      const r=await fn(item);
      samples.push(performance.now()-t); statuses[r.status]=(statuses[r.status]||0)+1;
    }
  }));
  return {...stats(samples,(performance.now()-start)/1000,statuses),concorrencia:concurrency};
}
const containers=docker([...compose,'ps','--format','json']);
assert.ok(containers.includes('votacao-qa-app-1') && containers.includes('votacao-qa-postgres-1'),'Inicie compose.qa.yaml antes do ensaio.');
await ok('GET','/actuator/health');
const tokens=[],seed=randomInt(300000000,800000000);
console.log('Preparando 20 contas de teste com login real...');
for(let i=0;i<20;i++) {
  const jar=new Map(),documento=cpf(seed+i),senha='Carga-local-'+seed+'!';
  await auth('cadastro',{nome:'Teste de carga '+i,cpf:documento,senha},jar,201);
  tokens.push((await auth('login',{cpf:documento,senha},jar)).accessToken);
}
const large=await pauta(tokens[0],'QA: apuração de 200 mil votos');
assert.match(large.id,/^[a-f0-9-]{36}$/);
docker([...compose,'exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U','votacao_qa','-d','votacao_qa','-c',
  "INSERT INTO voto(pauta_id,associado_id,escolha) SELECT '"+large.id+"','carga-seed-'||n,CASE WHEN n%2=0 THEN 'SIM' ELSE 'NAO' END FROM generate_series(1,200000) n; ANALYZE voto;"]);
const initial=await ok('GET','/api/v1/pautas/'+large.id+'/resultado',undefined,tokens[0]);
assert.equal(initial.total,200000); assert.equal(initial.sim,100000); assert.equal(initial.nao,100000);
const pautas=[];
for(let i=0;i<100;i++) pautas.push(await pauta(tokens[0],'QA: votos concorrentes '+i));
console.log('Medindo 2.000 votos HTTP autenticados com concorrência 20...');
const writes=await load(2000,20,i=>request('POST','/api/v1/pautas/'+pautas[Math.floor(i/20)].id+'/votos',
  {escolha:i%2?'NAO':'SIM'},tokens[i%20]));
console.log('Medindo 100 apurações de 200.000 votos com concorrência 5...');
const reads=await load(100,5,()=>request('GET','/api/v1/pautas/'+large.id+'/resultado',undefined,tokens[0]));
let verified=0;
for(const p of pautas) {
  const result=await ok('GET','/api/v1/pautas/'+p.id+'/resultado',undefined,tokens[0]);
  assert.equal(result.total,20); assert.equal(result.sim,10); assert.equal(result.nao,10); verified+=result.total;
}
console.log('Reiniciando apenas a API de QA e verificando persistência e JWT...');
docker([...compose,'restart','app']);
let ready=false;
for(let attempt=0;attempt<60;attempt++) {
  try { const r=await request('GET','/actuator/health'); if(r.status===200) {ready=true;break;} } catch {}
  await new Promise(resolve=>setTimeout(resolve,1000));
}
assert.ok(ready,'API não ficou saudável após reinício.');
const after=await ok('GET','/api/v1/pautas/'+large.id+'/resultado',undefined,tokens[0]);
assert.equal(after.total,200000); assert.equal(after.encerraEm,initial.encerraEm);
assert.equal((await ok('GET','/api/v1/pautas/'+pautas[0].id+'/resultado',undefined,tokens[0])).total,20);
const report={
  inicio:started,fim:new Date().toISOString(),
  ambiente:{node:process.version,plataforma:process.platform,docker:docker(['info','--format','{{.ServerVersion}}']).trim(),
    cpus:Number(docker(['info','--format','{{.NCPU}}']).trim()),memoriaDockerBytes:Number(docker(['info','--format','{{.MemTotal}}']).trim()),jvm:'21',postgres:'17-alpine',springBoot:'4.1.1',hikariPool:10,cpfFake:'apto',destino:base},
  metodologia:'20 cadastros e logins reais antes da medição. 200.000 votos sintéticos inseridos por SQL para avaliar apuração; 2.000 votos gravados pela API (100 pautas x 20 usuários). Não mede 200.000 inserções HTTP nem comportamento de produção.',
  escrita:writes,apuracao200Mil:reads,
  verificacoes:{votosHttpConfirmados:verified,votosSeed:after.total,reinicioApi:true,jwtAnteriorAceitoDepoisDoReinicio:true,prazoPreservado:true},
  criterio:{erroHttpInesperado:0,p95MaxMs:2000},
  aprovado:writes.status[201]===2000 && reads.status[200]===100 && writes.p95Ms<2000 && reads.p95Ms<2000
};
const dir=path.join(root,'target/performance'); mkdirSync(dir,{recursive:true});
writeFileSync(path.join(dir,'carga-local.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({aprovado:report.aprovado,escrita:writes,apuracao:reads,verificacoes:report.verificacoes},null,2));
assert.ok(report.aprovado,'Ensaio não atingiu o critério local. Consulte target/performance/carga-local.json.');
