#!/usr/bin/env python3
"""Reject new lint errors while reporting the existing integration baseline honestly."""
import collections
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
report = Path(sys.argv[1])
baseline = json.loads((root / 'release/lint-errors-baseline.json').read_text())
allowed = collections.Counter({(i['id'], i['message'], i['path']): i['count']
                               for i in baseline['issues']})
actual = collections.Counter()
for issue in ET.parse(report).getroot().findall('issue'):
    if issue.get('severity') not in ('Error', 'Fatal'):
        continue
    location = issue.find('location')
    path = location.get('file', '') if location is not None else ''
    if path:
        path = str(Path(os.path.abspath(path)).relative_to(root))
    actual[(issue.get('id'), issue.get('message'), path)] += 1
new = actual - allowed
print(f'Lint: {sum(actual.values())} total error/fatal findings; '
      f'{sum(new.values())} new; {sum((allowed - actual).values())} no longer reported.')
for (issue_id, message, path), count in new.items():
    print(f'NEW ({count}) {issue_id}: {path}: {message}', file=sys.stderr)
if new:
    sys.exit(1)
