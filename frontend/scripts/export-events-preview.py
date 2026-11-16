#!/usr/bin/env python3
"""Export current geolocated events for the shareable, static map preview."""
import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
source = json.loads((ROOT / 'embeddings-service/outputs/events.json').read_text())
now = datetime.now(timezone.utc)
cutoff = now - timedelta(hours=48)

def recent(article):
    try:
        published = datetime.fromisoformat(article['publishTime'].replace('Z', '+00:00'))
        return cutoff <= published <= now
    except (KeyError, ValueError, TypeError):
        return False

def mapped(event):
    lat, lon = event.get('lat'), event.get('lon')
    return (isinstance(lat, (float, int)) and isinstance(lon, (float, int))
            and math.isfinite(lat) and math.isfinite(lon) and abs(lat) <= 90 and abs(lon) <= 180
            and (lat != 0 or lon != 0) and event.get('locationMethod') != 'unresolved')

events = [event for event in source['events'] if event['status'] == 'ACTIVE'
          and mapped(event) and any(recent(article) for article in event.get('articles', []))]
if not events:
    raise SystemExit('No geolocated reporting from the past 48 hours; existing preview was preserved.')
source['sourceGeneratedAt'] = source['generatedAt']
source['generatedAt'] = now.isoformat()
source['events'] = events
output = ROOT / 'frontend/public/preview/events.json'
output.parent.mkdir(parents=True, exist_ok=True)
temporary = output.with_suffix('.json.tmp')
temporary.write_text(json.dumps(source, ensure_ascii=False, separators=(',', ':')))
temporary.replace(output)
reports = [article for event in events for article in event['articles'] if recent(article)]
print(json.dumps({'events': len(events), 'reportsInWindow': len(reports),
                  'sources': sorted({article['source'] for article in reports}),
                  'snapshotAt': source['generatedAt']}))
