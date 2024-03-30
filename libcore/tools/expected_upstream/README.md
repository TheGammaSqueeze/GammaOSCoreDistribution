If you want to import files from the OpenJDK into `libcore/`, you are reading
the right documentation.

The general idea is to get a change from OpenJDK into libcore in AOSP by
`git merge` from an OpenJDK branch. However, each file in `ojluni/` can come
from a different OpenJDK version. `expected_upstream` is a staging branch
storing the OpenJDK version of each file. Thus, we can use `git merge` when
we update an `ojluni/` file from a new upstream version, and the command should
automatically merge the file if no merge conflict.

# Directory Layout
in the `aosp/expected_upstream` branch.
1. `ojluni/`
    * It has the same layout as the ojluni/ files in `aosp/master`
2. `EXPECTED_UPSTREAM` file
    * The table has 3 columns, i.e.
        1. Destination path in `ojluni/`
        2. Expected upstream version / an upstream git tag
        3. Upstream source path
    * The file format is like .csv file using a `,` separator
3. `tools/expected_upstream/`
    * Contains the tools

# Understanding your change
## Changes that shouldn't happen in the `aosp/expected_upstream` branch
In general, if you want to change an `ojluni/` file by a text editor / IDE
manually, you should make the change on `aosp/master`.

1. Changes to non-OpenJDK files
    * Those files are usually under the `luni/` folder, you can make the change
      directly on `aosp/master`
