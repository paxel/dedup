# Active Bugs

1. **Add New Repository: Invalid/Duplicate Names**
   The "Add New Repository" dialog suggests invalid repo names (e.g., with blanks) and doesn't prevent adding a repository with a name that already exists. The proposed repo name should be valid and unique.

2. **Duplicate Page: Rendering/State Issue**
   The duplicate page sometimes shows only a background (gray background). This indicates a possible React rendering crash or a state that doesn't resolve correctly after scanning.

3. **Cancel Button: Hashing Process**
   The "Cancel" button for the hashing process during repository updates is reported as not working. 

4. **Index Decoding Error: Missing "h" Property**
   The index file (e.g., `0.idx`) fails to decode records, reporting a missing required creator property `h` (index 0) in `paxel.dedup.domain.model.RepoFile`. This leads to "corruption detected, repairing index file" messages.
