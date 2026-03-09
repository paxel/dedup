# Active Bugs

1. **Add New Repository: Invalid/Duplicate Names**
   The "Add New Repository" dialog suggests invalid repo names (e.g., with blanks) and doesn't prevent adding a repository with a name that already exists. The proposed repo name should be valid and unique.

2. **Duplicate Page: Rendering/State Issue**
   The duplicate page always shows only a background (gray background). This indicates a possible React rendering crash or a state that doesn't resolve correctly after scanning.

3. **Cancel Button: Hashing Process**
   The "Cancel" button for the hashing process during repository updates is reported as not working.
   Starting background update process for: private_media_local
   Cancel requested for repository: private_media_local
   Update completed successfully for: private_media_local
   The progress overlay vanishes for a second then reappears with 0 values. my assumption would be that the cancel goes to the backend, but another event comes through in the meantime and revives the progress. there is no log that the hashing ends, so I assume it is still running

4. **Index Decoding Error: Missing "h" Property**
   The index file (e.g., `0.idx`) fails to decode records, reporting a missing required creator property `h` (index 0) in `paxel.dedup.domain.model.RepoFile`. This leads to "corruption detected, repairing index file" messages.
