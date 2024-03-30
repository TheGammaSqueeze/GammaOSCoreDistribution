/*
 *
 * Copyright 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "guest_session.h"

#include <future>

namespace android {
namespace hardware {
namespace confirmationui {
namespace V1_0 {
namespace implementation {
using TeeuiRc = teeui::ResponseCode;

GuestSession::ResultTriple GuestSession::PromptUserConfirmation() {
    std::unique_lock<std::mutex> stateLock(listener_state_lock_);
    /*
     * This is the main listener thread function. The listener thread life cycle
     * is equivalent to the life cycle of a single confirmation request. The life
     * cycle is divided in four phases.
     *  * The starting phase:
     *    * Drives the cuttlefish confirmation UI session on the host side, too
     *
     * Note: During the starting phase the hwbinder service thread is blocked and
     * waiting for possible Errors. If the setup phase concludes successfully, the
     * hwbinder service thread gets unblocked and returns successfully. Errors
     * that occur after the first phase are delivered by callback interface.
     *
     * For cuttlefish, it means that the guest will conduct a blocking wait for
     * an ack to kStart.
     *
     *  * The 2nd phase - non interactive phase
     *    * After a grace period:
     *      * guest will pick up cuttlefish host's ack to kStart
     *
     *  * The 3rd phase - interactive phase
     *    * We wait to any external event
     *      * Abort
     *      * Secure user input asserted
     *    * The result is fetched from the TA.
     *
     *  * The 4th phase - cleanup
     *    * Sending the kStop command to the cuttlefish host, and wait for ack
     */

    GuestSession::ResultTriple error;
    auto& error_rc = std::get<ResponseCode>(error);
    error_rc = ResponseCode::SystemError;

    CHECK(listener_state_ == ListenerState::Starting) << "ListenerState should be Starting";

    // initiate prompt
    ConfUiLog(INFO) << "Initiating prompt";
    const std::uint32_t payload_lower_bound =
        static_cast<std::uint32_t>(prompt_text_.size() + extra_data_.size());
    const std::uint32_t upper_bound =
        static_cast<std::uint32_t>(cuttlefish::confui::kMaxMessageLength);
    if (payload_lower_bound > upper_bound) {
        ConfUiLog(INFO) << "UI message too long to send to the host";
        // message is too long anyway, and don't send it to the host
        error_rc = ResponseCode::UIErrorMessageTooLong;
        return error;
    }
    SerializedSend(cuttlefish::confui::SendStartCmd, host_fd_, session_name_, prompt_text_,
                   extra_data_, locale_, ui_options_);
    ConfUiLog(INFO) << "Session " << GetSessionId() << " started on both the guest and the host";

    auto clean_up_and_get_first = [&]() -> std::unique_ptr<ConfUiMessage> {
        // blocking wait to get the first msg that belongs to this session
        while (true) {
            auto first_curr_session_msg = incoming_msg_queue_.Pop();
            if (!first_curr_session_msg ||
                first_curr_session_msg->GetSessionId() != GetSessionId()) {
                continue;
            }
            return std::move(first_curr_session_msg);
        }
    };

    /*
     * Unconditionally wait ack, or host abort
     *
     * First couple of messages could be from the previous session.
     * We should clear them up.
     *
     * Even though the guest HAL sends kAbort to the host, the kAbort
     * does not happen immediately. Between the incoming_msg_queue_.FlushAll()
     * and the actual abort on the host, there could still be messages
     * sent from the host to the guest. As these lines are the first read
     * for the current session, we clear up the preceding messages
     * from the previous session until we see the message for the current
     * session.
     *
     * Note that abort() call puts the Abort command in the queue. So,
     * it will also show up in incoming_msg_queue_
     *
     */
    auto first_msg = std::move(clean_up_and_get_first());

    cuttlefish::confui::ConfUiAckMessage& start_ack_msg =
        static_cast<cuttlefish::confui::ConfUiAckMessage&>(*first_msg);
    if (!start_ack_msg.IsSuccess()) {
        // handle errors: MALFORMED_UTF8 or Message too long
        const std::string error_msg = start_ack_msg.GetStatusMessage();
        if (error_msg == cuttlefish::confui::HostError::kMessageTooLongError) {
            ConfUiLog(ERROR) << "Message + Extra data + Meta info were too long";
            error_rc = ResponseCode::UIErrorMessageTooLong;
        }
        if (error_msg == cuttlefish::confui::HostError::kIncorrectUTF8) {
            ConfUiLog(ERROR) << "Message is incorrectly UTF-encoded";
            error_rc = ResponseCode::UIErrorMalformedUTF8Encoding;
        }
        return error;
    }

    //  ############################## Start 2nd Phase #############################################
    listener_state_ = ListenerState::SetupDone;
    ConfUiLog(INFO) << "Transition to SetupDone";
    stateLock.unlock();
    listener_state_condv_.notify_all();

    // cuttlefish does not need the second phase to implement HAL APIs
    // input was already prepared before the confirmation UI screen was rendered

    //  ############################## Start 3rd Phase - interactive phase #########################
    stateLock.lock();
    listener_state_ = ListenerState::Interactive;
    ConfUiLog(INFO) << "Transition to Interactive";
    stateLock.unlock();
    listener_state_condv_.notify_all();

    // give deliverSecureInputEvent a chance to interrupt

    // wait for an input but should not block deliverSecureInputEvent or Abort
    // Thus, it should not hold the stateLock
    std::mutex input_ready_mtx;
    std::condition_variable input_ready_cv_;
    std::unique_lock<std::mutex> input_ready_lock(input_ready_mtx);
    bool input_ready = false;
    auto wait_input_and_signal = [&]() -> std::unique_ptr<ConfUiMessage> {
        auto msg = incoming_msg_queue_.Pop();
        {
            std::unique_lock<std::mutex> lock(input_ready_mtx);
            input_ready = true;
            input_ready_cv_.notify_one();
        }
        return msg;
    };
    auto input_and_signal_future = std::async(std::launch::async, wait_input_and_signal);
    input_ready_cv_.wait(input_ready_lock, [&]() { return input_ready; });
    // now an input is ready, so let's acquire the stateLock

    stateLock.lock();
    auto user_or_abort = input_and_signal_future.get();

    if (user_or_abort->GetType() == cuttlefish::confui::ConfUiCmd::kAbort) {
        ConfUiLog(ERROR) << "Abort called or the user/host aborted"
                         << " while waiting user response";
        return {ResponseCode::Aborted, {}, {}};
    }
    if (user_or_abort->GetType() == cuttlefish::confui::ConfUiCmd::kCliAck) {
        auto& ack_msg = static_cast<cuttlefish::confui::ConfUiAckMessage&>(*user_or_abort);
        if (ack_msg.IsSuccess()) {
            ConfUiLog(ERROR) << "When host failed, it is supposed to send "
                             << "kCliAck with fail, but this is kCliAck with success";
        }
        error_rc = ResponseCode::SystemError;
        return error;
    }
    cuttlefish::confui::ConfUiCliResponseMessage& user_response =
        static_cast<cuttlefish::confui::ConfUiCliResponseMessage&>(*user_or_abort);

    // pick, see if it is response, abort cmd
    // handle abort or error response here
    ConfUiLog(INFO) << "Making up the result";

    // make up the result triple
    if (user_response.GetResponse() == cuttlefish::confui::UserResponse::kCancel) {
        SerializedSend(cuttlefish::confui::SendStopCmd, host_fd_, GetSessionId());
        return {ResponseCode::Canceled, {}, {}};
    }

    if (user_response.GetResponse() != cuttlefish::confui::UserResponse::kConfirm) {
        ConfUiLog(ERROR) << "Unexpected user response that is " << user_response.GetResponse();
        return error;
    }
    SerializedSend(cuttlefish::confui::SendStopCmd, host_fd_, GetSessionId());
    //  ############################## Start 4th Phase - cleanup ##################################
    return {ResponseCode::OK, user_response.GetMessage(), user_response.GetSign()};
}

