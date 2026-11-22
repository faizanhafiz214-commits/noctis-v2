'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { WebSocketServer, WebSocket } = require('ws');
const QRCode = require('qrcode');

// In production put a TLS-terminating reverse proxy in front of this process.
const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '127.0.0.1';
const BASE_URL = process.env.BASE_URL || process.env.RENDER_EXTERNAL_URL || `http://localhost:${PORT}`;
const base = new URL(BASE_URL);
if (base.protocol !== 'https:' && !['localhost','127.0.0.1','::1'].includes(base.hostname)) throw Error('BASE_URL must use HTTPS outside localhost');

const SECRET = process.env.JWT_SECRET;
if (!SECRET || Buffer.byteLength(SECRET) < 32) throw Error('Set JWT_SECRET to a random secret of at least 32 bytes');
// DATABASE_URL selects PostgreSQL; SQLite is retained only for local testing.
const ephemeral=!!(!process.env.DATABASE_URL && (process.env.RENDER || process.env.ALLOW_EPHEMERAL_SQLITE==='true')); 
if(ephemeral&&process.env.ALLOW_EPHEMERAL_SQLITE!=='true') throw Error('DATABASE_URL is required unless ALLOW_EPHEMERAL_SQLITE=true is explicitly set');
if(ephemeral)console.warn('TEST ONLY: SQLite storage may be lost on restart, spin-down, or redeploy.');
if(process.env.NODE_ENV==='production'&&process.env.PGLITE_TEST==='1') throw Error('PGLITE_TEST is for local testing only');
const pgMode=!!process.env.DATABASE_URL||process.env.PGLITE_TEST==='1';
let db;
if(process.env.PGLITE_TEST==='1') { const {PGlite}=require('@electric-sql/pglite'); db=new PGlite(); }
else if(pgMode) { const {Pool}=require('pg'); db=new Pool({connectionString:process.env.DATABASE_URL,max:5,connectionTimeoutMillis:10000}); }
else db=new DatabaseSync(process.env.DB_PATH || path.join(__dirname,'data.sqlite'));
function pgSql(sql){let i=0;return sql.replace(/\?/g,()=>'$'+(++i));}
async function stmt(sql,...v){if(pgMode)return (await db.query(pgSql(sql),v)).rows[0];return db.prepare(sql).get(...v)}
async function all(sql,...v){if(pgMode)return (await db.query(pgSql(sql),v)).rows;return db.prepare(sql).all(...v)}
async function run(sql,...v){if(pgMode)return db.query(pgSql(sql),v);return db.prepare(sql).run(...v)}
async function initDatabase(){
 if(pgMode){const ddl=[
 'CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,salt TEXT NOT NULL,pass TEXT NOT NULL)',
 'CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES users(id),name TEXT NOT NULL,token_hash TEXT NOT NULL,latitude DOUBLE PRECISION,longitude DOUBLE PRECISION,accuracy DOUBLE PRECISION,battery DOUBLE PRECISION,charging INTEGER,updated_at BIGINT,location_at BIGINT,location_active INTEGER,created_at BIGINT NOT NULL DEFAULT 0)',
 'CREATE TABLE IF NOT EXISTS pair_codes(hash TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES users(id),expires BIGINT NOT NULL)',
 'CREATE INDEX IF NOT EXISTS devices_owner_idx ON devices(owner)',
 'CREATE UNIQUE INDEX IF NOT EXISTS devices_token_idx ON devices(token_hash)'];for(const sql of ddl)await db.query(sql);}
 else {db.exec(`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;
 CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,salt TEXT NOT NULL,pass TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES users(id),name TEXT NOT NULL,token_hash TEXT NOT NULL,latitude REAL,longitude REAL,accuracy REAL,battery REAL,charging INTEGER,updated_at INTEGER,location_at INTEGER,location_active INTEGER,created_at INTEGER NOT NULL DEFAULT 0);
 CREATE TABLE IF NOT EXISTS pair_codes(hash TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES users(id),expires INTEGER NOT NULL);`);
 for(const [name,type] of [['location_at','INTEGER'],['location_active','INTEGER'],['created_at','INTEGER NOT NULL DEFAULT 0']])if(!db.prepare('PRAGMA table_info(devices)').all().some(x=>x.name===name))db.exec(`ALTER TABLE devices ADD COLUMN ${name} ${type}`);}
}
async function consumeCode(codeHash,deviceId,deviceName,tokenHash){
 if(!pgMode){db.exec('BEGIN IMMEDIATE');try{const result=db.prepare('DELETE FROM pair_codes WHERE hash=? AND expires>? RETURNING owner').get(codeHash,Date.now());if(!result){db.exec('ROLLBACK');return null}db.prepare('INSERT INTO devices(id,owner,name,token_hash,created_at) VALUES(?,?,?,?,?)').run(deviceId,result.owner,deviceName,tokenHash,Date.now());db.exec('COMMIT');return result.owner}catch(e){db.exec('ROLLBACK');throw e}}
 const conn=process.env.PGLITE_TEST==='1'?db:await db.connect();try{await conn.query('BEGIN');const result=(await conn.query('DELETE FROM pair_codes WHERE hash=$1 AND expires>$2 RETURNING owner',[codeHash,Date.now()])).rows[0];if(!result){await conn.query('ROLLBACK');return null}await conn.query('INSERT INTO devices(id,owner,name,token_hash,created_at) VALUES($1,$2,$3,$4,$5)',[deviceId,result.owner,deviceName,tokenHash,Date.now()]);await conn.query('COMMIT');return result.owner}catch(e){await conn.query('ROLLBACK');throw e}finally{if(conn!==db)conn.release()}
}
const hash = s=>crypto.createHash('sha256').update(s).digest('hex');
const id = ()=>crypto.randomUUID();
const random = n=>crypto.randomBytes(n).toString('base64url');
const derive = (p,s)=>crypto.scryptSync(p,s,64).toString('hex');
const eq = (a,b)=>{const x=Buffer.from(a),y=Buffer.from(b);return x.length===y.length&&crypto.timingSafeEqual(x,y)};
const enc = o=>Buffer.from(JSON.stringify(o)).toString('base64url');
function issue(user){const p=enc({alg:'HS256',typ:'JWT'})+'.'+enc({sub:user,iss:'noctis',aud:'dashboard',iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600});return p+'.'+crypto.createHmac('sha256',SECRET).update(p).digest('base64url')}
async function verify(token){try {if(typeof token!=='string'||token.length>4096) return null;const a=token.split('.');if(a.length!==3)return null;const h=JSON.parse(Buffer.from(a[0],'base64url'));const p=JSON.parse(Buffer.from(a[1],'base64url'));const s=crypto.createHmac('sha256',SECRET).update(a[0]+'.'+a[1]).digest('base64url');if(!eq(s,a[2])||h.alg!=='HS256'||h.typ!=='JWT'||p.iss!=='noctis'||p.aud!=='dashboard'||typeof p.sub!=='string'||!Number.isInteger(p.exp)||p.exp<=Date.now()/1000||!await stmt('SELECT id FROM users WHERE id=?',p.sub))return null;return p.sub}catch{return null}}
const clients = new Set();
function broadcast(owner, payload){for(const c of clients) if(c.readyState===WebSocket.OPEN&&c.kind==='dashboard'&&c.owner===owner) c.send(JSON.stringify(payload));}
function visible(d){return {id:d.id,name:d.name,latitude:d.latitude,longitude:d.longitude,accuracy:d.accuracy,battery:d.battery,charging:d.charging===null?null:!!d.charging,updatedAt:d.updated_at==null?null:Number(d.updated_at),locationAt:d.location_at==null?null:Number(d.location_at),locationActive:!!d.location_active,online:[...clients].some(c=>c.kind==='device'&&c.deviceId===d.id&&c.readyState===WebSocket.OPEN)}}
const buckets=new Map();
function limit(key,max,ms){const now=Date.now(), old=(buckets.get(key)||[]).filter(t=>t>now-ms);old.push(now);buckets.set(key,old);return old.length<=max}
setInterval(()=>{const n=Date.now();for(const [k,v] of buckets)if(v[v.length-1]<n-3600000)buckets.delete(k);run('DELETE FROM pair_codes WHERE expires<?',n).catch(e=>console.error('pair cleanup failed',e))},60000).unref();
function send(res,status,obj){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(obj))}
function body(req){return new Promise((resolve,reject)=>{let s='';req.on('data',b=>{s+=b;if(s.length>16384){reject(Error('Request too large'));req.destroy()}});req.on('end',()=>{try{resolve(JSON.parse(s||'{}'))}catch{reject(Error('Invalid JSON'))}});req.on('error',reject)})}
function bearer(req){return verify((req.headers.authorization||'').replace(/^Bearer /,''))}
const headers={'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY','Permissions-Policy':'geolocation=(self)','Content-Security-Policy':"default-src 'self'; script-src 'self' https://unpkg.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://tiles.openfreemap.org; connect-src 'self' ws: wss: https://tiles.openfreemap.org; worker-src blob:; frame-src 'none'; font-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"};
const publicDir=path.join(__dirname,'public');
const server=http.createServer(async(req,res)=>{for(const [k,v] of Object.entries(headers))res.setHeader(k,v);try{
 const u=new URL(req.url,BASE_URL), pathname=u.pathname;
 if(req.method==='GET'&&!pathname.startsWith('/api/')){const files={'/':'index.html','/device':'device.html','/app.js':'app.js','/device.js':'device.js','/style.css':'style.css','/manifest.webmanifest':'manifest.webmanifest','/sw.js':'sw.js','/icon.svg':'icon.svg','/vendor/maplibre-gl.js':'vendor/maplibre-gl.js','/vendor/maplibre-gl.css':'vendor/maplibre-gl.css'};const f=files[pathname];if(!f)return send(res,404,{error:'Not found'});res.writeHead(200,{'Content-Type':({'html':'text/html; charset=utf-8','js':'text/javascript; charset=utf-8','css':'text/css; charset=utf-8','webmanifest':'application/manifest+json','svg':'image/svg+xml'})[f.split('.').pop()]||'text/plain','Cache-Control':'no-cache'});return fs.createReadStream(path.join(publicDir,f)).pipe(res)}
 if(req.method==='GET'&&pathname==='/api/config')return send(res,200,{ephemeral});
 if(req.method==='GET'&&pathname==='/api/health'){await stmt('SELECT 1 AS ok');return send(res,200,{ok:true});}
 const ip=req.socket.remoteAddress; if(!limit('global:'+ip,240,60000))return send(res,429,{error:'Too many requests'});
 if(req.method==='POST'&&(pathname==='/api/register'||pathname==='/api/login')){if(!limit('auth:'+ip,12,15*60000))return send(res,429,{error:'Too many attempts. Try later.'});const {email,password}=await body(req);if(typeof email!=='string'||email.length>254||!/^\S+@\S+\.\S+$/.test(email)||typeof password!=='string'||password.length<12||password.length>200)return send(res,400,{error:'Enter a valid email and a password of 12–200 characters.'});const mail=email.toLowerCase().trim();let user=await stmt('SELECT * FROM users WHERE email=?',mail);if(pathname.endsWith('register')){if(user)return send(res,409,{error:'Account already exists.'});const salt=random(16);user={id:id(),email:mail,salt,pass:derive(password,salt)};await run('INSERT INTO users VALUES(?,?,?,?)',user.id,user.email,user.salt,user.pass)}else if(!user||!eq(derive(password,user.salt),user.pass))return send(res,401,{error:'Invalid credentials.'});return send(res,200,{token:issue(user.id),email:user.email})}
 if(req.method==='POST'&&pathname==='/api/pair'){if(!limit('pair:'+ip,10,15*60000))return send(res,429,{error:'Too many attempts. Try later.'});const {code,name}=await body(req);if(typeof code!=='string'||!/^\d{6}$/.test(code)||typeof name!=='string'||!name.trim()||name.length>60)return send(res,400,{error:'Enter the 6-digit code and a device name.'});const token=random(32), deviceId=id();const codeOwner=await consumeCode(hash('pair:'+SECRET+':'+code),deviceId,name.trim(),hash(token));if(!codeOwner)return send(res,400,{error:'Code invalid or expired.'});broadcast(codeOwner,{type:'devices_changed'});return send(res,200,{token,deviceId})}
 
 const owner=await bearer(req);if(!owner)return send(res,401,{error:'Sign in required.'});
 if(req.method==='GET'&&pathname==='/api/devices')return send(res,200,{devices:(await all('SELECT * FROM devices WHERE owner=? ORDER BY created_at DESC',owner)).map(visible)});
 if(req.method==='POST'&&pathname==='/api/pair-code'){await run('DELETE FROM pair_codes WHERE owner=? OR expires<?',owner,Date.now());let code;for(let i=0;i<10;i++){code=String(crypto.randomInt(0,1000000)).padStart(6,'0');if(!await stmt('SELECT hash FROM pair_codes WHERE hash=?',hash('pair:'+SECRET+':'+code)))break}await run('INSERT INTO pair_codes VALUES(?,?,?)',hash('pair:'+SECRET+':'+code),owner,Date.now()+300000);const url=BASE_URL.replace(/\/$/,'')+'/device';return send(res,200,{code,url,qr:await QRCode.toDataURL(url,{margin:1,width:180,color:{dark:'#101912',light:'#ffffff'}}),expiresAt:Date.now()+300000})}
 const m=pathname.match(/^\/api\/devices\/([0-9a-f-]+)\/(command|remove)$/);if(req.method==='POST'&&m){const d=await stmt('SELECT * FROM devices WHERE id=? AND owner=?',m[1],owner);if(!d)return send(res,404,{error:'Device not found.'});const b=await body(req);if(m[2]==='remove'){await run('DELETE FROM devices WHERE id=?',d.id);for(const c of clients)if(c.kind==='device'&&c.deviceId===d.id){c.send(JSON.stringify({type:'revoked'}));c.close()}broadcast(owner,{type:'devices_changed'});return send(res,200,{ok:true})}
 if(!['alarm','message','wipe'].includes(b.action))return send(res,400,{error:'Invalid action.'});if(b.action==='message'&&(typeof b.message!=='string'||!b.message.trim()||b.message.length>300||typeof b.contact!=='string'||b.contact.length>80))return send(res,400,{error:'Enter a message (up to 300 characters) and contact (up to 80 characters).'});if(b.action==='wipe'){if(!limit('wipe:'+ip,8,15*60000))return send(res,429,{error:'Too many attempts.'});const user=await stmt('SELECT * FROM users WHERE id=?',owner);if(typeof b.password!=='string'||b.password.length>200||!eq(derive(b.password,user.salt),user.pass))return send(res,403,{error:'Password incorrect.'})}
 const targets=[...clients].filter(c=>c.kind==='device'&&c.deviceId===d.id&&c.readyState===WebSocket.OPEN);if(!targets.length)return send(res,409,{error:'Device is offline. Commands require the companion page to be open.'});const cmd={type:'command',action:b.action,message:b.message,contact:b.contact};for(const c of targets)c.send(JSON.stringify(cmd));if(b.action==='wipe'){await run('DELETE FROM devices WHERE id=?',d.id);broadcast(owner,{type:'devices_changed'})}return send(res,200,{ok:true,notice:'Sent to connected device; delivery is not proof of execution.'})}
 return send(res,404,{error:'Not found'});
 }catch(e){console.error(e);if(!res.headersSent)send(res,500,{error:'Server error.'})}});
