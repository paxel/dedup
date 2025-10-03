# Dedup

Treats your files as repositories and allows finding duplicates in the repositories and outside of them




## Config dir

**config:** ~/.config/dedup/

## Concept: Repo

* Each repo consists of x index DBs
* They are separated by modulo x filesize
* The Repo manages the index files in a way that the outside is unaware of them

~/.config/dedup/repos/myRepo/dedup_repo.cfg

| Name     | Absolute Path (normalized path) | Index files |
| -------- | ------------------------------- | ----------- |
| immiches | /home/user/immich               | 10          |

### Concept: Index file

* All files of an index are written as logs in a way that all entries are Read only until pruned
* When reading the last path wins
    * if it is marked deleted it is considered not existent
* The lookup always has to happen size -> hash because the hash alone is irrelevant. see Processes

~/.config/dedup/repos/myRepo/01.idx

| Relative path (normalized path) | size (bytes) | hash (string) | deleted (date or null) | last modified (date) |
| ------------------------------- | ------------ | ------------- | ---------------------- | -------------------- |
| my/file                         | 12442        | SHA1-string   |                        | Nov 23               |

## Processes

## Opening a Repo

* check for bak files
* for each
    * rename [xxx].bak to xxx
    * if fail
        * fail process with corruption error saying exactly what's the case and how to fix it
* read all .idx files

### Create a Repo

`dedup repo create <name> <path> -i 10`

* Creates the config dir
* Creates the dedup_repo.cfg
* Creates the idx files

### List Repos

`dedup repo ls`

* lists the repo names

### Delete Repo

`dedup repo rm [<name> | --all]`

* deletes the subdir under repos


### Update Repo

`dedup repo update [<name> | --all]`

* Reads the current repo DB
* Iterates over the files and
    * Adds files that
        * Have been deleted since
        * Have been added since
        * Have changed their last modified date without a change

#### Logic of updating a repo

* read the database
* build a set of paths to existing files
* iterate over the files on disk
* for each
    * check if path is in lookup
        * if not - addFile()
        * else check if size is equal
            * if not - addFile()
            * else check if last modified is equal
                * if not - addFile ()
                * else check force hash config
                    * if yes - hash file and check if hash is equal
                        * if not add file
                        * else continue
                    * else assume equal and continue
                    * endif forced hash
                * endif last modified
            * endif size equal
        * endif path found
* mark all left in lookup as deleted

### Prune Repo

Pruning a repo means remove all deprecated information

`dedup repo prune [<name> | --all]`

* Rename the index files to \[old_name].bak
* Create new index files
* For each bak file
    * Read it completely
    * remove all but last state
    * for each remaining entry
        * Add file to the index files
            * Is added to the index file modulo [number]
            * Only the last state is added
            * Also the deleted are added to keep the info that this file existed
* Delete the bak file



## Agenda

- [x] CLI main with simple argument parsing (JCommander)
- [ ] Config Dir Management (use default)
- [ ] Simple Repository Management (query Config Dir for Repository)
- [ ] List Repo command