2. Adding / updating a patch to an existing `ojluni/` file
    * You can make the change directly on `aosp/master`. Please follow this
      [patch style guideline](https://goto.google.com/libcore-openjdk8-verify).
3. Cherry-picking a commit from upstream
    * You should first try to update an `ojluni/` file to a particular upstream
      version. If you can't but still want to cherry-pick a upstream fix, you
      should do so on the `aosp/master` branch.
4. Changes to non-OpenJDK files in `ojluni/`
    * Files, e.g. Android.bp, don't come from the upstream. You can make the
      change directly on `aosp/master`.

## Changes that should be made via the `aosp/expected_upstream` branch

1. Add or upgrade a file from the upstream OpenJDK
    * You are reading the right document! This documentation tells you how to
      import the file from the upstream. Later, you can merge the file and
      `expected_upstream` into `aosp/master` branch.
2. Remove an `ojluni/` file that originally came from the OpenJDK
    * Please remove the file on both `aosp/master` and `aosp/expected_upstream`
      branches. Don't forget to remove the entry in the `EXPECTED_UPSTREAM` too.
3. Revert the merge commit on `aosp/master` from `expected_upstream`
    * If you don't plan to re-land your change on `aosp/master`, you should
      probably revert the change `aosp/expected_upstream` as well.
    * If you plan to re-land your change, your re-landing commit won't be
      a merge commit, because `git` doesn't allow you to merge the same commit
      twice into the same branch. You have 2 options
        1. Revert your change on `expected_upsteam` too and start over again
          when you reland your change
        2. Just accept that the re-landing commit won't be a merge commit.

## Life of a typical change

Commit graph of a typical change
```text
----11.0.13-ga----------------   openjdk/jdk11u
         \
          A
           \
------------B-----C------------   expected_upstream
                   \
--------------------D---E------   master
```

Typically, you will need 5 CLs
* Commit `A` imports the file and moves the file in the `ojluni/` folder
* Commit `B` merges the file into the expected_upstream with other `ojluni`
  files
    * Commit `A` and `B` are created by the `ojluni_refresh_files` script
* Commit `C` edits the entry in the `EXPECTED_UPSTREAM` file
* Commit `D` is a merge commit created by `git merge`
* Commit `E` adds Android patches
    * Includes other changes to non-OpenJDK files, e.g. `Android.bp`,
      `api/current.txt`.

### Why can't have a single commit to replace the commits `A`, `B` and `C`?
* Preserve the upstream history. We can later `git blame` with the upstream
  history.

# Tools

## Prerequisite
* python3
* pip3
* A remote `aosp` is setup in your local git repository

## List of the scripts
### tools/expected_upstream/install_tools.sh
* Installs the dependency libraries
* Installs the other tools into your current shell process

### ojluni_modify_expectation
* Command line tool that can help modify the EXPECTED_UPSTREAM file

### ojluni_refresh_files
* Reads the EXPECTED_UPSTREAM file and updates the files contents in ojluni/
accordingly

## Workflow in command lines
### Setup
1. Switch to the expected_upstream branch
```shell
git branch <local_branch> aosp/expected_upstream
git checkout <local_branch>
```

2. Install tools
```shell
source ./tools/expected_upstream/install_tools.sh
```
## Upgrade a java class to a higher OpenJDK version
For example, upgrade `java.lang.String` to 11.0.13-ga version:

```shell
ojluni_modify_expectation modify java.lang.String jdk11u/jdk-11.0.13-ga
ojluni_refresh_files
```

or if `java.lang.String` is missing in EXPECTED_UPSTREAM:
```shell
ojluni_modify_expectation add jdk11u/jdk-11.0.13-ga java.lang.String
ojluni_refresh_files
```
2 commits should be created to update the `ojluni/src/main/java/java/lang/String.java`.
You can verify and view the diff by the following command

```shell
git diff aosp/expected_upstream -- ojluni/src/main/java/java/lang/String.java
```

You can then upload your change to AOSP gerrit.
```shell
repo upload --cbr -t . # -t sets a topic to the CLs in the gerrit
```

Remember to commit your EXPECTED_UPSTREAM file change into a new commit
```shell
git commit -- EXPECTED_UPSTREAM
```

Then upload your change to AOSP gerrit.
```shell
repo upload --cbr .
```

Then you can switch back to your local `master` branch to apply the changes
```shell
git checkout <local_master_branch>
git merge local_expected_upstream
# Resolve any merge conflict
git commit --amend # Amend the commit message and add the bug number you are working on
repo upload .
```

### Add a java test from the upstream

The process is similar to the above commands, but needs to run
`ojluni_modify_expectation` with an `add` subcommand.

For example, add a test for `String.isEmpty()` method:
```shell
ojluni_modify_expectation add jdk8u/jdk8u121-b13 java.lang.String.IsEmpty
```
Note: `java.lang.String.IsEmpty` is a test class in the upstream repository.

# Submit your change in [AOSP gerrit](http://r.android.com/)
```text
----11.0.13-ga----------------   openjdk/jdk11u
         \
          A
           \
------------B-----C------------   expected_upstream
                   \
--------------------D---E------   master
```
Here are the order of events / votes required to submit your CL on gerrit as of
Nov 2021.
1. `Presubmit-Verified +2` on all 5 CLs
   * Due to [b/204973624](http://b/204973624), you may `Bypass-Presubmit +1`
     on commit `A` and `B` if the presubmit fails.
2. `Code-review +2` on all 5 CLs from an Android Core Library team member
3. If needed, `API-review +1` on commit `E` from an Android API council member
4. Click the submit button / `Autosubmit +1` on commit `B`, `C` and `E`
    * Never submit commit `A` individually without submitting `B` together.
        * Otherwise, gerrit will create another merge commit from `A` without
          submitting `B`.
    * Due a Gerrit bug, you can't submit the commit `C` before submitting `B`
      first manually, even though `B` is the direct parent of `C`. So just
      submit `B` yourself manually.
    * If you can't submit the CL due a permission issue, ask an Android Core
      Library member to submit.

# Known bugs
* `repo upload` may not succeed because gerrit returns error.
    1. Just try to run `repo upload` again!
        * The initial upload takes a long time because it tries to sync with the
          remote AOSP gerrit server. The second upload is much faster because
          the `git` objects have been uploaded.
    2. `repo upload` returns TimeOutException, but the CL has been uploaded.
       Just find your CL in http://r.android.com/. See http://b/202848945
    3. Try to upload the merge commits 1 by 1
    ```shell
    git rev-parse HEAD # a sha is printed and you will need it later
    git reset HEAD~1 # reset to a earlier commit
    repo upload --cbr . # try to upload it again
    git reset <the sha printed above>
    ```
* After `ojluni_modify_expectation add` and `ojluni_refresh_files`, a `git commit -a`
  would include more files than just EXPECTED_UPSTREAM, because `git`, e.g. `git status`,
  isn't aware of changes in the working tree / in the file system. This can lead to
  an error when checking out the branch that is based on master.
    1. Do a `git checkout --hard <initial commit before the add>`
    2. Rerun the `ojluni_modify_expectation add` and `ojluni_refresh_files`
    3. `git stash && git stash pop`
    4. Commit the updated EXPECTED_UPSTREAM and proceed

# Report bugs
* Report bugs if the git repository is corrupt!
    * Sometimes, you can recover the repository by running `git reset aosp/expected_upstream`
