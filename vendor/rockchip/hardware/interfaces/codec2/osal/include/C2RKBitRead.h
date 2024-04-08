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

#ifndef C2_RK_BITREAD_H_
#define C2_RK_BITREAD_H_

#include <stdio.h>
#include <assert.h>

#define   __BR_ERR   __bitread_error

#define READ_ONEBIT(bitctx, out)\
    do {\
        int32_t _out; \
        bitctx->ret = c2_read_bits(bitctx, 1, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; } \
    } while (0)

#define READ_BITS(bitctx, num_bits, out)\
    do {\
        int32_t _out; \
        bitctx->ret = c2_read_bits(bitctx, num_bits, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; } \
    } while (0)

#define READ_BITS_LONG(bitctx, num_bits, out)\
    do {\
        uint32_t _out; \
        bitctx->ret = c2_read_longbits(bitctx, num_bits, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; }\
    } while (0)

#define SHOW_BITS(bitctx, num_bits, out)\
    do {\
        int32_t _out; \
        bitctx->ret = c2_show_bits(bitctx, num_bits, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; }\
    } while (0)

#define SHOW_BITS_LONG(bitctx, num_bits, out)\
    do {\
        uint32_t _out; \
        bitctx->ret = c2_show_longbits(bitctx, num_bits, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; } \
    } while (0)

#define SKIP_BITS(bitctx, num_bits)\
    do {\
        bitctx->ret = c2_skip_bits(bitctx, num_bits); \
        if (!bitctx->ret) { goto __BR_ERR; }\
    } while (0)

#define SKIP_BITS_LONG(bitctx, num_bits)\
    do {\
        bitctx->ret = c2_skip_longbits(bitctx, num_bits); \
        if (!bitctx->ret) { goto __BR_ERR; }\
    } while (0)

#define READ_UE(bitctx, out)\
    do {\
        uint32_t _out; \
        bitctx->ret = c2_read_ue(bitctx, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; } \
    } while (0)

#define READ_SE(bitctx, out)\
    do {\
        int32_t _out; \
        bitctx->ret = c2_read_se(bitctx, &_out); \
        if (bitctx->ret) { \
            *out = _out; \
        } else { goto __BR_ERR; } \
    } while (0)

typedef struct bitread_ctx_t {
    // Pointer to the next unread (not in curr_byte_) byte in the stream.
    uint8_t  *data_;
    // Bytes left in the stream (without the curr_byte_).
    uint32_t  bytes_left_;
    // Contents of the current byte; first unread bit starting at position
    // 8 - num_remaining_bits_in_curr_byte_ from MSB.
    int64_t   curr_byte_;
    // Number of bits remaining in curr_byte_
    int32_t   num_remaining_bits_in_curr_byte_;
    // Used in emulation prevention three byte detection (see spec).
    // Initially set to 0xffff to accept all initial two-byte sequences.
    int64_t   prev_two_bytes_;
    // Number of emulation presentation bytes (0x000003) we met.
    int64_t   emulation_prevention_bytes_;
    // count PPS SPS SEI read bits
    int32_t   used_bits;
    uint8_t  *buf;
    int32_t   buf_len;
    // ctx
    bool      ret;
    int32_t   need_prevention_detection;
} BitReadContext;


#ifdef  __cplusplus
extern "C" {
#endif

bool c2_update_curbyte(BitReadContext *bitctx);

// set bit read context
void c2_set_bitread_ctx(BitReadContext *bitctx, uint8_t *data, int32_t size);

// Read bits (1-31)
bool c2_read_bits(BitReadContext *bitctx, int32_t num_bits, int32_t *out);

// Read bits (1-32)
bool c2_read_longbits(BitReadContext *bitctx, int32_t num_bits, uint32_t *out);

// Show bits (1-31)
bool c2_show_bits(BitReadContext *bitctx, int32_t num_bits, int32_t *out);

// Show bits (1-32)
bool c2_show_longbits(BitReadContext *bitctx, int32_t num_bits, uint32_t *out);

// skip bits(1-31)
bool c2_skip_bits(BitReadContext *bitctx, int32_t num_bits);

// skip bits(1-32)
bool c2_skip_longbits(BitReadContext *bitctx, int32_t num_bits);

// read ue(1-32)
bool c2_read_ue(BitReadContext *bitctx, uint32_t* val);

// read se(1-31)
bool c2_read_se(BitReadContext *bitctx, int32_t* val);

// set whether detect 0x03 (used in h264 and h265)
void c2_set_pre_detection(BitReadContext *bitctx);

// check whether has more rbsp data(used in h264)
uint32_t c2_has_more_rbsp_data(BitReadContext * bitctx);

// align bits and get current pointer
uint8_t *c2_align_get_bits(BitReadContext *bitctx);

#ifdef  __cplusplus
}
#endif

#endif  // C2_RK_BITREAD_H_

