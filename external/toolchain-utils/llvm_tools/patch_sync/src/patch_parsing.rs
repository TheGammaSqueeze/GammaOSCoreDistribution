use std::collections::{BTreeMap, BTreeSet};
use std::fs::{copy, File};
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

/// JSON serde struct.
// FIXME(b/221489531): Remove when we clear out start_version and
// end_version.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PatchDictSchema {
    /// [deprecated(since = "1.1", note = "Use version_range")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub end_version: Option<u64>,
    pub metadata: Option<BTreeMap<String, serde_json::Value>>,
    #[serde(default, skip_serializing_if = "BTreeSet::is_empty")]
    pub platforms: BTreeSet<String>,
    pub rel_patch_path: String,
    /// [deprecated(since = "1.1", note = "Use version_range")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub start_version: Option<u64>,
    pub version_range: Option<VersionRange>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct VersionRange {
    pub from: Option<u64>,
    pub until: Option<u64>,
}

// FIXME(b/221489531): Remove when we clear out start_version and
// end_version.
impl PatchDictSchema {
    pub fn get_start_version(&self) -> Option<u64> {
        self.version_range
            .map(|x| x.from)
            .unwrap_or(self.start_version)
    }

    pub fn get_end_version(&self) -> Option<u64> {
        self.version_range
            .map(|x| x.until)
            .unwrap_or(self.end_version)
    }
}

/// Struct to keep track of patches and their relative paths.
#[derive(Debug, Clone)]
pub struct PatchCollection {
    pub patches: Vec<PatchDictSchema>,
    pub workdir: PathBuf,
}

impl PatchCollection {
    /// Create a `PatchCollection` from a PATCHES.
    pub fn parse_from_file(json_file: &Path) -> Result<Self> {
        Ok(Self {
            patches: serde_json::from_reader(File::open(json_file)?)?,
            workdir: json_file
                .parent()
                .ok_or_else(|| anyhow!("failed to get json_file parent"))?
                .to_path_buf(),
        })
    }

    /// Create a `PatchCollection` from a string literal and a workdir.
    pub fn parse_from_str(workdir: PathBuf, contents: &str) -> Result<Self> {
        Ok(Self {
            patches: serde_json::from_str(contents).context("parsing from str")?,
            workdir,
        })
    }

    /// Copy this collection with patches filtered by given criterion.
    pub fn filter_patches(&self, f: impl FnMut(&PatchDictSchema) -> bool) -> Self {
        Self {
            patches: self.patches.iter().cloned().filter(f).collect(),
            workdir: self.workdir.clone(),
        }
    }

    /// Map over the patches.
    pub fn map_patches(&self, f: impl FnMut(&PatchDictSchema) -> PatchDictSchema) -> Self {
        Self {
            patches: self.patches.iter().map(f).collect(),
            workdir: self.workdir.clone(),
        }
    }

    /// Return true if the collection is tracking any patches.
    pub fn is_empty(&self) -> bool {
        self.patches.is_empty()
    }

    /// Compute the set-set subtraction, returning a new `PatchCollection` which
    /// keeps the minuend's workdir.
    pub fn subtract(&self, subtrahend: &Self) -> Result<Self> {
        let mut new_patches = Vec::new();
        // This is O(n^2) when it could be much faster, but n is always going to be less
        // than 1k and speed is not important here.
        for our_patch in &self.patches {
            let found_in_sub = subtrahend.patches.iter().any(|sub_patch| {
                let hash1 = subtrahend
                    .hash_from_rel_patch(sub_patch)
                    .expect("getting hash from subtrahend patch");
                let hash2 = self
                    .hash_from_rel_patch(our_patch)
                    .expect("getting hash from our patch");
                hash1 == hash2
            });
            if !found_in_sub {
                new_patches.push(our_patch.clone());
            }
        }
        Ok(Self {
            patches: new_patches,
            workdir: self.workdir.clone(),
        })
    }

    pub fn union(&self, other: &Self) -> Result<Self> {
        self.union_helper(
            other,
            |p| self.hash_from_rel_patch(p),
            |p| other.hash_from_rel_patch(p),
        )
    }

