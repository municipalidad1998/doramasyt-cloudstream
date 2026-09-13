#!/usr/bin/env node

const esbuild = require('esbuild');
const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'src');
const outDir = path.join(__dirname, 'providers');
const EXTERNAL_MODULES = ['cheerio-without-node-native', 'react-native-cheerio', 'cheerio', 'crypto-js', 'axios'];

function getProvidersToBuild() {
  const args = process.argv.slice(2).filter(arg => !arg.startsWith('-'));
  if (args.length > 0) return args;
  if (!fs.existsSync(srcDir)) process.exit(1);
  return fs.readdirSync(srcDir, { withFileTypes: true }).filter(d => d.isDirectory()).map(d => d.name);
}

async function buildProvider(providerName) {
  const entryPoint = path.join(srcDir, providerName, 'index.js');
  const outFile = path.join(outDir, `${providerName}.js`);
  if (!fs.existsSync(entryPoint)) return false;
  try {
    await esbuild.build({
      entryPoints: [entryPoint],
      bundle: true,
      outfile: outFile,
      format: 'cjs',
      platform: 'neutral',
      target: 'es2016',
      minify: false,
      sourcemap: false,
      external: EXTERNAL_MODULES,
      banner: { js: `/**\n * ${providerName} - Built from src/${providerName}/\n * Generated: ${new Date().toISOString()}\n */` },
      logLevel: 'warning'
    });
    return true;
  } catch (err) {
    console.error(`Failed to build ${providerName}:`, err.message);
    return false;
  }
}

async function main() {
  const providers = getProvidersToBuild();
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });
  let success = 0;
  for (const provider of providers) if (await buildProvider(provider)) success++;
  console.log(`Built ${success}/${providers.length} provider(s)`);
}

main().catch(err => { console.error(err); process.exit(1); });
