#!/usr/bin/python3

"""Updates prebuilt libraries used by Android builds.

For details on how to use this script, visit go/update-prebuilts.
"""
import os
import sys
import zipfile
import re
import argparse
import subprocess
import six
import shlex

from urllib import request
from shutil import which
from distutils.version import LooseVersion
from pathlib import Path
from maven import MavenLibraryInfo, GMavenArtifact, maven_path_for_artifact
from buildserver import fetch_and_extract, fetch_artifacts, fetch_artifact, extract_artifact, \
    parse_build_id
from utils import print_e, append, cp, mv, rm


current_path = 'current'
framework_sdk_target = 'sdk'
androidx_dir = os.path.join(current_path, 'androidx')
androidx_owners = os.path.join(androidx_dir, 'OWNERS')
java_plugins_bp_path = os.path.join(androidx_dir, 'JavaPlugins.bp')
test_mapping_file = os.path.join(androidx_dir, 'TEST_MAPPING')
compose_test_mapping_file = os.path.join(androidx_dir, 'm2repository/androidx/compose/TEST_MAPPING')
gmaven_dir = os.path.join(current_path, 'gmaven')
extras_dir = os.path.join(current_path, 'extras')
buildtools_dir = 'tools'
jetifier_dir = os.path.join(buildtools_dir, 'jetifier', 'jetifier-standalone')
repo_root_dir = Path(sys.argv[0]).resolve().parents[3]
extension_sdk_finalization_cmd = '%s -r "{readme}" -b {bug} -f {extension_version} {build_id}' % (
    "packages/modules/common/tools/finalize_sdk.py"
)
temp_dir = os.path.join(os.getcwd(), 'support_tmp')
os.chdir(os.path.dirname(os.path.dirname(os.path.realpath(sys.argv[0]))))
git_dir = os.getcwd()

