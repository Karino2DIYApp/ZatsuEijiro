const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const inputPath = path.resolve(process.argv[2] ?? 'index.txt');
const outputDir = path.resolve(process.argv[3] ?? 'split_index_2ch');
const maxOpenFiles = 64;

fs.mkdirSync(outputDir, { recursive: true });

for (const name of fs.readdirSync(outputDir)) {
  if (/^(?:[a-z]{1,2}|0before|zafter)\.txt$/.test(name)) {
    fs.unlinkSync(path.join(outputDir, name));
  }
}

const input = fs.createReadStream(inputPath, { encoding: 'utf8' });
const lines = readline.createInterface({ input, crlfDelay: Infinity });
const openFiles = new Map();
const initializedFiles = new Set();
let skipped = 0;
let written = 0;

function getFileDescriptor(key) {
  if (openFiles.has(key)) {
    const fd = openFiles.get(key);
    openFiles.delete(key);
    openFiles.set(key, fd);
    return fd;
  }

  if (openFiles.size >= maxOpenFiles) {
    const oldestKey = openFiles.keys().next().value;
    fs.closeSync(openFiles.get(oldestKey));
    openFiles.delete(oldestKey);
  }

  const filePath = path.join(outputDir, `${key}.txt`);
  const flags = initializedFiles.has(key) ? 'a' : 'w';
  const fd = fs.openSync(filePath, flags);
  initializedFiles.add(key);
  openFiles.set(key, fd);
  return fd;
}

function writeLine(fd, text) {
  const buffer = Buffer.from(`${text}\n`, 'utf8');
  let offset = 0;
  while (offset < buffer.length) {
    offset += fs.writeSync(fd, buffer, offset, buffer.length - offset);
  }
}

async function main() {
  try {
    for await (const entry of lines) {
      if (entry.length === 0) {
        skipped++;
        continue;
      }

      const characters = Array.from(entry);
      if (characters.length === 0) {
        skipped++;
        continue;
      }

      const first = characters[0];
      let key;
      if (/^[a-z]$/i.test(first)) {
        key = first.toLowerCase();
        if (characters[1] && /^[a-z]$/i.test(characters[1])) {
          key += characters[1].toLowerCase();
        }
      } else {
        key = /^[\p{N}\p{P}\p{S}]/u.test(first) ? '0before' : 'zzafter';
      }

      writeLine(getFileDescriptor(key), entry);
      written++;
    }
  } finally {
    for (const fd of openFiles.values()) {
      fs.closeSync(fd);
    }
  }
}

main().then(() => {
  console.log(`Wrote ${written} entries to ${outputDir}; skipped ${skipped} lines.`);
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
