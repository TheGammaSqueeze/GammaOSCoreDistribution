#!/usr/bin/env python3
#
# Copyright 2018, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unittests for module_info."""

# pylint: disable=line-too-long
# pylint: disable=missing-function-docstring

import os
import shutil
import tempfile
import unittest

from pathlib import Path
from unittest import mock

import constants
import module_info
import unittest_utils
import unittest_constants as uc

JSON_FILE_PATH = os.path.join(uc.TEST_DATA_DIR, uc.JSON_FILE)
CC_DEP_PATH = os.path.join(uc.TEST_DATA_DIR, uc.CC_DEP_FILE)
JAVA_DEP_PATH = os.path.join(uc.TEST_DATA_DIR, uc.JAVA_DEP_FILE)
EXPECTED_MOD_TARGET = 'tradefed'
EXPECTED_MOD_TARGET_PATH = ['tf/core']
UNEXPECTED_MOD_TARGET = 'this_should_not_be_in_module-info.json'
MOD_NO_PATH = 'module-no-path'
PATH_TO_MULT_MODULES = 'shared/path/to/be/used'
MULT_MOODULES_WITH_SHARED_PATH = ['module2', 'module1']
PATH_TO_MULT_MODULES_WITH_MULTI_ARCH = 'shared/path/to/be/used2'
TESTABLE_MODULES_WITH_SHARED_PATH = ['multiarch1', 'multiarch2', 'multiarch3', 'multiarch3_32']

ROBO_MOD_PATH = ['/shared/robo/path']
ROBO_MODULE = 'FooTests'
ASSOCIATED_ROBO_MODULE = 'RunFooTests'
ROBO_MODULE_INFO = {
    constants.MODULE_NAME: ROBO_MODULE,
    constants.MODULE_PATH: ROBO_MOD_PATH,
    constants.MODULE_CLASS: [constants.MODULE_CLASS_JAVA_LIBRARIES]}
ASSOCIATED_ROBO_MODULE_INFO = {
    constants.MODULE_NAME: ASSOCIATED_ROBO_MODULE,
    constants.MODULE_PATH: ROBO_MOD_PATH,
    constants.MODULE_CLASS: [constants.MODULE_CLASS_ROBOLECTRIC]}
MOD_PATH_INFO_DICT = {ROBO_MOD_PATH[0]: [ASSOCIATED_ROBO_MODULE_INFO, ROBO_MODULE_INFO]}
MOD_NAME_INFO_DICT = {
    ASSOCIATED_ROBO_MODULE: ASSOCIATED_ROBO_MODULE_INFO,
    ROBO_MODULE: ROBO_MODULE_INFO}
MOD_NAME1 = 'mod1'
MOD_NAME2 = 'mod2'
MOD_NAME3 = 'mod3'
MOD_NAME4 = 'mod4'
MOD_INFO_DICT = {}
MODULE_INFO = {constants.MODULE_NAME: 'random_name',
               constants.MODULE_PATH: 'a/b/c/path',
               constants.MODULE_CLASS: ['random_class']}
NAME_TO_MODULE_INFO = {'random_name' : MODULE_INFO}
# Mocking path allows str only, use os.path instead of Path.
BUILD_TOP_DIR = tempfile.TemporaryDirectory().name
SOONG_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/soong')
PRODUCT_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/target/product/vsoc_x86_64')
HOST_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/host/linux-x86')