# Leave map blank to automatically populate name and path:
# - Name format is MAVEN.replaceAll(':','_')
# - Path format is MAVEN.replaceAll(':','/').replaceAll('.','/')
maven_to_make = {
    # AndroidX
    'androidx.benchmark:benchmark-macro': {},
    'androidx.benchmark:benchmark-macro-junit4': {},
    'androidx.benchmark:benchmark-common': {},
    'androidx.benchmark:benchmark-junit4': {},
    'androidx.tracing:tracing': {},
    'androidx.tracing:tracing-perfetto': {},
    'androidx.tracing:tracing-perfetto-binary': {},
    'androidx.tracing:tracing-perfetto-common': {},
    'androidx.tracing:tracing-ktx': {},
    'androidx.slice:slice-builders': {},
    'androidx.slice:slice-core': {},
    'androidx.slice:slice-view': {},
    'androidx.remotecallback:remotecallback': {},
    'androidx.remotecallback:remotecallback-processor': {
        'host': True
    },
    'androidx.versionedparcelable:versionedparcelable': {},
    'androidx.vectordrawable:vectordrawable-animated': {},
    'androidx.activity:activity': {},
    'androidx.activity:activity-ktx': {},
    'androidx.annotation:annotation': {
        'host_and_device': True,
        'extra-static-libs': {
            'androidx.annotation_annotation-jvm'
        }
    },
    'androidx.annotation:annotation-jvm': {
        'host_and_device': True
    },
    'androidx.annotation:annotation-experimental': {},
    'androidx.asynclayoutinflater:asynclayoutinflater': {},
    'androidx.collection:collection': {
        'extra-static-libs': {
            'androidx.collection_collection-jvm'
        }
    },
    'androidx.collection:collection-ktx': {},
    'androidx.collection:collection-jvm': {},
    'androidx.concurrent:concurrent-futures': {},
    'androidx.concurrent:concurrent-listenablefuture-callback': {},
    'androidx.concurrent:concurrent-listenablefuture': {},
    'androidx.core:core': {},
    'androidx.core:core-animation': {},
    'androidx.core:core-animation-testing': {},
    'androidx.core:core-ktx': {},
    'androidx.core.uwb:uwb': {},
    'androidx.core.uwb:uwb-rxjava3': {},
    'androidx.contentpaging:contentpaging': {},
    'androidx.coordinatorlayout:coordinatorlayout': {},
    'androidx.legacy:legacy-support-core-ui': {},
    'androidx.legacy:legacy-support-core-utils': {},
    'androidx.cursoradapter:cursoradapter': {},
    'androidx.browser:browser': {},
    'androidx.customview:customview': {},
    'androidx.customview:customview-poolingcontainer': {},
    'androidx.documentfile:documentfile': {},
    'androidx.drawerlayout:drawerlayout': {},
    'androidx.dynamicanimation:dynamicanimation': {},
    'androidx.emoji:emoji': {},
    'androidx.emoji:emoji-appcompat': {},
    'androidx.emoji:emoji-bundled': {},
    'androidx.emoji2:emoji2': {},
    'androidx.emoji2:emoji2-views-helper': {},
    'androidx.exifinterface:exifinterface': {},
    'androidx.fragment:fragment': {},
    'androidx.fragment:fragment-ktx': {},
    'androidx.heifwriter:heifwriter': {},
    'androidx.interpolator:interpolator': {},
    'androidx.loader:loader': {},
    'androidx.media:media': {},
    'androidx.media2:media2-player': {},
    'androidx.media2:media2-session': {},
    'androidx.media2:media2-common': {},
    'androidx.media2:media2-exoplayer': {},
    'androidx.media2:media2-widget': {},
    'androidx.navigation:navigation-common': {},
    'androidx.navigation:navigation-common-ktx': {},
    'androidx.navigation:navigation-fragment': {},
    'androidx.navigation:navigation-fragment-ktx': {},
    'androidx.navigation:navigation-runtime': {},
    'androidx.navigation:navigation-runtime-ktx': {},
    'androidx.navigation:navigation-ui': {},
    'androidx.navigation:navigation-ui-ktx': {},
    'androidx.percentlayout:percentlayout': {},
    'androidx.print:print': {},
    'androidx.recommendation:recommendation': {},
    'androidx.recyclerview:recyclerview-selection': {},
    'androidx.savedstate:savedstate': {},
    'androidx.savedstate:savedstate-ktx': {},
    'androidx.slidingpanelayout:slidingpanelayout': {},
    'androidx.swiperefreshlayout:swiperefreshlayout': {},
    'androidx.textclassifier:textclassifier': {},
    'androidx.transition:transition': {},
    'androidx.tvprovider:tvprovider': {},
    'androidx.legacy:legacy-support-v13': {},
    'androidx.legacy:legacy-preference-v14': {},
    'androidx.leanback:leanback': {},
    'androidx.leanback:leanback-grid': {},
    'androidx.leanback:leanback-preference': {},
    'androidx.legacy:legacy-support-v4': {},
    'androidx.appcompat:appcompat': {},
    'androidx.appcompat:appcompat-resources': {},
    'androidx.cardview:cardview': {},
    'androidx.gridlayout:gridlayout': {},
    'androidx.mediarouter:mediarouter': {},
    'androidx.palette:palette': {},
    'androidx.preference:preference': {},
    'androidx.recyclerview:recyclerview': {},
    'androidx.vectordrawable:vectordrawable': {},
    'androidx.viewpager:viewpager': {},
    'androidx.viewpager2:viewpager2': {},
    'androidx.wear:wear': {},
    'androidx.wear:wear-ongoing': {},
    'androidx.javascriptengine:javascriptengine': {},
    'androidx.webkit:webkit': {},
    'androidx.biometric:biometric': {},
    'androidx.autofill:autofill': {},
    'androidx.appsearch:appsearch': {},
    'androidx.appsearch:appsearch-builtin-types': {},
    'androidx.appsearch:appsearch-compiler': {
        'name': 'androidx.appsearch_appsearch-compiler',
        'host': True
    },
    'androidx.appsearch:appsearch-local-storage': {
        'name': 'androidx.appsearch_appsearch_local_storage'
    },
    'androidx.appsearch:appsearch-platform-storage': {},
    'androidx.car.app:app': {},
    'androidx.car.app:app-automotive': {},
    'androidx.car.app:app-testing': {},
    'androidx.startup:startup-runtime': {},
    'androidx.window:window': {
        'optional-uses-libs': {
            'androidx.window.extensions',
            'androidx.window.sidecar'
        }
    },
    'androidx.window.extensions:extensions': {},
    'androidx.window.extensions.core:core': {},
    'androidx.window:window-core': {},
    'androidx.resourceinspection:resourceinspection-annotation': {},
    'androidx.profileinstaller:profileinstaller': {},
    'androidx.test.uiautomator:uiautomator': {},

    # AndroidX for Compose
    'androidx.compose.compiler:compiler-hosted': {
        'host': True
    },
    'androidx.compose.runtime:runtime': {},
    'androidx.compose.runtime:runtime-saveable': {},
    'androidx.compose.runtime:runtime-livedata': {},
    'androidx.compose.runtime:runtime-tracing': {},
    'androidx.compose.foundation:foundation': {},
    'androidx.compose.foundation:foundation-layout': {},
    'androidx.compose.foundation:foundation-text': {},
    'androidx.compose.ui:ui': {},
    'androidx.compose.ui:ui-geometry': {},
    'androidx.compose.ui:ui-graphics': {},
    'androidx.compose.ui:ui-text': {},
    'androidx.compose.ui:ui-tooling': {},
    'androidx.compose.ui:ui-tooling-preview': {},
    'androidx.compose.ui:ui-tooling-data': {},
    'androidx.compose.ui:ui-unit': {},
    'androidx.compose.ui:ui-util': {},
    'androidx.compose.ui:ui-test': { },
    'androidx.compose.ui:ui-test-junit4': { },
    'androidx.compose.ui:ui-test-manifest': { },
    'androidx.compose.animation:animation-core': {},
    'androidx.compose.animation:animation': {},
    'androidx.compose.material:material-icons-core': {},
    'androidx.compose.material:material-icons-extended': { },
    'androidx.compose.material:material-ripple': {},
    'androidx.compose.material:material': {},
    'androidx.compose.material3:material3': {},
    'androidx.activity:activity-compose': {},
    'androidx.navigation:navigation-compose': { },
    'androidx.lifecycle:lifecycle-viewmodel-compose': { },

    # AndroidX for Multidex
    'androidx.multidex:multidex': {},
    'androidx.multidex:multidex-instrumentation': {},

    # AndroidX for Constraint Layout
    'androidx.constraintlayout:constraintlayout': {
        'name': 'androidx-constraintlayout_constraintlayout'
    },
    'androidx.constraintlayout:constraintlayout-solver': {
        'name': 'androidx-constraintlayout_constraintlayout-solver'
    },
    'androidx.constraintlayout:constraintlayout-core': {},
    'androidx.constraintlayout:constraintlayout-compose': {},
    # AndroidX for Architecture Components
    'androidx.arch.core:core-common': {},
    'androidx.arch.core:core-runtime': {},
    'androidx.lifecycle:lifecycle-common': {},
    'androidx.lifecycle:lifecycle-common-java8': {},
    'androidx.lifecycle:lifecycle-extensions': {},
    'androidx.lifecycle:lifecycle-livedata': {},
    'androidx.lifecycle:lifecycle-livedata-ktx': {},
    'androidx.lifecycle:lifecycle-livedata-core': {},
    'androidx.lifecycle:lifecycle-livedata-core-ktx': {},
    'androidx.lifecycle:lifecycle-process': {},
    'androidx.lifecycle:lifecycle-runtime': {},
    'androidx.lifecycle:lifecycle-runtime-ktx': {},
    'androidx.lifecycle:lifecycle-service': {},
    'androidx.lifecycle:lifecycle-viewmodel': {},
    'androidx.lifecycle:lifecycle-viewmodel-ktx': {},
    'androidx.lifecycle:lifecycle-viewmodel-savedstate': {},
    'androidx.paging:paging-common': {},
    'androidx.paging:paging-common-ktx': {},
    'androidx.paging:paging-runtime': {},
    'androidx.sqlite:sqlite': {},
    'androidx.sqlite:sqlite-framework': {},
    'androidx.room:room-common': {
        'host_and_device': True
    },
    'androidx.room:room-compiler': {
        'host': True,
        'extra-static-libs': {
            'guava'
        }
    },
    'androidx.room:room-migration': {
        'host_and_device': True
    },
    'androidx.room:room-runtime': {},
    'androidx.room:room-testing': {},
    'androidx.room:room-compiler-processing': {
        'host': True
    },
    'androidx.work:work-runtime': {},
    'androidx.work:work-runtime-ktx': {},
    'androidx.work:work-testing': {},

    # Third-party dependencies
    'com.google.android:flexbox': {
        'name': 'flexbox',
        'path': 'flexbox'
    },

    # Androidx Material Design Components
    'com.google.android.material:material': {},
}