const wss=new WebSocketServer({noServer:true,maxPayload:8192});
server.on('upgrade',(req,socket,head)=>{const origin=req.headers.origin;if(origin!==base.origin){socket.destroy();return}if(new URL(req.url,BASE_URL).pathname!=='/ws'){socket.destroy();return}wss.handleUpgrade(req,socket,head,c=>wss.emit('connection',c,req))});
wss.on('connection',c=>{clients.add(c);const timer=setTimeout(()=>{if(!c.kind)c.close()},5000);c.on('message',async raw=>{try{let m;try{m=JSON.parse(raw)}catch{return c.close()}if(!c.kind){if(m.type!=='auth')return c.close();if(m.role==='dashboard'){const owner=await verify(m.token);if(!owner)return c.close();c.kind='dashboard';c.owner=owner;const payload=JSON.parse(Buffer.from(m.token.split('.')[1],'base64url'));setTimeout(()=>c.close(),Math.max(0,payload.exp*1000-Date.now())).unref()}else if(m.role==='device'&&typeof m.token==='string'){const d=await stmt('SELECT * FROM devices WHERE token_hash=?',hash(m.token));if(!d)return c.close(4001,'Pairing revoked');c.kind='device';c.deviceId=d.id;c.owner=d.owner;broadcast(d.owner,{type:'devices_changed'})}else return c.close();clearTimeout(timer);c.send(JSON.stringify({type:'authenticated'}));return}
 if(c.kind==='device'&&m.type==='telemetry'){const d=await stmt('SELECT * FROM devices WHERE id=? AND owner=?',c.deviceId,c.owner);if(!d)return c.close();const finite=n=>typeof n==='number'&&Number.isFinite(n);const loc=m.latitude==null&&m.longitude==null||(finite(m.latitude)&&m.latitude>=-90&&m.latitude<=90&&finite(m.longitude)&&m.longitude>=-180&&m.longitude<=180);if(!loc)return;const battery=finite(m.battery)&&m.battery>=0&&m.battery<=100?m.battery:null;const charging=typeof m.charging==='boolean'?Number(m.charging):null;await run('UPDATE devices SET latitude=COALESCE(?,latitude),longitude=COALESCE(?,longitude),accuracy=COALESCE(?,accuracy),location_at=COALESCE(?,location_at),battery=?,charging=?,location_active=?,updated_at=? WHERE id=?',m.latitude??null,m.longitude??null,finite(m.accuracy)?m.accuracy:null,m.latitude!=null&&finite(m.locationAt)?Math.min(m.locationAt,Date.now()):null,battery,charging,m.locationActive===true?1:0,Date.now(),d.id);broadcast(c.owner,{type:'devices_changed'})}}catch(e){console.error('websocket processing error',e);c.close()}});c.on('close',()=>{clearTimeout(timer);clients.delete(c);if(c.owner&&c.kind==='device')broadcast(c.owner,{type:'devices_changed'})});c.on('error',()=>{})});
initDatabase().then(()=>server.listen(PORT,HOST,()=>console.log(`NOCTIS listening on ${HOST}:${PORT}; public URL: ${BASE_URL}`))).catch(e=>{console.error('Database initialization failed',e);process.exit(1)});
