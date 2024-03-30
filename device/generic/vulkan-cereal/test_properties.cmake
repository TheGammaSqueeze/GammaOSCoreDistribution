# This file is executed by CTest prior to running tests
# It can be used to set extra properties on tests.
#
# Example:
#   set_tests_properties(MyTestSuite.SomeTest PROPERTIES LABELS foobar)
#
# For this to work:
# 1. Use gtest_discover_tests() to add the test targets
# 2. Call the set_test_include_files() macro at the end of each CMakeLists.txt that defines tests