# Mapping of POM dependencies to Soong build targets
deps_rewrite = {
    'auto-common': 'auto_common',
    'auto-value-annotations': 'auto_value_annotations',
    'com.google.auto.value:auto-value': 'libauto_value_plugin',
    'monitor': 'androidx.test.monitor',
    'rules': 'androidx.test.rules',
    'runner': 'androidx.test.runner',
    'androidx.test:core': 'androidx.test.core',
    'com.squareup:javapoet': 'javapoet',
    'com.google.guava:listenablefuture': 'guava-listenablefuture-prebuilt-jar',
    'sqlite-jdbc': 'xerial-sqlite-jdbc',
    'com.intellij:annotations': 'jetbrains-annotations',
    'javax.annotation:javax.annotation-api': 'javax-annotation-api-prebuilt-host-jar',
    'org.robolectric:robolectric': 'Robolectric_all-target',
    'org.jetbrains.kotlin:kotlin-stdlib-common': 'kotlin-stdlib',
    'org.jetbrains.kotlinx:kotlinx-coroutines-core': 'kotlinx_coroutines',
    'org.jetbrains.kotlinx:kotlinx-coroutines-android': 'kotlinx_coroutines_android',
    'org.jetbrains.kotlinx:kotlinx-coroutines-test':'kotlinx_coroutines_test',
    'org.jetbrains.kotlinx:kotlinx-metadata-jvm': 'kotlinx_metadata_jvm',
    'androidx.test.espresso:espresso-core':'androidx.test.espresso.core',
    'androidx.test.espresso:espresso-idling-resource':'androidx.test.espresso.idling-resource',
}

# List of artifacts that will be updated from GMaven
# Use pattern: `group:library:version:extension`
# e.g.:
#   androidx.appcompat:appcompat:1.2.0:aar
# Use `latest` to always fetch the latest version.
# e.g.:
#   androidx.appcompat:appcompat:latest:aar
# Also make sure you add `group:library`:{} to maven_to_make as well.
gmaven_artifacts = {}

# Always remove these files.
denylist_files = [
    'annotations.zip',
    'public.txt',
    'R.txt',
    'AndroidManifest.xml',
    os.path.join('libs', 'noto-emoji-compat-java.jar')
]

artifact_pattern = re.compile(r'^(.+?)-(\d+\.\d+\.\d+(?:-\w+\d+)?(?:-[\d.]+)*)\.(jar|aar)$')


def name_for_artifact(group_artifact):
    """Returns the build system target name for a given library's Maven coordinate.

    Args:
        group_artifact: an unversioned Maven artifact coordinate, ex. androidx.core:core
    Returns:
        The build system target name for the artifact, ex. androidx.core_core.
    """
    return group_artifact.replace(':', '_')


def path_for_artifact(group_artifact):
    """Returns the file system path for a given library's Maven coordinate.

    Args:
        group_artifact: an unversioned Maven artifact coordinate, ex. androidx.core:core
    Returns:
        The file system path for the artifact, ex. androidx/core/core.
    """
    return group_artifact.replace('.', '/').replace(':', '/')


def populate_maven_to_make(mapping):
    """Modifies the input mapping by expanding Maven coordinate keys into build target names and
    paths.

    Args:
        mapping: a map where the keys are Maven coordinates
    """
    for key in mapping:
        if 'name' not in mapping[key]:
            mapping[key]['name'] = name_for_artifact(key)
        if 'path' not in maven_to_make[key]:
            mapping[key]['path'] = path_for_artifact(key)


