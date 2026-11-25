#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Big Grit — prepare footage & photos for the website (requires ffmpeg).
#   Install ffmpeg: macOS `brew install ffmpeg` · Windows `winget install ffmpeg`
#
# VIDEO (makes desktop + vertical mobile versions + a poster frame):
#   ./scripts/optimize-media.sh video  ~/Footage/A001.mov  media/hero/hero-01
#     → media/hero/hero-01-desktop.mp4   1920×1080, H.264, no audio
#     → media/hero/hero-01-mobile.mp4    1080×1920 centre crop, H.264, no audio
#     → media/hero/hero-01-poster.jpg    first frame
#   Optional 4th/5th args: start time and length in seconds (default 0 and 8)
#     ./scripts/optimize-media.sh video A001.mov media/hero/hero-02 12.5 6
#
# LOOP (single file, e.g. training/detail loops — keeps source aspect, max 1600px):
#   ./scripts/optimize-media.sh loop  ~/Footage/B002.mov  media/training/training-01  3 8
#
# FILM (founder interview — keeps audio, 1080p):
#   ./scripts/optimize-media.sh film  ~/Footage/interview.mov  media/interview/founder-film
#
# PHOTO (max 2000px on the long edge, ~80% quality JPG):
#   ./scripts/optimize-media.sh photo ~/Photos/portrait.tif  media/founder/delone-portrait
# ---------------------------------------------------------------------------
set -euo pipefail
mode="${1:-}"; in="${2:-}"; out="${3:-}"; ss="${4:-0}"; len="${5:-8}"
if [[ -z "$mode" || -z "$in" || -z "$out" ]]; then sed -n '2,24p' "$0"; exit 1; fi
command -v ffmpeg >/dev/null || { echo "ffmpeg is not installed (see the top of this script)"; exit 1; }
mkdir -p "$(dirname "$out")"
X264=(-c:v libx264 -preset slow -profile:v high -pix_fmt yuv420p -movflags +faststart)

case "$mode" in
  video)
    ffmpeg -y -v error -ss "$ss" -t "$len" -i "$in" -an "${X264[@]}" -crf 25 -maxrate 6M -bufsize 12M \
      -vf "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,fps=30" "${out}-desktop.mp4"
    ffmpeg -y -v error -ss "$ss" -t "$len" -i "$in" -an "${X264[@]}" -crf 26 -maxrate 4M -bufsize 8M \
      -vf "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,fps=30" "${out}-mobile.mp4"
    ffmpeg -y -v error -i "${out}-desktop.mp4" -frames:v 1 -q:v 3 "${out}-poster.jpg"
    ;;
  loop)
    ffmpeg -y -v error -ss "$ss" -t "$len" -i "$in" -an "${X264[@]}" -crf 25 -maxrate 5M -bufsize 10M \
      -vf "scale='min(1600,iw)':-2,fps=30" "${out}.mp4"
    ffmpeg -y -v error -i "${out}.mp4" -frames:v 1 -q:v 3 "${out}-poster.jpg"
    ;;
  film)
    ffmpeg -y -v error -i "$in" "${X264[@]}" -crf 23 -vf "scale=-2:1080" -c:a aac -b:a 160k "${out}.mp4"
    ffmpeg -y -v error -ss 2 -i "${out}.mp4" -frames:v 1 -q:v 3 "${out}-poster.jpg"
    ;;
  photo)
    ffmpeg -y -v error -i "$in" -vf "scale='if(gt(iw,ih),min(2000,iw),-2)':'if(gt(iw,ih),-2,min(2000,ih))'" -q:v 4 "${out}.jpg"
    ;;
  *) echo "Unknown mode: $mode (use video, loop, film or photo)"; exit 1 ;;
esac
echo "Done:"; ls -lh "${out}"* 2>/dev/null
