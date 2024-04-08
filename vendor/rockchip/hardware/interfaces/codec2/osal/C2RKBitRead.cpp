/*
*
* Copyright 2019 Rockchip Electronics Co. LTD
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include <stdlib.h>
#include <string.h>

#include "C2RKBitRead.h"

bool c2_update_curbyte(BitReadContext *bitctx) {
    if (bitctx->bytes_left_ < 1)
        return true;

    // Emulation prevention three-byte detection.
    // If a sequence of 0x000003 is found, skip (ignore) the last byte (0x03).
    if (bitctx->need_prevention_detection
        && (*bitctx->data_ == 0x03)
        && ((bitctx->prev_two_bytes_ & 0xffff) == 0)) {
        // Detected 0x000003, skip last byte.
        ++bitctx->data_;
        --bitctx->bytes_left_;
        ++bitctx->emulation_prevention_bytes_;
        bitctx->used_bits += 8;
        // Need another full three bytes before we can detect the sequence again.
        bitctx->prev_two_bytes_ = 0xffff;
        if (bitctx->bytes_left_ < 1)
            return  false;
    }
    // Load a new byte and advance pointers.
    bitctx->curr_byte_ = *bitctx->data_++ & 0xff;
    --bitctx->bytes_left_;
    bitctx->num_remaining_bits_in_curr_byte_ = 8;
    bitctx->prev_two_bytes_ = (bitctx->prev_two_bytes_ << 8) | bitctx->curr_byte_;

    return true;
}

/*!
***********************************************************************
* \brief
*   Read |num_bits| (1 to 31 inclusive) from the stream and return them
*   in |out|, with first bit in the stream as MSB in |out| at position
*   (|num_bits| - 1)
***********************************************************************
*/
bool c2_read_bits(BitReadContext *bitctx, int32_t num_bits, int32_t *out) {
    int32_t bits_left = num_bits;
    *out = 0;
    if (num_bits > 31) {
        return  false;
    }
    while (bitctx->num_remaining_bits_in_curr_byte_ < bits_left) {
        // Take all that's left in current byte, shift to make space for the rest.
        *out |= (bitctx->curr_byte_ << (bits_left - bitctx->num_remaining_bits_in_curr_byte_));
        bits_left -= bitctx->num_remaining_bits_in_curr_byte_;
        if (!c2_update_curbyte(bitctx)) {
            return  false;
        }
    }
    *out |= (bitctx->curr_byte_ >> (bitctx->num_remaining_bits_in_curr_byte_ - bits_left));
    *out &= ((1 << num_bits) - 1);
    bitctx->num_remaining_bits_in_curr_byte_ -= bits_left;
    bitctx->used_bits += num_bits;

    return true;
}
/*!
***********************************************************************
* \brief
*   read more than 32 bits data
***********************************************************************
*/
bool c2_read_longbits(BitReadContext *bitctx, int32_t num_bits, uint32_t *out) {
    int32_t val = 0, val1 = 0;

    if (num_bits < 32)
        return c2_read_bits(bitctx, num_bits, reinterpret_cast<int32_t *>(out));

    if (!c2_read_bits(bitctx, 16, &val)) {
        return  false;
    }
    if (!c2_read_bits(bitctx, (num_bits - 16), &val1)) {
        return  false;
    }

    *out = (uint32_t)((val << 16) | val1);

    return true;
}
/*!
***********************************************************************
* \brief
*   skip bits (0 - 31)
***********************************************************************
*/
bool c2_skip_bits(BitReadContext *bitctx, int32_t num_bits) {
    int32_t bits_left = num_bits;

    while (bitctx->num_remaining_bits_in_curr_byte_ < bits_left) {
        // Take all that's left in current byte, shift to make space for the rest.
        bits_left -= bitctx->num_remaining_bits_in_curr_byte_;
        if (!c2_update_curbyte(bitctx)) {
            return  false;
        }
    }
    bitctx->num_remaining_bits_in_curr_byte_ -= bits_left;
    bitctx->used_bits += num_bits;

    return true;
}
/*!
***********************************************************************
* \brief
*   skip bits long (0 - 32)
***********************************************************************
*/
bool c2_skip_longbits(BitReadContext *bitctx, int32_t num_bits) {
    if (!c2_skip_bits(bitctx, 16)) {
        return  false;
    }
    if (!c2_skip_bits(bitctx, (num_bits - 16))) {
        return  false;
    }
    return true;
}
/*!
***********************************************************************
* \brief
*   show bits (0 - 31)
***********************************************************************
*/
bool c2_show_bits(BitReadContext *bitctx, int32_t num_bits, int32_t *out) {
    bool ret = false;
    BitReadContext tmp_ctx = *bitctx;

    if (num_bits < 32)
        ret = c2_read_bits(&tmp_ctx, num_bits, out);
    else
        ret = c2_read_longbits(&tmp_ctx, num_bits, reinterpret_cast<uint32_t *>(out));

    return ret;
}
/*!
***********************************************************************
* \brief
*   show long bits (0 - 32)
***********************************************************************
*/
bool c2_show_longbits(BitReadContext *bitctx, int32_t num_bits, uint32_t *out) {
    bool ret = false;
    BitReadContext tmp_ctx = *bitctx;

    ret = c2_read_longbits(&tmp_ctx, num_bits, out);

    return ret;
}
/*!
***********************************************************************
* \brief
*   read unsigned data
***********************************************************************
*/
bool c2_read_ue(BitReadContext *bitctx, uint32_t *val) {
    int32_t num_bits = -1;
    int32_t bit;
    int32_t rest;
    // Count the number of contiguous zero bits.
    do {
        if (!c2_read_bits(bitctx, 1, &bit)) {
            return  false;
        }
        num_bits++;
    } while (bit == 0);
    if (num_bits > 31) {
        return  false;
    }
    // Calculate exp-Golomb code value of size num_bits.
    *val = (1 << num_bits) - 1;
    if (num_bits > 0) {
        if (!c2_read_bits(bitctx, num_bits, &rest)) {
            return  false;
        }
        *val += rest;
    }

    return true;
}