def detect_artifacts(maven_repo_dirs):
    """Parses Maven libraries from the specified directories.

    Args:
        maven_repo_dirs: a list of maven repository roots
    Returns:
        A map of Maven coordinate keys to MavenLibraryInfo objects parsed from POM files.
    """
    maven_lib_info = {}

    # Find the latest revision for each artifact, remove others
    for repo_dir in maven_repo_dirs:
        for root, dirs, files in os.walk(repo_dir):
            for file in files:
                if file[-4:] == '.pom':
                    # Read the POM (hack hack hack).
                    group_id = ''
                    artifact_id = ''
                    version = ''
                    file = os.path.join(root, file)
                    with open(file) as pom_file:
                        for line in pom_file:
                            if line[:11] == '  <groupId>':
                                group_id = line[11:-11]
                            elif line[:14] == '  <artifactId>':
                                artifact_id = line[14:-14]
                            elif line[:11] == '  <version>':
                                version = line[11:-11]
                    if group_id == '' or artifact_id == '' or version == '':
                        print_e('Failed to find Maven artifact data in ' + file)
                        continue

                    # Locate the artifact.
                    artifact_file = file[:-4]
                    if os.path.exists(artifact_file + '.jar'):
                        artifact_file = artifact_file + '.jar'
                    elif os.path.exists(artifact_file + '.aar'):
                        artifact_file = artifact_file + '.aar'
                    else:
                        # This error only occurs for a handful of gradle.plugin artifacts that only
                        # ship POM files, so we probably don't need to log unless we're debugging.
                        # print_e('Failed to find artifact for ' + artifact_file)
                        continue

                    # Make relative to root.
                    artifact_file = artifact_file[len(root) + 1:]

                    # Find the mapping.
                    group_artifact = group_id + ':' + artifact_id
                    if group_artifact in maven_to_make:
                        key = group_artifact
                    elif artifact_id in maven_to_make:
                        key = artifact_id
                    else:
                        # No mapping entry, skip this library.
                        continue

                    # Store the latest version.
                    version = LooseVersion(version)
                    if key not in maven_lib_info \
                            or version > maven_lib_info[key].version:
                        maven_lib_info[key] = MavenLibraryInfo(key, group_id, artifact_id, version,
                                                               root, repo_dir, artifact_file)

    return maven_lib_info


def transform_maven_repos(maven_repo_dirs, transformed_dir, extract_res=True,
                          include_static_deps=True, include=None, exclude=None, prepend=None):
    """Transforms a standard Maven repository to be compatible with the Android build system.

    When using the include argument by itself, all other libraries will be excluded. When using the
    exclude argument by itself, all other libraries will be included. When using both arguments, the
    inclusion list will be applied followed by the exclusion list.

    Args:
        maven_repo_dirs: path to local Maven repository
        transformed_dir: relative path for output, ex. androidx
        extract_res: whether to extract Android resources like AndroidManifest.xml from AARs
        include_static_deps: whether to pass --static-deps to pom2bp
        include: list of Maven groupIds or unversioned artifact coordinates to include for
                 updates, ex. androidx.core or androidx.core:core
        exclude: list of Maven groupIds or unversioned artifact coordinates to exclude from
                 updates, ex. androidx.core or androidx.core:core
        prepend: Path to a file containing text to be inserted at the beginning of the generated
                 build file
    Returns:
        True if successful, false otherwise.
    """
    if exclude is None:
        exclude = []
    if include is None:
        include = []

    cwd = os.getcwd()
    local_repo = os.path.join(cwd, transformed_dir)
    working_dir = temp_dir

    # Handle inclusions by stashing the remote artifacts for the inclusions, replacing the entire
    # remote repo with the local repo, then restoring the stashed artifacts.
    for remote_repo in maven_repo_dirs:
        remote_repo = os.path.join(cwd, remote_repo)
        paths_to_copy = []
        for group_artifact in include:
            artifact_path = os.path.join('m2repository', path_for_artifact(group_artifact))
            remote_path = os.path.join(remote_repo, artifact_path)
            working_path = os.path.join(working_dir, artifact_path)
            if os.path.exists(remote_path):
                print(f'Included {group_artifact} in update')
                paths_to_copy.append([remote_path, working_path])

        # Move included artifacts from repo to temp.
        for [remote_path, working_path] in paths_to_copy:
            mv(remote_path, working_path)

        # Replace all remaining artifacts in remote repo with local repo.
        cp(local_repo, remote_repo)

        # Restore included artifacts to remote repo.
        for [remote_path, working_path] in paths_to_copy:
            mv(working_path, remote_path)

    # Handle exclusions by replacing the remote artifacts for the exclusions with local artifacts.
    # This must happen before we parse the artifacts.
    for remote_repo in maven_repo_dirs:
        for group_artifact in exclude:
            artifact_path = os.path.join('m2repository', path_for_artifact(group_artifact))
            remote_path = os.path.join(remote_repo, artifact_path)
            if os.path.exists(remote_path):
                rm(remote_path)
                local_path = os.path.join(local_repo, artifact_path)
                if os.path.exists(local_path):
                    print(f'Excluded {group_artifact} from update, used local artifact')
                    mv(local_path, remote_path)
                else:
                    print(f'Excluded {group_artifact} from update, no local artifact present')

    # Parse artifacts.
    maven_lib_info = detect_artifacts(maven_repo_dirs)

    if not maven_lib_info:
        print_e('Failed to detect artifacts')
        return False

    # Move libraries into the working directory, performing any necessary transformations.
    for info in maven_lib_info.values():
        transform_maven_lib(working_dir, info, extract_res)

    # Generate a single Android.bp that specifies to use all of the above artifacts.
    makefile = os.path.join(working_dir, 'Android.bp')
    with open(makefile, 'w') as f:
        args = ['pom2bp']
        args.extend(['-sdk-version', '31'])
        args.extend(['-default-min-sdk-version', '24'])
        if include_static_deps:
            args.append('-static-deps')
        if prepend:
            args.append(f'-prepend={prepend}')
        rewrite_names = sorted(maven_to_make.keys())
        args.extend([f'-rewrite=^{name}$={maven_to_make[name]["name"]}' for name in rewrite_names])
        args.extend([f'-rewrite=^{key}$={value}' for key, value in deps_rewrite.items()])
        args.extend(["-extra-static-libs=" + maven_to_make[name]['name'] + "=" + ",".join(
            sorted(maven_to_make[name]['extra-static-libs'])) for name in maven_to_make if
                     'extra-static-libs' in maven_to_make[name]])
        args.extend(["-optional-uses-libs=" + maven_to_make[name]['name'] + "=" + ",".join(
            sorted(maven_to_make[name]['optional-uses-libs'])) for name in maven_to_make if
                     'optional-uses-libs' in maven_to_make[name]])
        args.extend([f'-host={name}' for name in maven_to_make
                     if maven_to_make[name].get('host')])
        args.extend([f'-host-and-device={name}' for name in maven_to_make
                     if maven_to_make[name].get('host_and_device')])
        args.extend(['.'])
        subprocess.check_call(args, stdout=f, cwd=working_dir)

    # Replace the old directory.
    local_repo = os.path.join(cwd, transformed_dir)
    mv(working_dir, local_repo)
    return True


