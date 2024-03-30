#include "host-common/GfxstreamFatalError.h"

#include <cstdlib>
#include <ostream>

#include "base/Metrics.h"
#include "host-common/logging.h"

namespace {

using android::base::CreateMetricsLogger;
using android::base::GfxstreamVkAbort;

std::optional<std::function<void()>> customDieFunction = std::nullopt;

[[noreturn]] void die() {
    if (customDieFunction) {
        (*customDieFunction)();
    }
    abort();
}

}  // namespace

namespace emugl {

AbortMessage::AbortMessage(const char *file, const char *function, int line, FatalError reason)
    : mFile(file), mFunction(function), mLine(line), mReason(reason) {
    mOss << "FATAL in " << function << ", err code: " << reason.getAbortCode() << ": ";
}

AbortMessage::~AbortMessage() {
    OutputLog(stderr, 'F', mFile, mLine, 0, mOss.str().c_str());
    fflush(stderr);
    CreateMetricsLogger()->logMetricEvent(GfxstreamVkAbort{.file = mFile,
                                                           .function = mFunction,
                                                           .msg = mOss.str().c_str(),
                                                           .line = mLine,
                                                           .abort_reason = mReason.getAbortCode()});

    die();
}

void setDieFunction(std::optional<std::function<void()>> newDie) { customDieFunction = newDie; }
}  // namespace emugl
