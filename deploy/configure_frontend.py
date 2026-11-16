#!/usr/bin/env python3
"""Create Vercel's live API proxy configuration after the real backend is available."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit


def configuration(origin):
    parsed = urlsplit(origin)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.path not in ('', '/') or parsed.query or parsed.fragment
            or parsed.hostname in ('localhost', 'example.com', 'api.example.com')):
        raise ValueError('Provide your real HTTPS backend origin, without a path or credentials.')
    return {
        'framework': 'vite',
        'buildCommand': 'npm run build',
        'outputDirectory': 'dist',
        'rewrites': [{'source': '/api/:path*', 'destination': origin.rstrip('/') + '/api/:path*'}],
        'headers': [{'source': '/api/:path*', 'headers': [{'key': 'Cache-Control', 'value': 'private, no-store'}]}],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backend_origin')
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'frontend/vercel.json')
    args = parser.parse_args()
    try:
        config = configuration(args.backend_origin)
    except ValueError as error:
        parser.error(str(error))
    args.output.write_text(json.dumps(config, indent=2) + '\n')
    print(f'Prepared {args.output}. Deploy frontend with VITE_PREVIEW_MODE=false after checking backend health.')


if __name__ == '__main__':
    main()