/*!
***********************************************************************
* \brief
*   read signed data
***********************************************************************
*/
bool c2_read_se(BitReadContext *bitctx, int32_t *val) {
    uint32_t ue;

    if (!c2_read_ue(bitctx, &ue)) {
        return  false;
    }
    if (ue % 2 == 0) {  // odd
        *val = -(int32_t)(ue >> 1);
    } else {
        *val = (int32_t)((ue >> 1) + 1);
    }
    return true;
}

/*!
***********************************************************************
* \brief
*   check whether has more rbsp data
***********************************************************************
*/
uint32_t c2_has_more_rbsp_data(BitReadContext *bitctx) {
    // remove tail byte which equal zero
    while (bitctx->bytes_left_ &&
           bitctx->data_[bitctx->bytes_left_ - 1] == 0)
        bitctx->bytes_left_--;

    // Make sure we have more bits, if we are at 0 bits in current byte
    // and updating current byte fails, we don't have more data anyway.
    if (bitctx->num_remaining_bits_in_curr_byte_ == 0 && !c2_update_curbyte(bitctx))
        return 0;
    // On last byte?
    if (bitctx->bytes_left_)
        return 1;
    // Last byte, look for stop bit;
    // We have more RBSP data if the last non-zero bit we find is not the
    // first available bit.
    return (bitctx->curr_byte_ &
            ((1 << (bitctx->num_remaining_bits_in_curr_byte_ - 1)) - 1)) != 0;
}
/*!
***********************************************************************
* \brief
*   initialize bit read context
***********************************************************************
*/
void c2_set_bitread_ctx(BitReadContext *bitctx, uint8_t *data, int32_t size) {
    memset(bitctx, 0, sizeof(BitReadContext));
    bitctx->data_ = data;
    bitctx->bytes_left_ = size;
    bitctx->num_remaining_bits_in_curr_byte_ = 0;
    // Initially set to 0xffff to accept all initial two-byte sequences.
    bitctx->prev_two_bytes_ = 0xffff;
    bitctx->emulation_prevention_bytes_ = 0;
    // add
    bitctx->buf = data;
    bitctx->buf_len = size;
    bitctx->used_bits = 0;
    bitctx->need_prevention_detection = 0;
}
/*!
***********************************************************************
* \brief
*   set whether detect 0x03 for h264 and h265
***********************************************************************
*/
void c2_set_pre_detection(BitReadContext *bitctx) {
    bitctx->need_prevention_detection = 1;
}
/*!
***********************************************************************
* \brief
*   align data and get current point
***********************************************************************
*/
uint8_t *c2_align_get_bits(BitReadContext *bitctx) {
    int32_t n = bitctx->num_remaining_bits_in_curr_byte_;
    if (n)
        c2_skip_bits(bitctx, n);
    return bitctx->data_;
}
