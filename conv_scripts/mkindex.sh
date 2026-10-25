#!/bin/sh

sed 's/■\(.*[^[:space:]]\)[[:space:]][[:space:]]*{[^}]*} :.*/\1/; s/■\([^:]*\) :.*/\1/' EIJIRO_UTF8.TXT > tmp_index.txt
uniq tmp_index.txt > index.txt
rm tmp_index.txt