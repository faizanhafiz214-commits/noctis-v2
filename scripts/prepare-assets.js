'use strict';
const fs=require('node:fs');const path=require('node:path');
const root=path.join(__dirname,'..');const vendor=path.join(root,'public/vendor');fs.mkdirSync(vendor,{recursive:true});
for(const name of ['maplibre-gl.mjs','maplibre-gl-worker.mjs','maplibre-gl.css'])fs.copyFileSync(path.join(root,'node_modules/maplibre-gl/dist',name),path.join(vendor,name));
const encoded=path.join(root,'android/gradle/wrapper/gradle-wrapper.jar.base64');if(fs.existsSync(encoded))fs.writeFileSync(path.join(root,'android/gradle/wrapper/gradle-wrapper.jar'),Buffer.from(fs.readFileSync(encoded,'utf8'),'base64'));

const art=path.join(root,"scripts/radar.jpg.base64");if(fs.existsSync(art))fs.writeFileSync(path.join(root,"public/radar.jpg"),Buffer.from(fs.readFileSync(art,"utf8"),"base64"));
const assets=path.join(root,'android/app/src/main/assets/web');fs.mkdirSync(path.join(assets,'vendor'),{recursive:true});for(const name of ['index.html','device.html','app.js','device.js','v3.js','map-init.js','style.css','v4.css','manifest.webmanifest','sw.js','icon.svg','radar.jpg'])fs.copyFileSync(path.join(root,'public',name),path.join(assets,name));for(const name of ['maplibre-gl.mjs','maplibre-gl-worker.mjs','maplibre-gl.css'])fs.copyFileSync(path.join(vendor,name),path.join(assets,'vendor',name));
