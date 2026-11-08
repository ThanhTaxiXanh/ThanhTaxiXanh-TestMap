# NM7 Forensic Phase 1 — Read-only header probe

## Goal and scope

Inspect an **authorized local copy** of a Navitel \`.nm7\` map on an unrooted Android phone. This tool **does not** decrypt, parse or export the map. It does not bypass licenses. No map bytes are uploaded to GitHub. Do not commit original Navitel \`.nm7\` or \`.nsc\` files to this repository.

### Grounded signature reference

TrID format definition:
https://github.com/digipres/digipres.github.io/blob/master/_sources/registries/trid/triddefs_xml/defs/n/nm7-navitel.trid.xml

The signature definition reports:
- At offset 0: hex \`47 4E 4D 33 04 00 00 00 FF FF FF FF\` (\`GNM3\` plus bytes).
- At offset 24: ASCII \`NIDXR\`.

The above is from **other sample files**; it is not yet verified for the user's exact \`VNM.nm7\`.

### Android / Termux

In Termux, run:

\`\`\`sh
termux-setup-storage
pkg update -y
pkg install -y python curl
cd ~/storage/downloads
curl -fL --output nm7_probe.py \
  "https://raw.githubusercontent.com/ThanhTaxiXanh/ThanhTaxiXanh-TestMap/forensics/nm7-header-probe-20261108/scripts/nm7_probe.py"
python nm7_probe.py VNM.nm7 --out VNM_nm7_audit.json
\`\`\`

If the filename is different or is in a different folder, pass the appropriate absolute path.

The script writes **metadata only**, not any map object data, to \`VNM_nm7_audit.json\`. It computes SHA-256 by streaming, samples entropy, verifies the known TrID header and checks candidate markers in the first MiB.

### Interpretation

- \`likely_trid_family=true\`: a **signature match only**; does **not** prove validity, authenticity, or successful decoding.
- \`NIDXR_at_24=false\`: may indicate a different version, damaged file, or a file not matching the known TrID family.
- \`entropy_bits_per_byte\` close to 8: may be compressed or encrypted data **or simply high-entropy binary**. This is **not** a decryption diagnosis.
- \`signature_like_patterns_first_1MiB\`: candidate raw byte coincidences; **not** validated compressed streams.
- \`speed_limit_records_decoded=0\` and \`traffic_sign_records_decoded=0\`: the probe does not decode any such records, not evidence of their absence.

### Checkpoints

1. Get exact \`VNM.nm7\` size, SHA-256, header and \`NIDXR\` locations.
2. Compare the report with a *different, authorized* Navitel NM7 sample from another version/region if available.
3. If evidence supports a stable container layout, build an index-offset reader **with range validation**.
4. Independently inspect \`VNM.nsc\` as a separate SpeedCam candidate, only with authorized data.
5. Build a clean-room TTX model with explicitly licensed traffic-sign and road-speed data.

### Security, permission and CI

- Never put \`.nm7\`, \`.nsc\`, raw map blocks or extracted licensed geodata into this public repository.
- The script accesses only the local input file and optional local JSON output.
- Existing sovereignty gate remains fail-closed and is not changed.
- CI uses **synthetic bytes** to test signature handling; a passing build does not prove parsing of real Navitel maps.
