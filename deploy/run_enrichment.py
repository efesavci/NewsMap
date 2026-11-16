#!/usr/bin/env python3
"""Run bounded OpenRouter enrichment with private local configuration."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
config = root / 'deploy/openrouter.env.local'
environment = dict(os.environ)
allowed = {'OPENROUTER_API_KEY', 'NEWSMAP_ENRICHMENT_MODEL', 'NEWSMAP_ENRICHMENT_BUDGET_USD', 'NEWSMAP_ENRICHMENT_MAX_EVENTS_PER_RUN'}
if config.exists():
    for line in config.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'): continue
        name, separator, value = line.partition('=')
        if separator and name.strip() in allowed: environment[name.strip()] = value.strip()
if not environment.get('OPENROUTER_API_KEY', '').strip():
    sys.exit('Add your key to deploy/openrouter.env.local; do not paste it into chat.')
jar = root / 'target/newsmap-1.0-SNAPSHOT.jar'
if not jar.exists(): sys.exit('Build the backend package before running enrichment.')
# No key in process arguments, logs, browser code or shell history.
raise SystemExit(subprocess.call(['java', '-Dloader.main=analysis.enrichment.EnrichmentMain', '-cp', str(jar),
                                 'org.springframework.boot.loader.launch.PropertiesLauncher'], cwd=root, env=environment))
