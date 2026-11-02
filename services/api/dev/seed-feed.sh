#!/usr/bin/env bash
# Dados de exemplo do feed SOMENTE para desenvolvimento local.
# Nunca rode em producao: cria operadores e posts ficticios marcados com [DEV].
#
# Uso (Git Bash ou Linux), com o banco local ja migrado pela API:
#   PGHOST=localhost PGPORT=55440 PGUSER=oz PGDATABASE=oz MEDIA_ROOT=./data/media services/api/dev/seed-feed.sh
set -euo pipefail

: "${PGDATABASE:?Defina PGDATABASE (banco local de desenvolvimento)}"
: "${MEDIA_ROOT:?Defina MEDIA_ROOT (mesmo valor usado pela API)}"
case "${PGHOST:-localhost}" in
  localhost|127.0.0.1|::1) ;;
  *) echo "Recusado: o seed do feed so roda contra um banco local." >&2; exit 1 ;;
esac

here="$(cd "$(dirname "$0")" && pwd)"
dir="$MEDIA_ROOT/dev-seed"
mkdir -p "$dir"
ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }

# Gera um video H.264 + poster + previa desfocada a partir de uma fonte sintetica do ffmpeg.
video() {
  local name="$1" source="$2" size="$3"
  mkdir -p "$dir/$name"
  ff -f lavfi -i "$source=size=$size:rate=30" -f lavfi -i "sine=frequency=220:sample_rate=44100" -t 8 \
     -c:v libx264 -pix_fmt yuv420p -profile:v main -preset veryfast -crf 26 -movflags +faststart \
     -c:a aac -b:a 96k -shortest "$dir/$name/720.mp4"
  ff -i "$dir/$name/720.mp4" -frames:v 1 -c:v libwebp -quality 80 "$dir/$name/poster.webp"
  ff -i "$dir/$name/720.mp4" -frames:v 1 -vf "scale=16:-2" -c:v libwebp -quality 40 "$dir/$name/blur.webp"
}

image() {
  local name="$1" source="$2" size="$3"
  mkdir -p "$dir/$name"
  ff -f lavfi -i "$source=size=$size" -frames:v 1 -c:v libwebp -quality 82 "$dir/$name/1080.webp"
  ff -i "$dir/$name/1080.webp" -vf "scale=540:-2" -c:v libwebp -quality 78 "$dir/$name/540.webp"
  ff -i "$dir/$name/1080.webp" -vf "scale=16:-2" -c:v libwebp -quality 40 "$dir/$name/blur.webp"
}

video v1 testsrc2 720x1280
video v2 mandelbrot 720x1280
video v3 life 720x1280
video v4 testsrc 1280x720
image i1 mandelbrot 1080x1350
image i2 testsrc2 1080x1080

blur() { printf 'data:image/webp;base64,%s' "$(base64 -w0 "$dir/$1/blur.webp")"; }

psql -v ON_ERROR_STOP=1 -q \
  -v blur_v1="$(blur v1)" -v blur_v2="$(blur v2)" -v blur_v3="$(blur v3)" -v blur_v4="$(blur v4)" \
  -v blur_i1="$(blur i1)" -v blur_i2="$(blur i2)" \
  -f "$here/seed-feed.sql"
echo "Seed do feed aplicado: 6 posts de exemplo em $dir"