def transform_maven_lib(working_dir, artifact_info, extract_res):
    """Transforms the specified artifact for use in the Android build system.

    Moves relevant files for the artifact represented by artifact_info of type MavenLibraryInfo into
    the appropriate path inside working_dir, unpacking files needed by the build system from AARs.

    Args:
        working_dir: The directory into which the artifact should be moved
        artifact_info: A MavenLibraryInfo representing the library artifact
        extract_res: True to extract resources from AARs, false otherwise.
    """
    # Move library into working dir
    new_dir = os.path.normpath(
        os.path.join(working_dir, os.path.relpath(artifact_info.dir, artifact_info.repo_dir)))
    mv(artifact_info.dir, new_dir)

    maven_lib_type = os.path.splitext(artifact_info.file)[1][1:]

    group_artifact = artifact_info.key
    make_lib_name = maven_to_make[group_artifact]['name']
    make_dir_name = maven_to_make[group_artifact]['path']

    artifact_file = os.path.join(new_dir, artifact_info.file)

    if maven_lib_type == 'aar':
        if extract_res:
            target_dir = os.path.join(working_dir, make_dir_name)
            if not os.path.exists(target_dir):
                os.makedirs(target_dir)

            process_aar(artifact_file, target_dir)

        with zipfile.ZipFile(artifact_file) as zip_file:
            manifests_dir = os.path.join(working_dir, 'manifests')
            zip_file.extract('AndroidManifest.xml', os.path.join(manifests_dir, make_lib_name))


def process_aar(artifact_file, target_dir):
    """Extracts and cleans up the contents of an AAR file to the specified directory.

    Removes classes.jar, empty directories, and denylisted files.

    Args:
        artifact_file: path to the AAR to extract
        target_dir: directory into which the contents should be extracted
    """
    # Extract AAR file to target_dir.
    with zipfile.ZipFile(artifact_file) as zip_file:
        zip_file.extractall(target_dir)

    # Remove classes.jar
    classes_jar = os.path.join(target_dir, 'classes.jar')
    if os.path.exists(classes_jar):
        os.remove(classes_jar)

    # Remove empty dirs.
    for root, dirs, files in os.walk(target_dir, topdown=False):
        for dir_name in dirs:
            dir_path = os.path.join(root, dir_name)
            if not os.listdir(dir_path):
                os.rmdir(dir_path)

    # Remove top-level cruft.
    for file in denylist_files:
        file_path = os.path.join(target_dir, file)
        if os.path.exists(file_path):
            os.remove(file_path)


def fetch_gmaven_artifact(artifact):
    """Fetch a GMaven artifact.

    Downloads a GMaven artifact
    (https://developer.android.com/studio/build/dependencies#gmaven-access)

    Args:
        artifact: an instance of GMavenArtifact.
    """
    pom_path = maven_path_for_artifact(
        'gmaven', artifact.group, artifact.library, artifact.version, 'pom')
    artifact_path = maven_path_for_artifact(
        'gmaven', artifact.group, artifact.library, artifact.version, artifact.ext)

    download_file_to_disk(artifact.get_pom_file_url(), pom_path)
    download_file_to_disk(artifact.get_artifact_url(), artifact_path)

    return os.path.dirname(artifact_path)


def download_file_to_disk(url, filepath):
    """Download the file at URL to the location dictated by the path.

    Args:
        url: Remote URL to download file from.
        filepath: Filesystem path to write the file to.
    """
    print(f'Downloading URL: {url}')
    file_data = request.urlopen(url)

    try:
        os.makedirs(os.path.dirname(filepath))
    except os.error:
        # This is a common situation - os.makedirs fails if dir already exists.
        pass
    try:
        with open(filepath, 'wb') as f:
            f.write(six.ensure_binary(file_data.read()))
    except Exception as e:
        print_e(e.__class__, 'occurred while reading', filepath)
        os.remove(os.path.dirname(filepath))
        raise


def update_gmaven(gmaven_artifacts_list):
    artifacts = [GMavenArtifact(artifact) for artifact in gmaven_artifacts_list]
    for artifact in artifacts:
        if artifact.version == 'latest':
            artifact.version = artifact.get_latest_version()

    if not transform_maven_repos(['gmaven'], gmaven_dir, extract_res=False):
        return []
    return [artifact.key for artifact in artifacts]


