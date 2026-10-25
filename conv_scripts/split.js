const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const inputPath = path.resolve(process.argv[2] ?? 'EIJIRO_UTF8.TXT');
const outputDir = path.resolve(process.argv[3] ?? 'split');
const maxOpenFiles = 64;

fs.mkdirSync(outputDir, { recursive: true });

for (const name of fs.readdirSync(outputDir)) {
  if (/^(?:[a-z]{1,3}|before|after|0before|zzafter|zzzafter)\.txt$/.test(name)) {
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
    for await (const line of lines) {
      if (!line.startsWith('■')) {
        skipped++;
        continue;
      }

      const entry = line.slice(1);
      const separator = entry.indexOf(' : ');
      const headword = (separator === -1 ? entry : entry.slice(0, separator)).trimStart();
      const characters = Array.from(headword);
      if (characters.length === 0) {
        skipped++;
        continue;
      }

      const first = characters[0];
      let key;
      if (/^[a-z]$/i.test(first)) {
        key = first.toLowerCase();
        for (const character of characters.slice(1, 2)) {
          if (!/^[a-z]$/i.test(character)) {
            break;
          }
          key += character.toLowerCase();
        }
      } else {
        key = /^[\p{N}\p{P}\p{S}]/u.test(first) ? '0before' : 'zzzafter';
      }

      writeLine(getFileDescriptor(key), line);
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
