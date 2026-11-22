'use strict';
const fs=require('node:fs');const path=require('node:path');
const root=path.join(__dirname,'..');const vendor=path.join(root,'public/vendor');fs.mkdirSync(vendor,{recursive:true});
for(const name of ['maplibre-gl.js','maplibre-gl.css'])fs.copyFileSync(path.join(root,'node_modules/maplibre-gl/dist',name),path.join(vendor,name));
const encoded=path.join(root,'android/gradle/wrapper/gradle-wrapper.jar.base64');if(fs.existsSync(encoded))fs.writeFileSync(path.join(root,'android/gradle/wrapper/gradle-wrapper.jar'),Buffer.from(fs.readFileSync(encoded,'utf8'),'base64'));