    fn union_helper(
        &self,
        other: &Self,
        our_hash_f: impl Fn(&PatchDictSchema) -> Result<String>,
        their_hash_f: impl Fn(&PatchDictSchema) -> Result<String>,
    ) -> Result<Self> {
        // 1. For all our patches:
        //   a. If there exists a matching patch hash from `other`:
        //     i. Create a new patch with merged platform info,
        //     ii. add the new patch to our new collection.
        //     iii. Mark the other patch as "merged"
        //   b. Otherwise, copy our patch to the new collection
        // 2. For all unmerged patches from the `other`
        //   a. Copy their patch into the new collection
        let mut combined_patches = Vec::new();
        let mut other_merged = vec![false; other.patches.len()];

        // 1.
        for p in &self.patches {
            let our_hash = our_hash_f(p)?;
            let mut found = false;
            // a.
            for (idx, merged) in other_merged.iter_mut().enumerate() {
                if !*merged {
                    let other_p = &other.patches[idx];
                    let their_hash = their_hash_f(other_p)?;
                    if our_hash == their_hash {
                        // i.
                        let new_platforms =
                            p.platforms.union(&other_p.platforms).cloned().collect();
                        // ii.
                        combined_patches.push(PatchDictSchema {
                            rel_patch_path: p.rel_patch_path.clone(),
                            start_version: p.start_version,
                            end_version: p.end_version,
                            platforms: new_platforms,
                            metadata: p.metadata.clone(),
                            version_range: p.version_range,
                        });
                        // iii.
                        *merged = true;
                        found = true;
                        break;
                    }
                }
            }
            // b.
            if !found {
                combined_patches.push(p.clone());
            }
        }
        // 2.
        // Add any remaining, other-only patches.
        for (idx, merged) in other_merged.iter().enumerate() {
            if !*merged {
                combined_patches.push(other.patches[idx].clone());
            }
        }

        Ok(Self {
            workdir: self.workdir.clone(),
            patches: combined_patches,
        })
    }

    /// Copy all patches from this collection into another existing collection, and write that
    /// to the existing collection's file.
    pub fn transpose_write(&self, existing_collection: &mut Self) -> Result<()> {
        for p in &self.patches {
            let original_file_path = self.workdir.join(&p.rel_patch_path);
            let copy_file_path = existing_collection.workdir.join(&p.rel_patch_path);
            copy_create_parents(&original_file_path, &copy_file_path)?;
            existing_collection.patches.push(p.clone());
        }
        existing_collection.write_patches_json("PATCHES.json")
    }

    /// Write out the patch collection contents to a PATCHES.json file.
    fn write_patches_json(&self, filename: &str) -> Result<()> {
        let write_path = self.workdir.join(filename);
        let mut new_patches_file = File::create(&write_path)
            .with_context(|| format!("writing to {}", write_path.display()))?;
        new_patches_file.write_all(self.serialize_patches()?.as_bytes())?;
        Ok(())
    }

    pub fn serialize_patches(&self) -> Result<String> {
        let mut serialization_buffer = Vec::<u8>::new();
        // Four spaces to indent json serialization.
        let mut serializer = serde_json::Serializer::with_formatter(
            &mut serialization_buffer,
            serde_json::ser::PrettyFormatter::with_indent(b"    "),
        );
        self.patches
            .serialize(&mut serializer)
            .context("serializing patches to JSON")?;
        // Append a newline at the end if not present. This is necessary to get
        // past some pre-upload hooks.
        if serialization_buffer.last() != Some(&b'\n') {
            serialization_buffer.push(b'\n');
        }
        Ok(std::str::from_utf8(&serialization_buffer)?.to_string())
    }

    /// Return whether a given patch actually exists on the file system.
    pub fn patch_exists(&self, patch: &PatchDictSchema) -> bool {
        self.workdir.join(&patch.rel_patch_path).exists()
    }

    fn hash_from_rel_patch(&self, patch: &PatchDictSchema) -> Result<String> {
        hash_from_patch_path(&self.workdir.join(&patch.rel_patch_path))
    }
}

impl std::fmt::Display for PatchCollection {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        for (i, p) in self.patches.iter().enumerate() {
            let title = p
                .metadata
                .as_ref()
                .and_then(|x| x.get("title"))
                .and_then(serde_json::Value::as_str)
                .unwrap_or("[No Title]");
            let path = self.workdir.join(&p.rel_patch_path);
            writeln!(f, "* {}", title)?;
            if i == self.patches.len() - 1 {
                write!(f, "  {}", path.display())?;
            } else {
                writeln!(f, "  {}", path.display())?;
            }
        }
        Ok(())
    }
}

/// Generate a PatchCollection incorporating only the diff between current patches and old patch
/// contents.
pub fn new_patches(
    patches_path: &Path,
    old_patch_contents: &str,
    platform: &str,
) -> Result<(PatchCollection, PatchCollection)> {
    let cur_collection = PatchCollection::parse_from_file(patches_path)
        .with_context(|| format!("parsing {} PATCHES.json", platform))?;
    let cur_collection = filter_patches_by_platform(&cur_collection, platform);
    let cur_collection = cur_collection.filter_patches(|p| cur_collection.patch_exists(p));
    let new_patches: PatchCollection = {
        let old_collection = PatchCollection::parse_from_str(
            patches_path.parent().unwrap().to_path_buf(),
            old_patch_contents,
        )?;
        let old_collection = old_collection.filter_patches(|p| old_collection.patch_exists(p));
        cur_collection.subtract(&old_collection)?
    };
    let new_patches = new_patches.map_patches(|p| {
        let mut platforms = BTreeSet::new();
        platforms.extend(["android".to_string(), "chromiumos".to_string()]);
        PatchDictSchema {
            platforms: platforms.union(&p.platforms).cloned().collect(),
            ..p.to_owned()
        }
    });
    Ok((cur_collection, new_patches))
}

