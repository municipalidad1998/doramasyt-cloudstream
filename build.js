const fs = require("fs");
const path = require("path");
const { build } = require("esbuild");

const provider = process.argv[2] || "doramasyt";
const entry = path.join(process.cwd(), "src", provider, "index.js");
const outfile = path.join(process.cwd(), "providers", provider + ".js");

(async () => {
  try {
    await build({
      entryPoints: [entry],
      outfile,
      bundle: true,
      format: "cjs",
      platform: "neutral",
      target: "es2020",
      minify: false,
      sourcemap: false
    });
    console.log("Built " + outfile);
  } catch (e) {
    console.error(e);
    process.exit(1);
  }
})();
