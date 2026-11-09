#!/usr/bin/env bash
# Genera docs/manual/Manual-de-usuario-Bellidental.pdf (requiere xelatex y latexmk).
set -euo pipefail
cd "$(dirname "$0")"
latexmk -xelatex -interaction=nonstopmode -halt-on-error -outdir=build -quiet manual-usuario.tex >/dev/null
cp build/manual-usuario.pdf Manual-de-usuario-Bellidental.pdf
echo "PDF: $(pwd)/Manual-de-usuario-Bellidental.pdf"
