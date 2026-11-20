#!/usr/bin/env bash
# Ask Codex (gpt-6-luna, low effort) which prefix-only pairs are real opposites, then write the bundled TSV.
# Usage: content-tools/review_negation_candidates.sh [OUT_DIR]
#   OUT_DIR defaults to content-tools/out/negation (made by build_dictionary_negation_links.py).
# Codex output goes to OUT_DIR/logs; this script prints only a short summary.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
dir="$(cd "${1:-$root/content-tools/out/negation}" && pwd)"
dest="$root/composeApp/src/commonMain/composeResources/files/fr/lexicon/dictionary_negation_links.tsv"
mkdir -p "$dir/chunks" "$dir/logs"
rm -f "$dir"/chunks/*
split -l 250 "$dir/negation_candidates.tsv" "$dir/chunks/part_"

for chunk in "$dir"/chunks/part_??; do
  keep="$chunk.keep"
  [ -s "$keep" ] && continue  # resume: chunk already reviewed
  echo "reviewing $(basename "$chunk")"
  "$root/scripts/codex-run.sh" low "Read $chunk. Each line is: French word, part of speech, other word (TAB-separated).
Keep a line only if the other word is the common opposite or negation of the first word in normal French
(e.g. compréhensible/incompréhensible, faire/défaire, plaisir/déplaisir, légal/illégal).
Drop lines where the prefix is a coincidence or the meaning is unrelated or only technical
(e.g. signer/désigner, partir/impartir, migration/immigration, formateur/informateur).
Write the kept lines unchanged, one per line, to $keep. Do not change any other file. Reply only: done." \
    > "$dir/logs/$(basename "$chunk").log" 2>&1 || { echo "codex failed on $chunk, see logs"; exit 1; }
  touch "$keep"
done

# Only lines that really were candidates survive; Codex cannot add words.
cat "$dir"/chunks/*.keep | grep -Fxf "$dir/negation_candidates.tsv" > "$dir/negation_reviewed.tsv" || true
# Unconfirmed a-/an- pairs are mostly coincidences (adoucir, agrandir, anesthésie), so only Wiktionary-backed ones stay.
# Order matters: the app keeps the first opposite per headword, so Wiktionary pairs lead and in-/im-/il-/ir- beat dé-/mé-.
rank() { awk -F'\t' '{ p = substr($3, 1, length($3) - length($1)); r = (p ~ /^(in|im|il|ir)$/) ? 1 : (p ~ /^(non-|dé|dés)$/) ? 2 : (p ~ /^(mé|més)$/) ? 3 : 9; if (r < 9) print r "\t" $0 }'; }
{
  echo "# French Wiktionary contributors (CC BY-SA 4.0), via Kaikki. Source pages: https://fr.wiktionary.org/wiki/{word}"
  echo "# Columns: headword, part of speech, opposite formed with a negative prefix"
  # Hand-checked false positives the model kept.
  { sort -u "$dir/negation_confirmed.tsv"; grep -vE $'^(primer\tverb\tdéprimer|gouter\tverb\tdégouter|lavage\tnoun\tdélavage)$' "$dir/negation_reviewed.tsv" | rank | sort -t$'\t' -k1,1n -k2 | cut -f2-; } \
    | awk -F'\t' '!seen[$1 FS $2]++ && !seen[$3 FS $2]++'
} > "$dest"
echo "kept $(wc -l < "$dir/negation_reviewed.tsv") of $(wc -l < "$dir/negation_candidates.tsv") candidates; wrote $(($(wc -l < "$dest") - 2)) pairs to $dest"
