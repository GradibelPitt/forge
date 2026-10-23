# Official card synchronization checkpoint

The machine-readable baseline is `../official-card-sync.json`. Read it before the next official-card import. The cutoff is **The Hobbit (HOB), 2026-08-14**, including HOC and later editions available in the local official checkout. Do not rescan the complete card catalog.

## 2026-10-22 checkpoint

- Official snapshot: `2ca9b8cab4ad8be7ce9535e783726d8bc2da4cf0`.
- DIY source parent: `7912bfbbfc094d4c767ef69910b66f38820065cb`.
- Runtime parent: `7565d792279b8a068774633ca21e7e8b898a4732`.
- Imported 354 missing scripts (366 faces), three token scripts, seven edition files, 17 required subtype definitions, and 89 available official Chinese translation records.
- Added only the required engine support for negative-power combat damage, the zero-loyalty planeswalker exception, and Vengeance secondary spells. Existing DIY static-ability indexing is retained.
- Initial compatibility check: 351/354 scripts instantiate; three fail because the above mechanisms are absent. After the patch, 354/354 instantiate. Four targeted behavior tests pass, covering damage precedence, controller/zone boundaries, graveyard-only casting, resolution/countering exile, and copied replacement effects.
- Existing scripts, including 51 different official revisions, are retained. They are explicitly recorded as `retained_existing_revision`, not falsely marked as imported.
- There are 14 edition snapshots, 1,712 collector-number entries and 998 implemented script records in the checkpoint. Entries without a matching official script remain pending; an edition listing alone is not an implementation.

## Next import

1. Resolve the local official checkout HEAD and compare it against the checkpoint's `upstreamCommit`.
2. Start with changed edition metadata and recorded script paths. Expand only into changed/new editions on or after the cutoff. Include additions to the existing sets; do not use a maximum card number as a completion watermark.
3. Use the exact collector number, face name and path in the JSON. `A-` rebalanced names must not collide with ordinary names, and secondary faces do not count as separate physical cards.
4. Skip unchanged Git blobs. For changed official files, compare the recorded LF-normalized SHA-256 with the current DIY bytes before editing. Preserve independent DIY changes and review intentionally retained revisions separately.
5. Revisit pending official-script entries, then include only their required tokens, types, edition registrations and engine support.
6. After testing and local deployment, advance the JSON checkpoint. Record the new official ref, every actual collector entry, script hash and the tests actually run. The source commit containing this file and the runtime `release.json` identify the published delivery.

The load test does not claim a complete simulated match for every imported card. Client restart/display verification is separate from script and engine verification.
