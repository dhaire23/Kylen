#!/usr/bin/env bash
# Replace the YOUR-DOMAIN.com placeholder in SEO/social tags, robots.txt and sitemap.xml.
# Usage: ./scripts/set-domain.sh biggritperformance.com
set -euo pipefail
d="${1:-}"; d="${d#https://}"; d="${d#http://}"; d="${d%/}"
[[ -z "$d" ]] && { echo "Usage: $0 yourdomain.com"; exit 1; }
cd "$(dirname "$0")/.."
for f in index.html robots.txt sitemap.xml; do
  sed -i.bak "s#YOUR-DOMAIN\.com#${d}#g" "$f" && rm -f "$f.bak"
done
echo "Domain set to https://${d}/ in index.html, robots.txt and sitemap.xml"