Return<ResponseCode> GuestSession::DeliverSecureInputEvent(
    const android::hardware::keymaster::V4_0::HardwareAuthToken& auth_token) {
    ResponseCode rc = ResponseCode::Ignored;
    {
        /*
         * deliverSecureInputEvent is only used by the VTS test to mock human input. A correct
         * implementation responds with a mock confirmation token signed with a test key. The
         * problem is that the non interactive grace period was not formalized in the HAL spec,
         * so that the VTS test does not account for the grace period. (It probably should.)
         * This means we can only pass the VTS test if we block until the grace period is over
         * (SetupDone -> Interactive) before we deliver the input event.
         *
         * The true secure input is delivered by a different mechanism and gets ignored -
         * not queued - until the grace period is over.
         *
         */
        std::unique_lock<std::mutex> stateLock(listener_state_lock_);
        listener_state_condv_.wait(stateLock,
                                   [this] { return listener_state_ != ListenerState::SetupDone; });
        if (listener_state_ != ListenerState::Interactive) return ResponseCode::Ignored;
        if (static_cast<TestModeCommands>(auth_token.challenge) == TestModeCommands::OK_EVENT) {
            SerializedSend(cuttlefish::confui::SendUserSelection, host_fd_, GetSessionId(),
                           cuttlefish::confui::UserResponse::kConfirm);
        } else {
            SerializedSend(cuttlefish::confui::SendUserSelection, host_fd_, GetSessionId(),
                           cuttlefish::confui::UserResponse::kCancel);
        }
        rc = ResponseCode::OK;
    }
    listener_state_condv_.notify_all();
    // VTS test expect an OK response if the event was successfully delivered.
    // But since the TA returns the callback response now, we have to translate
    // Canceled into OK. Canceled is only returned if the delivered event canceled
    // the operation, which means that the event was successfully delivered. Thus
    // we return OK.
    if (rc == ResponseCode::Canceled) return ResponseCode::OK;
    return rc;
}

Return<void> GuestSession::Abort() {
    {
        std::unique_lock<std::mutex> stateLock(listener_state_lock_);
        if (listener_state_ == ListenerState::SetupDone ||
            listener_state_ == ListenerState::Interactive) {
            if (host_fd_->IsOpen()) {
                SerializedSend(cuttlefish::confui::SendAbortCmd, host_fd_, GetSessionId());
            }
            using cuttlefish::confui::ConfUiAbortMessage;
            auto local_abort_cmd = std::make_unique<ConfUiAbortMessage>(GetSessionId());
            incoming_msg_queue_.Push(std::move(local_abort_cmd));
        }
    }
    listener_state_condv_.notify_all();
    return Void();
}
}  // namespace implementation
}  // namespace V1_0
}  // namespace confirmationui
}  // namespace hardware
}  // namespace android
