"""API-only fallback for downloading this small, public APK, not private files.

GitHub artifact storage is unavailable from some restricted clients. Workflow
annotations let those clients transfer the exact APK via api.github.com and
compare it to the independently emitted SHA256. Small chunks avoid annotation
truncation; eight per step stay within GitHub's per-step annotation limits.
Normal GitHub Actions artifacts remain the primary distribution method.
"""
import base64
import sys
from pathlib import Path

apk = Path('android/app/build/outputs/apk/debug/app-debug.apk').read_bytes()
encoded = base64.b64encode(apk).decode('ascii')
chunks = [encoded[i:i + 3500] for i in range(0, len(encoded), 3500)]
batch = int(sys.argv[1])
assert 0 <= batch < 3
if len(chunks) > 24:
    print('::notice::APK exceeds the API transfer size limit; use the Actions artifact.')
else:
    for index in range(batch * 8, min((batch + 1) * 8, len(chunks))):
        print(f'::notice title=APK transfer {index + 1:03d} of {len(chunks):03d}::{chunks[index]}')