/// Create a new collection with only the patches that apply to the
/// given platform.
///
/// If there's no platform listed, the patch should still apply if the patch file exists.
pub fn filter_patches_by_platform(collection: &PatchCollection, platform: &str) -> PatchCollection {
    collection.filter_patches(|p| {
        p.platforms.contains(platform) || (p.platforms.is_empty() && collection.patch_exists(p))
    })
}

/// Get the hash from the patch file contents.
///
/// Not every patch file actually contains its own hash,
/// we must compute the hash ourselves when it's not found.
fn hash_from_patch(patch_contents: impl Read) -> Result<String> {
    let mut reader = BufReader::new(patch_contents);
    let mut buf = String::new();
    reader.read_line(&mut buf)?;
    let mut first_line_iter = buf.trim().split(' ').fuse();
    let (fst_word, snd_word) = (first_line_iter.next(), first_line_iter.next());
    if let (Some("commit" | "From"), Some(hash_str)) = (fst_word, snd_word) {
        // If the first line starts with either "commit" or "From", the following
        // text is almost certainly a commit hash.
        Ok(hash_str.to_string())
    } else {
        // This is an annoying case where the patch isn't actually a commit.
        // So we'll hash the entire file, and hope that's sufficient.
        let mut hasher = Sha256::new();
        hasher.update(&buf); // Have to hash the first line.
        reader.read_to_string(&mut buf)?;
        hasher.update(buf); // Hash the rest of the file.
        let sha = hasher.finalize();
        Ok(format!("{:x}", &sha))
    }
}

fn hash_from_patch_path(patch: &Path) -> Result<String> {
    let f = File::open(patch).with_context(|| format!("opening patch file {}", patch.display()))?;
    hash_from_patch(f)
}

/// Copy a file from one path to another, and create any parent
/// directories along the way.
fn copy_create_parents(from: &Path, to: &Path) -> Result<()> {
    let to_parent = to
        .parent()
        .with_context(|| format!("getting parent of {}", to.display()))?;
    if !to_parent.exists() {
        std::fs::create_dir_all(to_parent)?;
    }

    copy(&from, &to)
        .with_context(|| format!("copying file from {} to {}", &from.display(), &to.display()))?;
    Ok(())
}

#[cfg(test)]
mod test {

    use super::*;

    /// Test we can extract the hash from patch files.
    #[test]
    fn test_hash_from_patch() {
        // Example git patch from Gerrit
        let desired_hash = "004be4037e1e9c6092323c5c9268acb3ecf9176c";
        let test_file_contents = "commit 004be4037e1e9c6092323c5c9268acb3ecf9176c\n\
            Author: An Author <some_email>\n\
            Date:   Thu Aug 6 12:34:16 2020 -0700";
        assert_eq!(
            &hash_from_patch(test_file_contents.as_bytes()).unwrap(),
            desired_hash
        );

        // Example git patch from upstream
        let desired_hash = "6f85225ef3791357f9b1aa097b575b0a2b0dff48";
        let test_file_contents = "From 6f85225ef3791357f9b1aa097b575b0a2b0dff48\n\
            Mon Sep 17 00:00:00 2001\n\
            From: Another Author <another_email>\n\
            Date: Wed, 18 Aug 2021 15:03:03 -0700";
        assert_eq!(
            &hash_from_patch(test_file_contents.as_bytes()).unwrap(),
            desired_hash
        );
    }

    #[test]
    fn test_union() {
        let patch1 = PatchDictSchema {
            start_version: Some(0),
            end_version: Some(1),
            rel_patch_path: "a".into(),
            metadata: None,
            platforms: BTreeSet::from(["x".into()]),
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(1),
            }),
        };
        let patch2 = PatchDictSchema {
            rel_patch_path: "b".into(),
            platforms: BTreeSet::from(["x".into(), "y".into()]),
            ..patch1.clone()
        };
        let patch3 = PatchDictSchema {
            platforms: BTreeSet::from(["z".into(), "x".into()]),
            ..patch1.clone()
        };
        let collection1 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1, patch2],
        };
        let collection2 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch3],
        };
        let union = collection1
            .union_helper(
                &collection2,
                |p| Ok(p.rel_patch_path.to_string()),
                |p| Ok(p.rel_patch_path.to_string()),
            )
            .expect("could not create union");
        assert_eq!(union.patches.len(), 2);
        assert_eq!(
            union.patches[0].platforms.iter().collect::<Vec<&String>>(),
            vec!["x", "z"]
        );
        assert_eq!(
            union.patches[1].platforms.iter().collect::<Vec<&String>>(),
            vec!["x", "y"]
        );
    }

    #[test]
    fn test_union_empties() {
        let patch1 = PatchDictSchema {
            start_version: Some(0),
            end_version: Some(1),
            rel_patch_path: "a".into(),
            metadata: None,
            platforms: Default::default(),
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(1),
            }),
        };
        let collection1 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1.clone()],
        };
        let collection2 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1],
        };
        let union = collection1
            .union_helper(
                &collection2,
                |p| Ok(p.rel_patch_path.to_string()),
                |p| Ok(p.rel_patch_path.to_string()),
            )
            .expect("could not create union");
        assert_eq!(union.patches.len(), 1);
        assert_eq!(union.patches[0].platforms.len(), 0);
    }
}
