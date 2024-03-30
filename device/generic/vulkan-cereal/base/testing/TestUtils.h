#ifndef BASE_TESTING_TESTUTILS_H_
#define BASE_TESTING_TESTUTILS_H_

#include <gmock/gmock.h>

#include <regex>
#include <string>

// The original gtest MatchesRegex will use different regex implementation on different platforms,
// e.g. on Windows gtest's limited regex engine is used while on Linux Posix ERE is used, which
// could result incompatible syntaxes. See
// https://github.com/google/googletest/blob/main/docs/advanced.md#regular-expression-syntax for
// details. std::regex will by default use the ECMAScript syntax for all platforms, which could fix
// this issue.
MATCHER_P(MatchesStdRegex, regStr, std::string("contains regular expression: ") + regStr) {
    std::regex reg(regStr);
    return std::regex_search(arg, reg);
}

#endif  // BASE_TESTING_TESTUTILS_H_