def update_androidx(target, build_id, local_file, include, exclude, beyond_corp):
    """Fetches and extracts Jetpack library prebuilts.

    Args:
        target: Android build server target name, must be specified if local_file is empty
        build_id: Optional Android build server ID, must be specified if local_file is empty
        local_file: Optional local top-of-tree ZIP, must be specified if build_id is empty
        include: List of Maven groupIds or unversioned artifact coordinates to include for
                 updates, ex. android.core or androidx.core:core
        exclude: List of Maven groupIds or unversioned artifact coordinates to exclude from
                 updates, ex. android.core or androidx.core:core
        beyond_corp: Whether to use BeyondCorp-compatible artifact fetcher
    Returns:
        True if successful, false otherwise.
    """
    if build_id:
        repo_file = 'top-of-tree-m2repository-all-%s.zip' % build_id.fs_id
        repo_dir = fetch_and_extract(target, build_id.url_id, repo_file, beyond_corp, None)
    else:
        repo_dir = fetch_and_extract(target, None, None, beyond_corp, local_file)
    if not repo_dir:
        print_e('Failed to extract AndroidX repository')
        return False

    prepend_path = os.path.relpath('update_prebuilts/prepend_androidx_license', start=temp_dir)

    # Transform the repo archive into a Makefile-compatible format.
    if not transform_maven_repos([repo_dir], androidx_dir, extract_res=False, include=include,
                                 exclude=exclude, prepend=prepend_path):
        return False

    # Import JavaPlugins.bp in Android.bp.
    makefile = os.path.join(androidx_dir, 'Android.bp')
    with open(makefile, 'a+') as f:
        f.write('\nbuild = ["JavaPlugins.bp"]\n')

    # Keep OWNERs file, JavaPlugins.bp file, and TEST_MAPPING files untouched.
    files_to_restore = [androidx_owners, java_plugins_bp_path, test_mapping_file,
                        compose_test_mapping_file]
    for file_to_restore in files_to_restore:
        # Ignore any output or error - these files are not gauranteed to exist, but
        # if they do, we want to restore them.
        subprocess.call(['git', 'restore', file_to_restore],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    return True


def update_jetifier(target, build_id, beyond_corp):
    """
    Fetches and extracts Jetifier tool prebuilts.

    Args:
        target: Android build server target name
        build_id: Android build server ID
        beyond_corp: Whether to use BeyondCorp-compatible artifact fetcher
    Return:
        Whether the prebuilt was successfully updated.
    """
    repo_file = 'jetifier-standalone.zip'
    repo_dir = fetch_and_extract(target, build_id.url_id, repo_file, beyond_corp)
    if not repo_dir:
        print_e('Failed to extract Jetifier')
        return False

    rm(jetifier_dir)
    mv(os.path.join(repo_dir, 'jetifier-standalone'), jetifier_dir)
    os.chmod(os.path.join(jetifier_dir, 'bin', 'jetifier-standalone'), 0o755)
    return True


def update_constraint(local_file):
    """
    Extracts ConstraintLayout library prebuilts.

    Args:
        local_file: local Maven repository ZIP containing library artifacts
    Return:
        Whether the prebuilts were successfully updated.
    """
    repo_dir = extract_artifact(local_file)
    if not repo_dir:
        print_e('Failed to extract Constraint Layout')
        return False
    return transform_maven_repos([repo_dir], os.path.join(extras_dir, 'constraint-layout-x'),
                                 extract_res=False)


def update_material(local_file):
    """
    Extracts Material Design Components library prebuilts.

    Args:
        local_file: local Maven repository ZIP containing library artifacts
    Return:
        Whether the prebuilts were successfully updated.
    """
    design_dir = extract_artifact(local_file)
    if not design_dir:
        print_e('Failed to extract Material Design Components')
        return False
    return transform_maven_repos([design_dir], os.path.join(extras_dir, 'material-design-x'),
                                 extract_res=False)


def update_framework(target, build_id, sdk_dir, beyond_corp):
    api_scope_list = ['public', 'system', 'test', 'module-lib', 'system-server']
    if sdk_dir == 'current':
        api_scope_list.append('core')

    for api_scope in api_scope_list:
        target_dir = os.path.join(sdk_dir, api_scope)
        if api_scope == 'core':
            artifact_to_path = {'core.current.stubs.jar': os.path.join(target_dir, 'android.jar')}
        else:
            artifact_to_path = {
                'apistubs/android/' + api_scope + '/*.jar': os.path.join(target_dir, '*'),
            }
            if api_scope == 'public' or api_scope == 'module-lib':
                # Distinct core-for-system-modules.jar files are only provided
                # for the public and module-lib API surfaces.
                artifact_to_path[
                    'system-modules/' + api_scope + '/core-for-system-modules.jar'] = os.path.join(
                    target_dir, '*')

        if not fetch_artifacts(target, build_id, artifact_to_path, beyond_corp):
            return False

        if api_scope == 'public':
            # Fetch a few artifacts from the public sdk.
            artifact = 'sdk-repo-linux-platforms-%s.zip' % build_id.fs_id
            artifact_path = fetch_artifact(target, build_id.url_id, artifact, beyond_corp)
            if not artifact_path:
                return False

            with zipfile.ZipFile(artifact_path) as zipFile:
                extra_files = [
                    'android.jar',
                    'framework.aidl',
                    'uiautomator.jar',
                    'data/annotations.zip',
                    'data/api-versions.xml']
                for filename in extra_files:
                    matches = list(filter(lambda path: filename in path, zipFile.namelist()))
                    if len(matches) != 1:
                        print_e('Expected 1 file named \'%s\' in zip %s, found %d' %
                                (filename, zipFile.filename, len(matches)))
                        return False
                    zip_path = matches[0]
                    src_path = zipFile.extract(zip_path)
                    dst_path = os.path.join(target_dir, filename)
                    mv(src_path, dst_path)

            # Filtered API DB is currently only available for "public"
            fetch_artifacts(target, build_id, {'api-versions-public-filtered.xml': os.path.join(
                target_dir, 'data/api-versions-filtered.xml')}, beyond_corp)

    return True


def update_makefile(build_id):
    template = '"%s",\n\
        "current"'
    makefile = os.path.join(git_dir, 'Android.bp')

    with open(makefile, 'r+') as f:
        contents = f.read().replace('"current"', template % build_id)
        f.seek(0)
        f.write(contents)

    return True


def finalize_sdk(target, build_id, sdk_version, beyond_corp):
    target_finalize_dir = '%d' % sdk_version

    for api_scope in ['public', 'system', 'test', 'module-lib', 'system-server']:
        artifact_to_path = {f'apistubs/android/{api_scope}/api/*.txt': os.path.join(
            target_finalize_dir, api_scope, 'api', '*')}

        if not fetch_artifacts(target, build_id, artifact_to_path, beyond_corp):
            return False

    return update_framework(target, build_id, target_finalize_dir, beyond_corp) and update_makefile(
        target_finalize_dir)


def update_framework_current(target, build_id, beyond_corp):
    return update_framework(target, build_id, current_path, beyond_corp)


def update_buildtools(target, arch, build_id, beyond_corp):
    artifact_path = fetch_and_extract(target, build_id.url_id,
                                      f'sdk-repo-{arch}-build-tools-{build_id.fs_id}.zip',
                                      beyond_corp)
    if not artifact_path:
        return False

    top_level_dir = os.listdir(artifact_path)[0]
    src_path = os.path.join(artifact_path, top_level_dir)
    dst_path = os.path.join(buildtools_dir, arch)

    # There are a few libraries that have been manually added to the
    # build tools, copy them from the destination back to the source
    # before the destination is overwritten.
    files_to_save = (
        'lib64/libconscrypt_openjdk_jni.dylib',
        'lib64/libconscrypt_openjdk_jni.so',
        'bin/lib64/libwinpthread-1.dll',
    )
    for file in files_to_save:
        src_file = os.path.join(dst_path, file)
        dst_file = os.path.join(src_path, file)
        if os.path.exists(dst_path):
            mv(src_file, dst_file)

    mv(src_path, dst_path)

    # Move all top-level files to /bin and make them executable
    bin_path = os.path.join(dst_path, 'bin')
    top_level_files = filter(lambda e: os.path.isfile(os.path.join(dst_path, e)), os.listdir(dst_path))
    for file in top_level_files:
        src_file = os.path.join(dst_path, file)
        dst_file = os.path.join(bin_path, file)
        mv(src_file, dst_file)
        os.chmod(dst_file, 0o755)

    # Make the files under lld-bin executable
    lld_bin_files = os.listdir(os.path.join(dst_path, 'lld-bin'))
    for file in lld_bin_files:
        os.chmod(os.path.join(dst_path, 'lld-bin', file), 0o755)

    # Remove renderscript
    rm(os.path.join(dst_path, 'renderscript'))

    return True


def has_uncommitted_changes():
    try:
        # Make sure we don't overwrite any pending changes.
        diff_command = f'cd {git_dir} && git diff --quiet'
        subprocess.check_call(diff_command, shell=True)
        subprocess.check_call(f'{diff_command} --cached', shell=True)
        return False
    except subprocess.CalledProcessError:
        return True


def main():
    parser = argparse.ArgumentParser(
        description='Update current prebuilts')
    parser.add_argument(
        'source', nargs='?',
        help='Build server build ID or local Maven ZIP file')
    parser.add_argument(
        '-m', '--material', action='store_true',
        help='If specified, updates only Material Design Components')
    parser.add_argument(
        '-c', '--constraint', action='store_true',
        help='If specified, updates only Constraint Layout')
    parser.add_argument(
        '-j', '--jetifier', action='store_true',
        help='If specified, updates only Jetifier')
    parser.add_argument(
        '-p', '--platform', action='store_true',
        help='If specified, updates only the Android Platform')
    parser.add_argument(
        '-f', '--finalize_sdk', type=int,
        help='Finalize the build as the specified SDK version. Must be used together with -e')
    parser.add_argument(
        '-e', '--finalize_extension', type=int,
        help='Finalize the build as the specified extension SDK version. Must be used together with -f')
    parser.add_argument('--bug', type=int, help='The bug number to add to the commit message.')
    parser.add_argument(
        '--sdk_target',
        default=framework_sdk_target,
        help='If specified, the name of the build target from which to retrieve the SDK when -p or -f '
             'is specified.')
    parser.add_argument(
        '-b', '--buildtools', action='store_true',
        help='If specified, updates only the Build Tools')
    parser.add_argument(
        '-x', '--androidx', action='store_true',
        help='If specified, updates only the Jetpack (androidx) libraries excluding those covered by '
             'other arguments')
    parser.add_argument(
        '--include', action='append', default=[],
        help='If specified with -x, includes the specified Jetpack library Maven group or artifact for '
             'updates. Applied before exclude.')
    parser.add_argument(
        '--exclude', action='append', default=[],
        help='If specified with -x, excludes the specified Jetpack library Maven group or artifact '
             'from updates')
    parser.add_argument(
        '-g', '--gmaven', action='store_true',
        help='If specified, updates only the artifact from GMaven libraries excluding those covered by '
             'other arguments')
    parser.add_argument(
        '--commit-first', action='store_true',
        help='If specified, then if uncommited changes exist, commit before continuing')
    parser.add_argument(
        '--beyond-corp', action='store_true',
        help='If specified, then fetch artifacts with tooling that works on BeyondCorp devices')

    rm(temp_dir)

    args = parser.parse_args()

    # Validate combinations of arguments.
    if not args.source and (args.platform or args.buildtools or args.jetifier
                            or args.androidx or args.material or args.finalize_sdk
                            or args.constraint):
        parser.error('You must specify a build ID or local Maven ZIP file')
        sys.exit(1)
    if not (args.gmaven or args.platform or args.buildtools or args.jetifier
            or args.androidx or args.material or args.finalize_sdk
            or args.finalize_extension or args.constraint):
        parser.error('You must specify at least one target to update')
        sys.exit(1)
    if (args.finalize_sdk is None) != (args.finalize_extension is None):
        parser.error('Either both or neither of -e and -f must be specified.')
        sys.exit(1)
    if args.finalize_sdk and not args.bug:
        parser.error('Specifying a bug ID with --bug is required when finalizing an SDK.')
        sys.exit(1)

    # Validate the build environment for POM-dependent targets.
    if (args.constraint or args.material or args.androidx or args.gmaven) \
            and which('pom2bp') is None:
        parser.error('Cannot find pom2bp in path; please run lunch to set up build environment. '
                     'You may also need to run \'m pom2bp\' if it hasn\'t been built already.')
        sys.exit(1)

    # Validate the git status.
    if has_uncommitted_changes():
        if args.commit_first:
            subprocess.check_call(f'cd {git_dir} && git add -u', shell=True)
            subprocess.check_call(f'cd {git_dir} && git commit -m \'save working state\'',
                                  shell=True)
    if has_uncommitted_changes():
        self_file = os.path.basename(__file__)
        print_e(f'FAIL: There are uncommitted changes here. Please commit or stash before '
                f'continuing, because {self_file} will run "git reset --hard" if execution fails')
        sys.exit(1)

    if args.bug:
        commit_msg_suffix = '\n\nBug: {args.bug}'
    else:
        commit_msg_suffix = ''

    # Are we fetching a build ID or using a local file?
    build_id = None
    file = None
    if args.source:
        build_id = parse_build_id(args.source)
        if build_id is None:
            file = args.source

    try:
        components = []
        if args.constraint:
            if update_constraint(file):
                components.append('Constraint Layout')
            else:
                print_e('Failed to update Constraint Layout, aborting...')
                sys.exit(1)
        if args.material:
            if update_material(file):
                components.append('Material Design Components')
            else:
                print_e('Failed to update Material Design Components, aborting...')
                sys.exit(1)
        if args.gmaven:
            updated_artifacts = update_gmaven(gmaven_artifacts)
            if updated_artifacts:
                components.append('\n'.join(updated_artifacts))
            else:
                print_e('Failed to update GMaven, aborting...')
                sys.exit(1)
        if args.androidx:
            if update_androidx('androidx', build_id, file, args.include, args.exclude,
                               args.beyond_corp):
                components.append('AndroidX')
            else:
                print_e('Failed to update AndroidX, aborting...')
                sys.exit(1)
        if args.jetifier:
            if update_jetifier('androidx', build_id, args.beyond_corp):
                components.append('Jetifier')
            else:
                print_e('Failed to update Jetifier, aborting...')
                sys.exit(1)
        if args.platform or args.finalize_sdk:
            if update_framework_current(args.sdk_target, build_id, args.beyond_corp):
                components.append('platform SDK')
            else:
                print_e('Failed to update platform SDK, aborting...')
                sys.exit(1)
        if args.finalize_sdk:
            n = args.finalize_sdk
            if not finalize_sdk(args.sdk_target, build_id, n, args.beyond_corp):
                print_e('Failed to finalize SDK %d, aborting...' % n)
                sys.exit(1)

            # We commit the finalized dir separately from the current sdk update.
            msg = f'Import final sdk version {n} from build {build_id.url_id}{commit_msg_suffix}'
            subprocess.check_call(['git', 'add', '%d' % n])
            subprocess.check_call(['git', 'add', 'Android.bp'])
            subprocess.check_call(['git', 'commit', '-m', msg])

            # Finalize extension sdk level
            readme = (f'- {args.finalize_extension}: Finalized together with '
                      'Android {args.finalize_sdk} (all modules)')
            cmd = extension_sdk_finalization_cmd.format(
                readme=readme,
                bug=args.bug,
                extension_version=args.finalize_extension,
                build_id=build_id.url_id)
            subprocess.check_call(shlex.split(cmd), cwd=repo_root_dir.resolve())
        if args.buildtools:
            if update_buildtools('sdk_mac', 'darwin', build_id, args.beyond_corp) \
                    and update_buildtools('sdk', 'linux', build_id, args.beyond_corp) \
                    and update_buildtools('sdk', 'windows', build_id, args.beyond_corp):
                components.append('build tools')
            else:
                print_e('Failed to update build tools, aborting...')
                sys.exit(1)

        # Build the git commit.
        subprocess.check_call(['git', 'add', current_path, buildtools_dir])

        # Build the commit message.
        components_msg = ', '.join(components)
        argv_msg = ' '.join(sys.argv)
        if not args.source and args.gmaven:
            src_msg = 'GMaven'
        elif not args.source.isnumeric():
            src_msg = 'local Maven ZIP'
        else:
            src_msg = f'build {build_id.url_id}'
        msg = f'Import {components_msg} from {src_msg}\n\n{argv_msg}{commit_msg_suffix}'

        # Create the git commit.
        subprocess.check_call(['git', 'commit', '-q', '-m', msg])

        if args.finalize_sdk:
            print('NOTE: Created three commits:')
            subprocess.check_call(['git', 'log', '-3', '--oneline'])
        else:
            print('Created commit:')
            subprocess.check_call(['git', 'log', '-1', '--oneline'])
        print('Remember to test this change before uploading it to Gerrit!')

    finally:
        # Revert all stray files, including the downloaded zip.
        try:
            with open(os.devnull, 'w') as bitbucket:
                subprocess.check_call(['git', 'add', '-Af', '.'], stdout=bitbucket)
                subprocess.check_call(
                    ['git', 'commit', '-m', 'COMMIT TO REVERT - RESET ME!!!', '--allow-empty'],
                    stdout=bitbucket)
                subprocess.check_call(['git', 'reset', '--hard', 'HEAD~1'], stdout=bitbucket)
        except subprocess.CalledProcessError:
            print_e('ERROR: Failed cleaning up, manual cleanup required!!!')


# Add automatic entries to maven_to_make.
populate_maven_to_make(maven_to_make)

if __name__ == '__main__':
    main()