#pylint: disable=protected-access
class ModuleInfoUnittests(unittest.TestCase):
    """Unit tests for module_info.py"""

    def setUp(self) -> None:
        for path in [BUILD_TOP_DIR, PRODUCT_OUT_DIR, SOONG_OUT_DIR, HOST_OUT_DIR]:
            if not Path(path).is_dir():
                Path(path).mkdir(parents=True)
        shutil.copy2(JSON_FILE_PATH, PRODUCT_OUT_DIR)
        self.json_file_path = Path(PRODUCT_OUT_DIR).joinpath(uc.JSON_FILE)
        shutil.copy2(CC_DEP_PATH, SOONG_OUT_DIR)
        self.cc_dep_path = Path(SOONG_OUT_DIR).joinpath(uc.CC_DEP_FILE)
        shutil.copy2(JAVA_DEP_PATH, SOONG_OUT_DIR)
        self.java_dep_path = Path(SOONG_OUT_DIR).joinpath(uc.JAVA_DEP_FILE)
        self.merged_dep_path = Path(PRODUCT_OUT_DIR).joinpath(uc.MERGED_DEP_FILE)

    def tearDown(self) -> None:
        if self.merged_dep_path.is_file():
            os.remove(self.merged_dep_path)

    @mock.patch('json.load', return_value={})
    @mock.patch('builtins.open', new_callable=mock.mock_open)
    @mock.patch('os.path.isfile', return_value=True)
    def test_load_mode_info_file_out_dir_handling(self, _isfile, _open, _json):
        """Test _load_module_info_file out dir handling."""
        # Test out default out dir is used.
        build_top = '/path/to/top'
        default_out_dir = os.path.join(build_top, 'out/dir/here')
        os_environ_mock = {'ANDROID_PRODUCT_OUT': default_out_dir,
                           constants.ANDROID_BUILD_TOP: build_top}
        default_out_dir_mod_targ = 'out/dir/here/module-info.json'
        # Make sure module_info_target is what we think it is.
        with mock.patch.dict('os.environ', os_environ_mock, clear=True):
            mod_info = module_info.ModuleInfo(index_dir=HOST_OUT_DIR)
            self.assertEqual(default_out_dir_mod_targ,
                             mod_info.module_info_target)

        # Test out custom out dir is used (OUT_DIR=dir2).
        custom_out_dir = os.path.join(build_top, 'out2/dir/here')
        os_environ_mock = {'ANDROID_PRODUCT_OUT': custom_out_dir,
                           constants.ANDROID_BUILD_TOP: build_top}
        custom_out_dir_mod_targ = 'out2/dir/here/module-info.json'
        # Make sure module_info_target is what we think it is.
        with mock.patch.dict('os.environ', os_environ_mock, clear=True):
            mod_info = module_info.ModuleInfo(index_dir=HOST_OUT_DIR)
            self.assertEqual(custom_out_dir_mod_targ,
                             mod_info.module_info_target)

        # Test out custom abs out dir is used (OUT_DIR=/tmp/out/dir2).
        abs_custom_out_dir = '/tmp/out/dir'
        os_environ_mock = {'ANDROID_PRODUCT_OUT': abs_custom_out_dir,
                           constants.ANDROID_BUILD_TOP: build_top}
        custom_abs_out_dir_mod_targ = '/tmp/out/dir/module-info.json'
        # Make sure module_info_target is what we think it is.
        with mock.patch.dict('os.environ', os_environ_mock, clear=True):
            mod_info = module_info.ModuleInfo(index_dir=HOST_OUT_DIR)
            self.assertEqual(custom_abs_out_dir_mod_targ,
                             mod_info.module_info_target)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, '_load_module_info_file')
    def test_get_path_to_module_info(self, mock_load_module):
        """Test that we correctly create the path to module info dict."""
        mod_one = 'mod1'
        mod_two = 'mod2'
        mod_path_one = '/path/to/mod1'
        mod_path_two = '/path/to/mod2'
        mod_info_dict = {mod_one: {constants.MODULE_PATH: [mod_path_one],
                                   constants.MODULE_NAME: mod_one},
                         mod_two: {constants.MODULE_PATH: [mod_path_two],
                                   constants.MODULE_NAME: mod_two}}
        mock_load_module.return_value = ('mod_target', mod_info_dict)
        path_to_mod_info = {mod_path_one: [{constants.MODULE_NAME: mod_one,
                                            constants.MODULE_PATH: [mod_path_one]}],
                            mod_path_two: [{constants.MODULE_NAME: mod_two,
                                            constants.MODULE_PATH: [mod_path_two]}]}
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH,
                                          index_dir=HOST_OUT_DIR)
        self.assertDictEqual(path_to_mod_info,
                             mod_info._get_path_to_module_info(mod_info_dict))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_module(self):
        """Test that we get the module when it's properly loaded."""
        # Load up the test json file and check that module is in it
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertTrue(mod_info.is_module(EXPECTED_MOD_TARGET))
        self.assertFalse(mod_info.is_module(UNEXPECTED_MOD_TARGET))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_path(self):
        """Test that we get the module path when it's properly loaded."""
        # Load up the test json file and check that module is in it
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertEqual(mod_info.get_paths(EXPECTED_MOD_TARGET),
                         EXPECTED_MOD_TARGET_PATH)
        self.assertEqual(mod_info.get_paths(MOD_NO_PATH), [])

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_module_names(self):
        """test that we get the module name properly."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertEqual(mod_info.get_module_names(EXPECTED_MOD_TARGET_PATH[0]),
                         [EXPECTED_MOD_TARGET])
        unittest_utils.assert_strict_equal(
            self, mod_info.get_module_names(PATH_TO_MULT_MODULES),
            MULT_MOODULES_WITH_SHARED_PATH)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_path_to_mod_info(self):
        """test that we get the module name properly."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        module_list = []
        for path_to_mod_info in mod_info.path_to_module_info[PATH_TO_MULT_MODULES_WITH_MULTI_ARCH]:
            module_list.append(path_to_mod_info.get(constants.MODULE_NAME))
        module_list.sort()
        TESTABLE_MODULES_WITH_SHARED_PATH.sort()
        self.assertEqual(module_list, TESTABLE_MODULES_WITH_SHARED_PATH)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_suite_in_compatibility_suites(self):
        """Test is_suite_in_compatibility_suites."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        info = {'compatibility_suites': []}
        self.assertFalse(mod_info.is_suite_in_compatibility_suites("cts", info))
        info2 = {'compatibility_suites': ["cts"]}
        self.assertTrue(mod_info.is_suite_in_compatibility_suites("cts", info2))
        self.assertFalse(mod_info.is_suite_in_compatibility_suites("vts10", info2))
        info3 = {'compatibility_suites': ["cts", "vts10"]}
        self.assertTrue(mod_info.is_suite_in_compatibility_suites("cts", info3))
        self.assertTrue(mod_info.is_suite_in_compatibility_suites("vts10", info3))
        self.assertFalse(mod_info.is_suite_in_compatibility_suites("ats", info3))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR,
                                    constants.ANDROID_HOST_OUT:HOST_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'is_testable_module')
    @mock.patch.object(module_info.ModuleInfo, 'is_suite_in_compatibility_suites')
    def test_get_testable_modules(self, mock_is_suite_exist, mock_is_testable):
        """Test get_testable_modules."""
        # 1. No modules.idx yet, will run _get_testable_modules()
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertEqual(len(mod_info.get_testable_modules()), 28)

        # 2. read modules.idx.
        expected_modules = {'dep_test_module', 'MainModule2', 'test_dep_level_1_1'}
        self.assertTrue(expected_modules.issubset(mod_info.get_testable_modules()))

        # 3. search modules by giving a suite name, run _get_testable_modules()
        mod_info.name_to_module_info = NAME_TO_MODULE_INFO
        mock_is_testable.return_value = True
        mock_is_suite_exist.return_value = True
        self.assertEqual(1, len(mod_info.get_testable_modules('test_suite')))
        mock_is_suite_exist.return_value = False
        self.assertEqual(0, len(mod_info.get_testable_modules('test_suite')))
        self.assertEqual(1, len(mod_info.get_testable_modules()))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'has_test_config')
    @mock.patch.object(module_info.ModuleInfo, 'is_robolectric_test')
    def test_is_testable_module(self, mock_is_robo_test, mock_has_test_config):
        """Test is_testable_module."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mock_is_robo_test.return_value = False
        mock_has_test_config.return_value = True
        installed_module_info = {constants.MODULE_INSTALLED:
                                 uc.DEFAULT_INSTALL_PATH}
        non_installed_module_info = {constants.MODULE_NAME: 'rand_name'}
        # Empty mod_info or a non-installed module.
        self.assertFalse(mod_info.is_testable_module(non_installed_module_info))
        self.assertFalse(mod_info.is_testable_module({}))
        # Testable Module or is a robo module for non-installed module.
        self.assertTrue(mod_info.is_testable_module(installed_module_info))
        mock_has_test_config.return_value = False
        self.assertFalse(mod_info.is_testable_module(installed_module_info))
        mock_is_robo_test.return_value = True
        self.assertTrue(mod_info.is_testable_module(non_installed_module_info))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'is_auto_gen_test_config')
    def test_has_test_config(self, mock_is_auto_gen):
        """Test has_test_config."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        info = {constants.MODULE_PATH:[uc.TEST_DATA_DIR]}
        mock_is_auto_gen.return_value = True
        # Validate we see the config when it's auto-generated.
        self.assertTrue(mod_info.has_test_config(info))
        self.assertTrue(mod_info.has_test_config({}))
        # Validate when actual config exists and there's no auto-generated config.
        mock_is_auto_gen.return_value = False
        info = {constants.MODULE_PATH:[uc.TEST_DATA_DIR]}
        self.assertTrue(mod_info.has_test_config(info))
        self.assertFalse(mod_info.has_test_config({}))
        # Validate the case mod_info MODULE_TEST_CONFIG be set
        info2 = {constants.MODULE_PATH:[uc.TEST_CONFIG_DATA_DIR],
                 constants.MODULE_TEST_CONFIG:[os.path.join(
                     uc.TEST_CONFIG_DATA_DIR, "a.xml.data")]}
        self.assertTrue(mod_info.has_test_config(info2))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'get_module_names')
    def test_get_robolectric_test_name(self, mock_get_module_names):
        """Test get_robolectric_test_name."""
        # Happy path testing, make sure we get the run robo target.
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mod_info.name_to_module_info = MOD_NAME_INFO_DICT
        mod_info.path_to_module_info = MOD_PATH_INFO_DICT
        mock_get_module_names.return_value = [ASSOCIATED_ROBO_MODULE, ROBO_MODULE]
        self.assertEqual(mod_info.get_robolectric_test_name(
            ROBO_MODULE), ASSOCIATED_ROBO_MODULE)
        # Let's also make sure we don't return anything when we're not supposed
        # to.
        mock_get_module_names.return_value = [ROBO_MODULE]
        self.assertEqual(mod_info.get_robolectric_test_name(
            ROBO_MODULE), None)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'is_robolectric_module')
    @mock.patch('os.path.isfile', return_value=False)
    @mock.patch.object(module_info.ModuleInfo, 'get_module_info')
    @mock.patch.object(module_info.ModuleInfo, 'get_module_names')
    def test_get_robolectric_type(self, mock_get_module_names, mock_get_module_info,
        mock_isfile, mock_is_robo_mod):
        """Test get_robolectric_type."""
        # Happy path testing, make sure we get the run robo target.
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mod_info.name_to_module_info = MOD_NAME_INFO_DICT
        mod_info.path_to_module_info = MOD_PATH_INFO_DICT
        mock_isfile.return_value = False
        mock_get_module_names.return_value = [ASSOCIATED_ROBO_MODULE, ROBO_MODULE]
        mock_get_module_info.return_value = ASSOCIATED_ROBO_MODULE_INFO
        # Test on an legacy associated robo module.
        self.assertEqual(
            mod_info.get_robolectric_type(ASSOCIATED_ROBO_MODULE), constants.ROBOTYPE_LEGACY)
        # Test on a legacy robo module.
        self.assertEqual(
            mod_info.get_robolectric_type(ROBO_MODULE), constants.ROBOTYPE_LEGACY)
        # Test on a modern robo module.
        mock_isfile.return_value = True
        self.assertEqual(
            mod_info.get_robolectric_type(ROBO_MODULE), constants.ROBOTYPE_MODERN)
        # Two situations that are not a robolectric test:
        # 1. Not is_robolectric_module:
        mock_is_robo_mod.return_value = False
        self.assertEqual(mod_info.get_robolectric_type(ROBO_MODULE), 0)
        # 2. The path in the mod_info is inexistent.
        mod_info.path_to_module_info = {'/inexist': ['Foo', 'RunFoo']}
        self.assertEqual(mod_info.get_robolectric_type(ROBO_MODULE), 0)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'get_robolectric_type')
    def test_is_robolectric_test(self, mock_type):
        """Test is_robolectric_test."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mock_type.return_value = constants.ROBOTYPE_MODERN
        self.assertTrue(mod_info.is_robolectric_test(ROBO_MODULE))
        mock_type.return_value = constants.ROBOTYPE_LEGACY
        self.assertTrue(mod_info.is_robolectric_test(ROBO_MODULE))
        mock_type.return_value = 0
        self.assertFalse(mod_info.is_robolectric_test(ROBO_MODULE))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    @mock.patch.object(module_info.ModuleInfo, 'is_module')
    def test_is_auto_gen_test_config(self, mock_is_module):
        """Test is_auto_gen_test_config correctly detects the module."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mock_is_module.return_value = True
        is_auto_test_config = {'auto_test_config': [True]}
        is_not_auto_test_config = {'auto_test_config': [False]}
        is_not_auto_test_config_again = {'auto_test_config': []}
        MOD_INFO_DICT[MOD_NAME1] = is_auto_test_config
        MOD_INFO_DICT[MOD_NAME2] = is_not_auto_test_config
        MOD_INFO_DICT[MOD_NAME3] = is_not_auto_test_config_again
        MOD_INFO_DICT[MOD_NAME4] = {}
        mod_info.name_to_module_info = MOD_INFO_DICT
        self.assertTrue(mod_info.is_auto_gen_test_config(MOD_NAME1))
        self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME2))
        self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME3))
        self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME4))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_robolectric_module(self):
        """Test is_robolectric_module correctly detects the module."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        is_robolectric_module = {'class': ['ROBOLECTRIC']}
        is_not_robolectric_module = {'class': ['OTHERS']}
        MOD_INFO_DICT[MOD_NAME1] = is_robolectric_module
        MOD_INFO_DICT[MOD_NAME2] = is_not_robolectric_module
        mod_info.name_to_module_info = MOD_INFO_DICT
        self.assertTrue(mod_info.is_robolectric_module(MOD_INFO_DICT[MOD_NAME1]))
        self.assertFalse(mod_info.is_robolectric_module(MOD_INFO_DICT[MOD_NAME2]))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_merge_build_system_infos(self):
        """Test _merge_build_system_infos."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mod_info_1 = {constants.MODULE_NAME: 'module_1',
                      constants.MODULE_DEPENDENCIES: []}
        name_to_mod_info = {'module_1' : mod_info_1}
        expect_deps = ['test_dep_level_1_1', 'test_dep_level_1_2']
        name_to_mod_info = mod_info._merge_build_system_infos(
            name_to_mod_info, java_bp_info_path=self.java_dep_path)
        self.assertEqual(
            name_to_mod_info['module_1'].get(constants.MODULE_DEPENDENCIES),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_merge_dependency_with_ori_dependency(self):
        """Test _merge_dependency."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mod_info_1 = {constants.MODULE_NAME: 'module_1',
                      constants.MODULE_DEPENDENCIES: ['ori_dep_1']}
        name_to_mod_info = {'module_1' : mod_info_1}
        expect_deps = ['ori_dep_1', 'test_dep_level_1_1', 'test_dep_level_1_2']
        name_to_mod_info = mod_info._merge_build_system_infos(
            name_to_mod_info, java_bp_info_path=self.java_dep_path)
        self.assertEqual(
            name_to_mod_info['module_1'].get(constants.MODULE_DEPENDENCIES),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_module_dependency(self):
        """Test get_module_dependency."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        expect_deps = {'test_dep_level_1_1', 'module_1', 'test_dep_level_1_2',
                       'test_dep_level_2_2', 'test_dep_level_2_1', 'module_2'}
        mod_info._merge_build_system_infos(mod_info.name_to_module_info,
                                   java_bp_info_path=self.java_dep_path)
        self.assertEqual(
            mod_info.get_module_dependency('dep_test_module'),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_module_dependency_w_loop(self):
        """Test get_module_dependency with problem dep file."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        # Java dependency file with a endless loop define.
        java_dep_file = os.path.join(uc.TEST_DATA_DIR,
                                     'module_bp_java_loop_deps.json')
        expect_deps = {'test_dep_level_1_1', 'module_1', 'test_dep_level_1_2',
                       'test_dep_level_2_2', 'test_dep_level_2_1', 'module_2'}
        mod_info._merge_build_system_infos(mod_info.name_to_module_info,
                                   java_bp_info_path=java_dep_file)
        self.assertEqual(
            mod_info.get_module_dependency('dep_test_module'),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_install_module_dependency(self):
        """Test get_install_module_dependency."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        expect_deps = {'module_1', 'test_dep_level_2_1'}
        mod_info._merge_build_system_infos(mod_info.name_to_module_info,
                                           java_bp_info_path=self.java_dep_path)
        self.assertEqual(
            mod_info.get_install_module_dependency('dep_test_module'),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_cc_merge_build_system_infos(self):
        """Test _merge_build_system_infos for cc."""
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        mod_info_1 = {constants.MODULE_NAME: 'module_cc_1',
                      constants.MODULE_DEPENDENCIES: []}
        name_to_mod_info = {'module_cc_1' : mod_info_1}
        expect_deps = ['test_cc_dep_level_1_1', 'test_cc_dep_level_1_2']
        name_to_mod_info = mod_info._merge_build_system_infos(
            name_to_mod_info, cc_bp_info_path=self.cc_dep_path)
        self.assertEqual(
            name_to_mod_info['module_cc_1'].get(constants.MODULE_DEPENDENCIES),
            expect_deps)

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_unit_test(self):
        """Test is_unit_test."""
        module_name = 'myModule'
        maininfo_with_unittest = {constants.MODULE_NAME: module_name,
                                  constants.MODULE_IS_UNIT_TEST: 'true'}
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertTrue(mod_info.is_unit_test(maininfo_with_unittest))


    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_host_unit_test(self):
        """Test is_host_unit_test."""
        module_name = 'myModule'
        maininfo_with_host_unittest = {
            constants.MODULE_NAME: module_name,
            constants.MODULE_IS_UNIT_TEST: 'true',
            'compatibility_suites': ['host-unit-tests']
        }
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        self.assertTrue(mod_info.is_host_unit_test(maininfo_with_host_unittest))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:'/',
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_is_device_driven_test(self):
        module_name = 'myModule'
        maininfo_with_device_driven_test = {
            constants.MODULE_NAME: module_name,
            constants.MODULE_TEST_CONFIG:[os.path.join(
                     uc.TEST_CONFIG_DATA_DIR, "a.xml.data")],
            constants.MODULE_INSTALLED: uc.DEFAULT_INSTALL_PATH,
            'supported_variants': ['DEVICE']
        }
        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)

        self.assertTrue(mod_info.is_device_driven_test(maininfo_with_device_driven_test))

    @mock.patch.dict('os.environ', {constants.ANDROID_BUILD_TOP:os.path.dirname(__file__),
                                    constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_has_mainline_modules(self):
        """Test has_mainline_modules."""
        name1 = 'MainModule1'
        mainline_module1 = 'foo2.apk+foo3.apk'
        name2 = 'MainModule2'
        mainline_module2 = 'foo1.apex'
        name3 = 'MainModule3'

        mod_info = module_info.ModuleInfo(module_file=JSON_FILE_PATH)
        # found in 'test_mainlne_modules' attribute.
        self.assertTrue(mod_info.has_mainline_modules(name1, mainline_module1))
        # found in the value of 'mainline-param' in test_config.
        self.assertTrue(mod_info.has_mainline_modules(name2, mainline_module2))
        # cannot be found in both 'test_mainline_modules' and 'test_config'.
        self.assertFalse(mod_info.has_mainline_modules(name3, mainline_module2))

    @mock.patch.dict('os.environ',
                     {constants.ANDROID_BUILD_TOP:os.path.dirname(__file__),
                      constants.ANDROID_PRODUCT_OUT:PRODUCT_OUT_DIR})
    def test_get_module_info_for_multi_lib_module(self):
        my_module_name = 'MyMultiArchTestModule'
        multi_arch_json = os.path.join(uc.TEST_DATA_DIR,
                                       'multi_arch_module-info.json')
        mod_info = module_info.ModuleInfo(module_file=multi_arch_json)

        self.assertIsNotNone(mod_info.get_module_info(my_module_name))

if __name__ == '__main__':
    unittest.main()
